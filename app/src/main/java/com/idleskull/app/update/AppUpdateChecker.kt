package com.idleskull.app.update

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.idleskull.app.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

internal data class AvailableUpdate(
    val versionCode: Int,
    val versionName: String,
    val tagName: String,
    val releaseNotes: String,
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
     * We intentionally do NOT use GitHub's `/releases/latest` endpoint because it ignores
     * prereleases, while IdleSkull beta builds are published as prereleases. Instead we list
     * published releases, pick the one with the newest published_at timestamp (prereleases
     * included), and read that release's `latest.json` asset directly.
     */
    private fun fetchLatest(): AvailableUpdate = fetchNewestPublishedReleaseManifest()

    private fun fetchNewestPublishedReleaseManifest(): AvailableUpdate {
        val body = requestText(
            url = UpdateConfig.RELEASES_API_URL,
            accept = "application/vnd.github+json",
            cacheBust = true,
        )
        val releases = JSONArray(body)
        require(releases.length() > 0) { "GitHub 暂无已发布 Release" }

        var newestRelease: JSONObject? = null
        var newestPublishedAt = Long.MIN_VALUE
        for (index in 0 until releases.length()) {
            val release = releases.optJSONObject(index) ?: continue
            if (release.optBoolean("draft", false)) continue
            val publishedAt = release.optString("published_at")
            if (publishedAt.isBlank()) continue
            val publishedEpoch = runCatching { java.time.Instant.parse(publishedAt).toEpochMilli() }
                .getOrNull() ?: continue
            if (publishedEpoch > newestPublishedAt) {
                newestPublishedAt = publishedEpoch
                newestRelease = release
            }
        }

        val release = newestRelease ?: error("GitHub 暂无可用的已发布 Release")
        val tagName = release.optString("tag_name")
        require(tagName.isNotBlank()) { "最新 Release 缺少 tag_name" }
        val assets = release.optJSONArray("assets")
            ?: error("最新 Release $tagName 没有 assets")

        var manifestUrl: String? = null
        for (index in 0 until assets.length()) {
            val asset = assets.optJSONObject(index) ?: continue
            if (asset.optString("name") != UpdateConfig.RELEASE_MANIFEST_ASSET_NAME) continue
            val downloadUrl = asset.optString("browser_download_url")
            if (downloadUrl.isNotBlank()) {
                manifestUrl = downloadUrl
                break
            }
        }
        val url = manifestUrl
            ?: error("最新 Release $tagName 缺少 ${UpdateConfig.RELEASE_MANIFEST_ASSET_NAME}")

        val manifest = fetchManifest(url, cacheBust = true)
        require(manifest.tagName == tagName) {
            "Release $tagName 的 latest.json 指向 ${manifest.tagName}"
        }
        return manifest
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
        return AvailableUpdate(
            versionCode = versionCode,
            versionName = json.getString("versionName"),
            tagName = json.getString("tagName"),
            releaseNotes = json.optString("releaseNotes"),
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
