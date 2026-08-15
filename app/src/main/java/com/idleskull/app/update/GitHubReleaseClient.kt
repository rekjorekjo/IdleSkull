package com.idleskull.app.update

import android.util.Log
import com.idleskull.app.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

class GitHubReleaseClient {
    companion object {
        private const val TAG = "IdleSkullUpdate"
        private const val CONNECT_TIMEOUT = 15_000
        private const val READ_TIMEOUT = 15_000
        private const val MAX_RESPONSE_SIZE = 1_048_576
        private val TAG_PATTERN = Regex("^v\\d+\\.\\d+\\.\\d+(?:-beta)?$")
    }

    suspend fun fetchLatestRelease(): GitHubRelease = withContext(Dispatchers.IO) {
        val connection = URL(UpdateConfig.LATEST_RELEASE_API_URL).openConnection() as HttpURLConnection
        connection.connectTimeout = CONNECT_TIMEOUT
        connection.readTimeout = READ_TIMEOUT
        connection.useCaches = false
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        connection.setRequestProperty("X-GitHub-Api-Version", UpdateConfig.GITHUB_API_VERSION)
        connection.setRequestProperty("User-Agent", "IdleSkull/${BuildConfig.VERSION_NAME}")

        try {
            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) throw Exception("HTTP $responseCode")
            val json = JSONObject(String(readLimited(connection, MAX_RESPONSE_SIZE), Charsets.UTF_8))

            if (json.optBoolean("draft", false)) throw Exception("Release is draft")
            if (json.optBoolean("prerelease", false)) throw Exception("Release is prerelease")

            val tagName = json.getString("tag_name")
            if (!TAG_PATTERN.matches(tagName)) throw Exception("Invalid tagName: $tagName")
            val versionName = tagName.substring(1)
            val expectedApkName = UpdateConfig.expectedApkName(tagName)

            val assets = json.getJSONArray("assets")
            var apkAsset: JSONObject? = null
            var apkCount = 0
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                if (asset.getString("name") == expectedApkName) {
                    apkCount++
                    apkAsset = asset
                }
            }
            if (apkCount == 0) throw Exception("APK asset not found")
            if (apkCount > 1) throw Exception("Multiple APK assets found")

            val asset = apkAsset!!
            val apkUrl = asset.getString("browser_download_url")
            val apkSize = asset.getLong("size")
            if (apkSize <= 0L || apkSize > 200L * 1024L * 1024L) throw Exception("Invalid APK size")
            validateApkUrl(apkUrl, tagName, expectedApkName)

            val digest = asset.optString("digest", "")
            if (!digest.startsWith("sha256:")) throw Exception("Missing or invalid digest")
            val sha256 = digest.substring(7).lowercase()
            if (!sha256.matches(Regex("^[a-f0-9]{64}$"))) throw Exception("Invalid SHA-256 digest")

            GitHubRelease(
                source = UpdateSource.GITHUB_API,
                tagName = tagName,
                versionName = versionName,
                versionCode = null,
                releaseNotes = json.optString("body", "").take(20_000),
                apkAssetName = expectedApkName,
                apkDownloadUrl = apkUrl,
                apkSize = apkSize,
                sha256 = sha256,
            )
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            Log.w(TAG, "GitHub API fetch failed", exception)
            throw exception
        } finally {
            connection.disconnect()
        }
    }

    private fun readLimited(connection: HttpURLConnection, maxBytes: Int): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var total = 0
        connection.inputStream.use { input ->
            while (true) {
                val count = input.read(buffer)
                if (count <= 0) break
                total += count
                if (total > maxBytes) throw Exception("Response too large")
                output.write(buffer, 0, count)
            }
        }
        return output.toByteArray()
    }

    private fun validateApkUrl(value: String, tagName: String, apkName: String) {
        val url = URL(value)
        if (url.protocol != "https") throw Exception("APK URL must use HTTPS")
        if (!url.host.equals("github.com", ignoreCase = true)) throw Exception("APK URL host must be github.com")
        if (url.query != null || url.ref != null || url.userInfo != null || url.port != -1) {
            throw Exception("Invalid APK URL")
        }
        val expectedPath = "/rekjorekjo/IdleSkull/releases/download/$tagName/$apkName"
        if (url.path != expectedPath) throw Exception("APK URL path mismatch")
    }
}
