package com.idleskull.app.update

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

internal sealed interface UpdateDownloadState {
    data class Downloading(val percent: Int) : UpdateDownloadState
    data object LaunchingInstaller : UpdateDownloadState
    data class Failed(val message: String) : UpdateDownloadState
}

internal object AppUpdateDownloader {
    fun downloadAndInstall(
        context: Context,
        update: UpdateCheckResult.Available,
        onState: (UpdateDownloadState) -> Unit,
    ) {
        val appContext = context.applicationContext
        Thread {
            runCatching {
                val dir = File(appContext.cacheDir, "updates").apply { mkdirs() }
                dir.listFiles()?.forEach { old ->
                    if (old.name != update.apkName) old.delete()
                }
                val target = File(dir, sanitizeFileName(update.apkName))
                val temp = File(dir, target.name + ".part")
                temp.delete()

                val connection = (URL(update.apkUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 10_000
                    readTimeout = 120_000
                    requestMethod = "GET"
                    useCaches = false
                    defaultUseCaches = false
                    instanceFollowRedirects = true
                    setRequestProperty("Accept", "application/vnd.android.package-archive, application/octet-stream;q=0.9, */*;q=0.8")
                    setRequestProperty("Cache-Control", "no-cache")
                    setRequestProperty("User-Agent", "IdleSkull-update-downloader")
                }

                val digest = MessageDigest.getInstance("SHA-256")
                var downloaded = 0L
                var lastPercent = -1
                try {
                    val code = connection.responseCode
                    if (code !in 200..299) error("APK 下载失败：HTTP $code")
                    val expectedSize = update.apkSize.takeIf { it > 0L }
                        ?: connection.contentLengthLong.takeIf { it > 0L }
                        ?: -1L

                    connection.inputStream.use { input ->
                        FileOutputStream(temp).use { output ->
                            val buffer = ByteArray(64 * 1024)
                            while (true) {
                                val count = input.read(buffer)
                                if (count <= 0) break
                                output.write(buffer, 0, count)
                                digest.update(buffer, 0, count)
                                downloaded += count
                                val percent = if (expectedSize > 0L) {
                                    ((downloaded * 100L) / expectedSize).toInt().coerceIn(0, 100)
                                } else {
                                    0
                                }
                                if (percent != lastPercent) {
                                    lastPercent = percent
                                    postMain { onState(UpdateDownloadState.Downloading(percent)) }
                                }
                            }
                            output.flush()
                        }
                    }
                } finally {
                    connection.disconnect()
                }

                if (update.apkSize > 0L && downloaded != update.apkSize) {
                    temp.delete()
                    error("APK 大小校验失败")
                }
                if (update.apkSha256.isNotBlank()) {
                    val actual = digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
                    if (!actual.equals(update.apkSha256, ignoreCase = true)) {
                        temp.delete()
                        error("APK SHA-256 校验失败")
                    }
                }

                if (target.exists()) target.delete()
                if (!temp.renameTo(target)) {
                    temp.copyTo(target, overwrite = true)
                    temp.delete()
                }

                postMain {
                    runCatching {
                        onState(UpdateDownloadState.LaunchingInstaller)
                        launchInstaller(context, target)
                    }.onFailure { error ->
                        onState(UpdateDownloadState.Failed(error.message ?: "无法打开系统安装程序"))
                    }
                }
            }.onFailure { error ->
                postMain {
                    onState(UpdateDownloadState.Failed(error.message ?: "下载失败"))
                }
            }
        }.apply {
            name = "IdleSkull-update-download"
            isDaemon = true
        }.start()
    }

    private fun launchInstaller(context: Context, apk: File) {
        val uri = FileProvider.getUriForFile(
            context,
            context.packageName + ".fileprovider",
            apk,
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (context !is android.app.Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    private fun sanitizeFileName(name: String): String {
        val cleaned = name.replace(Regex("[^A-Za-z0-9._-]"), "_")
        return cleaned.ifBlank { "IdleSkull-update.apk" }
    }

    private fun postMain(block: () -> Unit) {
        android.os.Handler(android.os.Looper.getMainLooper()).post(block)
    }
}
