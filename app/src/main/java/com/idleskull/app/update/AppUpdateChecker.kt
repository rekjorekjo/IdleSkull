package com.idleskull.app.update

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.Xml
import com.idleskull.app.BuildConfig
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.io.StringReader
import org.xmlpull.v1.XmlPullParser

internal data class AvailableUpdate(
    val versionCode: Int,
    val versionName: String,
    val tagName: String,
    val releaseNotes: String,
    val apkName: String,
    val apkUrl: String,
    val apkSize: Long,
    val apkSha256: String,
) {
    val releaseUrl: String
        get() = UpdateConfig.RELEASE_BASE_URL + tagName
}

internal sealed interface UpdateCheckResult {
    data object UpToDate : UpdateCheckResult
    data class Available(
        val versionName: String,
        val releaseUrl: String,
        val releaseNotes: String,
        val apkName: String,
        val apkUrl: String,
        val apkSize: Long,
        val apkSha256: String,
    ) : UpdateCheckResult
    data class Failed(val message: String) : UpdateCheckResult
}

object AppUpdateChecker {
    private const val TAG = "IdleSkullUpdate"

    /** Silent startup check. It has no UI side effect unless a newer version exists. */
    fun checkInBackground(context: Context) {
        val appContext = context.applicationContext
        Thread {
            runCatching { fetchLatest() }
                .onSuccess { latest ->
                    if (latest.versionCode > BuildConfig.VERSION_CODE) {
                        UpdateNotifier.notifyIfNeeded(appContext, latest)
                    }
                }
                .onFailure { error ->
                    Log.d(TAG, "Background update check failed", error)
                }
        }.apply {
            name = "IdleSkull-update-check"
            isDaemon = true
        }.start()
    }

    /**
     * Explicit user action. Every invocation performs a new request and returns its own result;
     * it never reuses the startup check state.
     */
    internal fun checkNow(
        context: Context,
        onResult: (UpdateCheckResult) -> Unit,
    ) {
        val threadPrefix = context.applicationContext.packageName
        Thread {
            val result = runCatching { fetchLatest() }
                .fold(
                    onSuccess = { latest ->
                        if (latest.versionCode > BuildConfig.VERSION_CODE) {
                            UpdateCheckResult.Available(
                                versionName = latest.versionName,
                                releaseUrl = latest.releaseUrl,
                                releaseNotes = latest.releaseNotes,
                                apkName = latest.apkName,
                                apkUrl = latest.apkUrl,
                                apkSize = latest.apkSize,
                                apkSha256 = latest.apkSha256,
                            )
                        } else {
                            UpdateCheckResult.UpToDate
                        }
                    },
                    onFailure = { error ->
                        Log.d(TAG, "Manual update check failed", error)
                        UpdateCheckResult.Failed(error.message ?: "网络请求失败")
                    },
                )
            Handler(Looper.getMainLooper()).post { onResult(result) }
        }.apply {
            name = "$threadPrefix-update-check-manual"
            isDaemon = true
        }.start()
    }

    /**
     * GitHub Release assets are the single source of truth for update checks.
     *
     * Release discovery intentionally avoids the unauthenticated GitHub REST API. The app reads
     * GitHub's public releases Atom feed, takes the first published release entry (prereleases are
     * included), then fetches that release's immutable `latest.json` asset directly.
     *
     * A failed request is never persisted as update state: every startup/manual check starts a new
     * feed + manifest request sequence.
     */
    private fun fetchLatest(): AvailableUpdate = fetchNewestPublishedReleaseManifest()

    private fun fetchNewestPublishedReleaseManifest(): AvailableUpdate {
        val feed = requestText(
            url = UpdateConfig.RELEASES_FEED_URL,
            accept = "application/atom+xml, application/xml;q=0.9, text/xml;q=0.8, */*;q=0.7",
            cacheBust = true,
        )
        val tagName = newestPublishedTagFromFeed(feed)
        val manifestUrl =
            UpdateConfig.RELEASE_DOWNLOAD_BASE_URL + tagName + "/" + UpdateConfig.RELEASE_MANIFEST_ASSET_NAME
        val manifest = fetchManifest(manifestUrl, cacheBust = false)
        require(manifest.tagName == tagName) {
            "Release $tagName 的 latest.json 指向 ${manifest.tagName}"
        }
        return manifest
    }

    private fun newestPublishedTagFromFeed(feed: String): String {
        val parser = Xml.newPullParser().apply {
            setInput(StringReader(feed))
        }
        var event = parser.eventType
        var insideEntry = false
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "entry" -> insideEntry = true
                    "link" -> if (insideEntry) {
                        val href = parser.getAttributeValue(null, "href").orEmpty()
                        val rel = parser.getAttributeValue(null, "rel").orEmpty()
                        if ((rel.isBlank() || rel == "alternate") && href.contains("/releases/tag/")) {
                            val tag = href.substringAfterLast('/').substringBefore('?').trim()
                            if (tag.isNotBlank()) return tag
                        }
                    }
                }
                XmlPullParser.END_TAG -> if (parser.name == "entry") insideEntry = false
            }
            event = parser.next()
        }
        error("GitHub Releases feed 中没有可用的已发布 Release")
    }

    private fun fetchManifest(url: String, cacheBust: Boolean): AvailableUpdate {
        val body = requestText(
            url = url,
            accept = "application/json, application/octet-stream;q=0.9, */*;q=0.8",
            cacheBust = cacheBust,
        )
        val json = JSONObject(body)
        val versionCode = json.getInt("versionCode")
        require(versionCode > 0) { "versionCode 无效" }
        val apk = json.getJSONObject("apk")
        val apkName = apk.getString("name")
        val apkUrl = apk.getString("url")
        require(apkName.endsWith(".apk", ignoreCase = true)) { "APK 文件名无效" }
        require(apkUrl.startsWith("https://")) { "APK 下载地址无效" }
        return AvailableUpdate(
            versionCode = versionCode,
            versionName = json.getString("versionName"),
            tagName = json.getString("tagName"),
            releaseNotes = json.optString("releaseNotes"),
            apkName = apkName,
            apkUrl = apkUrl,
            apkSize = apk.optLong("size", 0L),
            apkSha256 = apk.optString("sha256"),
        )
    }

    private fun requestText(
        url: String,
        accept: String,
        cacheBust: Boolean,
    ): String {
        val requestUrl = if (cacheBust) {
            val separator = if (url.contains('?')) '&' else '?'
            url + separator + "_=" + System.currentTimeMillis()
        } else {
            url
        }
        val connection = (URL(requestUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 8_000
            requestMethod = "GET"
            useCaches = false
            defaultUseCaches = false
            setRequestProperty("Accept", accept)
            setRequestProperty("Cache-Control", "no-cache, no-store, max-age=0")
            setRequestProperty("Pragma", "no-cache")
            setRequestProperty("User-Agent", "IdleSkull/${BuildConfig.VERSION_NAME}")
            instanceFollowRedirects = true
        }
        return try {
            val code = connection.responseCode
            if (code !in 200..299) error("HTTP $code")
            connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}
