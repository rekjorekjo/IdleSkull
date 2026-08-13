package com.idleskull.app.update

import android.content.Context
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

object AppUpdateChecker {
    private const val TAG = "IdleSkullUpdate"

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

    private fun fetchLatest(): AvailableUpdate {
        val connection = (URL(UpdateConfig.UPDATE_MANIFEST_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 6_000
            readTimeout = 6_000
            requestMethod = "GET"
            setRequestProperty("Accept", "application/json")
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
