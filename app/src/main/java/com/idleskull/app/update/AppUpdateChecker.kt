package com.idleskull.app.update

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.idleskull.app.BuildConfig
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

    private fun fetchLatest(): AvailableUpdate {
        // raw.githubusercontent.com can be cached by intermediate/CDN layers. The timestamp makes
        // every check a distinct request, while the headers also ask all caches to revalidate.
        val separator = if (UpdateConfig.UPDATE_MANIFEST_URL.contains('?')) '&' else '?'
        val requestUrl = UpdateConfig.UPDATE_MANIFEST_URL + separator + "_=" + System.currentTimeMillis()
        val connection = (URL(requestUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 8_000
            requestMethod = "GET"
            useCaches = false
            defaultUseCaches = false
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Cache-Control", "no-cache, no-store, max-age=0")
            setRequestProperty("Pragma", "no-cache")
            setRequestProperty("User-Agent", "IdleSkull/${BuildConfig.VERSION_NAME}")
            instanceFollowRedirects = true
        }
        return try {
            val code = connection.responseCode
            if (code !in 200..299) error("HTTP $code")
            val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val json = JSONObject(body)
            AvailableUpdate(
                versionCode = json.getInt("versionCode"),
                versionName = json.getString("versionName"),
                tagName = json.getString("tagName"),
                releaseNotes = json.optString("releaseNotes"),
            )
        } finally {
            connection.disconnect()
        }
    }
}
