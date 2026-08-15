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

class ReleaseManifestClient {
    companion object {
        private const val TAG = "IdleSkullUpdate"
        private const val CONNECT_TIMEOUT = 15_000
        private const val READ_TIMEOUT = 15_000
        private const val MAX_MANIFEST_SIZE = 65_536
        private const val MAX_RELEASE_NOTES_SIZE = 20_000
        private val TAG_PATTERN = Regex("^v\\d+\\.\\d+\\.\\d+(?:-beta)?$")
        private val VERSION_PATTERN = Regex("^\\d+\\.\\d+\\.\\d+(?:-beta)?$")
    }

    suspend fun fetchLatestRelease(): GitHubRelease = withContext(Dispatchers.IO) {
        val connection = URL(UpdateConfig.MANIFEST_URL).openConnection() as HttpURLConnection
        connection.connectTimeout = CONNECT_TIMEOUT
        connection.readTimeout = READ_TIMEOUT
        connection.instanceFollowRedirects = true
        connection.useCaches = false
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("Cache-Control", "no-cache")
        connection.setRequestProperty("User-Agent", "IdleSkull/${BuildConfig.VERSION_NAME}")

        try {
            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw Exception("HTTP $responseCode")
            }

            val contentLength = connection.contentLengthLong
            if (contentLength > MAX_MANIFEST_SIZE) {
                throw Exception("Manifest too large")
            }

            val bytes = readLimited(connection, MAX_MANIFEST_SIZE)
            if (bytes.isEmpty()) throw Exception("Empty manifest")

            val json = JSONObject(String(bytes, Charsets.UTF_8))
            val schemaVersion = json.getInt("schemaVersion")
            if (schemaVersion != 1) throw Exception("Unsupported schema version: $schemaVersion")

            val tagName = json.getString("tagName")
            if (!TAG_PATTERN.matches(tagName)) throw Exception("Invalid tagName: $tagName")

            val versionName = json.getString("versionName")
            if (!VERSION_PATTERN.matches(versionName)) throw Exception("Invalid versionName: $versionName")
            if (tagName != "v$versionName") throw Exception("tagName and versionName mismatch")

            val versionCode = json.getLong("versionCode")
            if (versionCode <= 0L) throw Exception("Invalid versionCode: $versionCode")

            val releaseNotes = json.optString("releaseNotes", "").take(MAX_RELEASE_NOTES_SIZE)
            val apk = json.getJSONObject("apk")
            val apkName = apk.getString("name")
            val expectedApkName = UpdateConfig.expectedApkName(tagName)
            if (apkName != expectedApkName) throw Exception("Invalid APK name: $apkName")

            val apkUrl = apk.getString("url")
            val apkSize = apk.getLong("size")
            if (apkSize <= 0L || apkSize > 200L * 1024L * 1024L) {
                throw Exception("Invalid APK size: $apkSize")
            }

            val sha256 = apk.getString("sha256").lowercase()
            if (!sha256.matches(Regex("^[a-f0-9]{64}$"))) {
                throw Exception("Invalid SHA-256: $sha256")
            }

            validateApkUrl(apkUrl, tagName, apkName)

            GitHubRelease(
                source = UpdateSource.MANIFEST,
                tagName = tagName,
                versionName = versionName,
                versionCode = versionCode,
                releaseNotes = releaseNotes,
                apkAssetName = apkName,
                apkDownloadUrl = apkUrl,
                apkSize = apkSize,
                sha256 = sha256,
            )
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            Log.w(TAG, "Manifest fetch failed", exception)
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
                if (total > maxBytes) throw Exception("Manifest too large")
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
