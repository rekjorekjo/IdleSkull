package com.idleskull.app.update

import android.app.Activity
import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.util.Log
import com.idleskull.app.BuildConfig
import com.idleskull.app.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

class AppUpdateManager(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = UpdatePreferences(appContext)
    private val manifestClient = ReleaseManifestClient()
    private val githubClient = GitHubReleaseClient()
    private val downloadManager = appContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    companion object {
        private const val TAG = "IdleSkullUpdate"
    }

    init {
        clearInstalledUpdateIfNeeded()
    }

    private fun normalizedVersion(value: String): String = value.removeSuffix("-beta")

    private fun parseSemanticVersion(value: String): Triple<Int, Int, Int>? {
        val normalized = normalizedVersion(value)
        if (!normalized.matches(Regex("^\\d+\\.\\d+\\.\\d+$"))) return null
        val parts = normalized.split(".")
        return Triple(
            parts[0].toIntOrNull() ?: return null,
            parts[1].toIntOrNull() ?: return null,
            parts[2].toIntOrNull() ?: return null,
        )
    }

    private fun compareSemanticVersions(a: Triple<Int, Int, Int>, b: Triple<Int, Int, Int>): Int {
        val major = a.first.compareTo(b.first)
        if (major != 0) return major
        val minor = a.second.compareTo(b.second)
        if (minor != 0) return minor
        return a.third.compareTo(b.third)
    }

    private fun clearInstalledUpdateIfNeeded() {
        val pendingVersionName = preferences.pendingVersionName ?: return
        val current = parseSemanticVersion(BuildConfig.VERSION_NAME) ?: return
        val pending = parseSemanticVersion(pendingVersionName) ?: return
        if (compareSemanticVersions(current, pending) < 0) return
        clearObsoleteUpdate()
    }

    private fun clearObsoleteUpdate() {
        val downloadId = preferences.pendingDownloadId
        val assetName = preferences.pendingAssetName
        if (downloadId != null) runCatching { downloadManager.remove(downloadId) }
        val directory = appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        if (directory != null && assetName != null) {
            val file = File(directory, assetName)
            if (file.exists() && !file.delete()) Log.w(TAG, "Failed to delete obsolete update file")
        }
        preferences.clearPendingUpdate()
        preferences.pendingInstallPermission = false
        UpdateNotifier.cancelUpdateNotifications(appContext)
    }

    private fun markDownloadFailed(downloadId: Long?) {
        if (downloadId != null) runCatching { downloadManager.remove(downloadId) }
        preferences.clearPendingUpdate()
        preferences.downloadFailed = true
    }

    suspend fun checkForUpdate(): UpdateCheckResult {
        return try {
            val release = try {
                manifestClient.fetchLatestRelease()
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                Log.w(TAG, "Manifest failed, falling back to GitHub API", exception)
                githubClient.fetchLatestRelease()
            }

            val current = parseSemanticVersion(BuildConfig.VERSION_NAME)
            val remote = parseSemanticVersion(release.versionName)
            if (current == null || remote == null) return UpdateCheckResult.CheckFailed
            val semanticComparison = compareSemanticVersions(remote, current)

            val hasUpdate = when (release.source) {
                UpdateSource.MANIFEST -> {
                    val remoteCode = release.versionCode ?: return UpdateCheckResult.CheckFailed
                    remoteCode > BuildConfig.VERSION_CODE.toLong() && semanticComparison >= 0
                }
                UpdateSource.GITHUB_API -> semanticComparison > 0
            }

            if (hasUpdate) UpdateCheckResult.UpdateAvailable(release) else UpdateCheckResult.UpToDate
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            Log.w(TAG, "Update check failed", exception)
            UpdateCheckResult.CheckFailed
        }
    }

    /** A failed DownloadManager request must not poison later manual checks. */
    fun resetFailedDownloadForManualCheck() {
        if (currentStatus().state != UpdateDownloadState.FAILED) return

        val failedId = preferences.pendingDownloadId
        val failedAsset = preferences.pendingAssetName
        if (failedId != null) runCatching { downloadManager.remove(failedId) }
        val directory = appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        if (directory != null && failedAsset != null) {
            val file = File(directory, failedAsset)
            if (file.exists() && !file.delete()) Log.w(TAG, "Failed to delete stale failed update file")
        }
        preferences.clearPendingUpdate()
        Log.i(TAG, "Cleared stale failed update before manual check")
    }

    fun startDownload(release: GitHubRelease): Boolean {
        val existingId = preferences.pendingDownloadId
        val existingAsset = preferences.pendingAssetName
        if (existingId != null && existingAsset == release.apkAssetName) {
            when (currentStatus().state) {
                UpdateDownloadState.WAITING,
                UpdateDownloadState.DOWNLOADING,
                UpdateDownloadState.VERIFYING,
                UpdateDownloadState.READY -> return true
                else -> Unit
            }
        }

        if (existingId != null) runCatching { downloadManager.remove(existingId) }
        val directory = appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        if (directory == null) {
            preferences.downloadFailed = true
            return false
        }
        if (existingAsset != null) File(directory, existingAsset).takeIf { it.exists() }?.delete()

        val target = File(directory, release.apkAssetName)
        if (target.exists() && !target.delete()) {
            preferences.downloadFailed = true
            return false
        }

        preferences.clearPendingUpdate()
        preferences.downloadFailed = false
        preferences.verified = false
        preferences.pendingAssetName = release.apkAssetName

        val request = DownloadManager.Request(Uri.parse(release.apkDownloadUrl))
            .setTitle("IdleSkull ${release.versionName}")
            .setDescription(appContext.getString(R.string.app_name))
            .setDestinationInExternalFilesDir(appContext, Environment.DIRECTORY_DOWNLOADS, release.apkAssetName)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(false)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setMimeType("application/vnd.android.package-archive")

        val downloadId = try {
            downloadManager.enqueue(request)
        } catch (exception: RuntimeException) {
            Log.w(TAG, "Failed to enqueue update", exception)
            preferences.downloadFailed = true
            return false
        }

        preferences.pendingDownloadId = downloadId
        preferences.pendingVersionName = release.versionName
        preferences.pendingTagName = release.tagName
        preferences.pendingSha256 = release.sha256
        preferences.pendingApkSize = release.apkSize
        return true
    }

    fun currentStatus(): UpdateDownloadStatus {
        clearInstalledUpdateIfNeeded()
        val downloadId = preferences.pendingDownloadId
        val failed = preferences.downloadFailed
        if (downloadId == null) {
            return UpdateDownloadStatus(if (failed) UpdateDownloadState.FAILED else UpdateDownloadState.NONE)
        }

        val cursor: Cursor? = downloadManager.query(DownloadManager.Query().setFilterById(downloadId))
        cursor?.use {
            if (it.moveToFirst()) {
                val statusIndex = it.getColumnIndex(DownloadManager.COLUMN_STATUS)
                val soFarIndex = it.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                val totalIndex = it.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                if (statusIndex >= 0) {
                    val status = it.getInt(statusIndex)
                    val soFar = if (soFarIndex >= 0) it.getLong(soFarIndex) else 0L
                    val total = if (totalIndex >= 0) it.getLong(totalIndex) else 0L
                    val progress = if (total > 0L) ((soFar * 100L) / total).toInt().coerceIn(0, 100) else null
                    return when (status) {
                        DownloadManager.STATUS_PENDING, DownloadManager.STATUS_PAUSED -> UpdateDownloadStatus(
                            UpdateDownloadState.WAITING, preferences.pendingVersionName,
                        )
                        DownloadManager.STATUS_RUNNING -> UpdateDownloadStatus(
                            UpdateDownloadState.DOWNLOADING, preferences.pendingVersionName, progress,
                        )
                        DownloadManager.STATUS_SUCCESSFUL -> UpdateDownloadStatus(
                            if (preferences.verified) UpdateDownloadState.READY else UpdateDownloadState.VERIFYING,
                            preferences.pendingVersionName,
                        )
                        DownloadManager.STATUS_FAILED -> {
                            preferences.downloadFailed = true
                            UpdateDownloadStatus(UpdateDownloadState.FAILED, preferences.pendingVersionName)
                        }
                        else -> UpdateDownloadStatus(UpdateDownloadState.NONE)
                    }
                }
            }
        }
        preferences.clearPendingUpdate()
        return UpdateDownloadStatus(if (failed) UpdateDownloadState.FAILED else UpdateDownloadState.NONE)
    }

    suspend fun verifyDownload(downloadId: Long): Boolean = withContext(Dispatchers.IO) {
        val startedAt = SystemClock.elapsedRealtime()
        if (preferences.pendingDownloadId != downloadId) return@withContext false

        val cursor = downloadManager.query(DownloadManager.Query().setFilterById(downloadId))
        var status = -1
        var totalSize = 0L
        cursor?.use {
            if (it.moveToFirst()) {
                val statusIndex = it.getColumnIndex(DownloadManager.COLUMN_STATUS)
                val totalIndex = it.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                if (statusIndex >= 0) status = it.getInt(statusIndex)
                if (totalIndex >= 0) totalSize = it.getLong(totalIndex)
            }
        }
        if (status != DownloadManager.STATUS_SUCCESSFUL || totalSize != preferences.pendingApkSize) {
            markDownloadFailed(downloadId)
            return@withContext false
        }

        val expectedSha = preferences.pendingSha256 ?: run {
            markDownloadFailed(downloadId)
            return@withContext false
        }
        val descriptor = try {
            downloadManager.openDownloadedFile(downloadId)
        } catch (exception: Exception) {
            Log.w(TAG, "Cannot open downloaded APK", exception)
            markDownloadFailed(downloadId)
            return@withContext false
        }

        try {
            ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { input ->
                val digest = MessageDigest.getInstance("SHA-256")
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer)
                    if (count <= 0) break
                    digest.update(buffer, 0, count)
                }
                val actual = digest.digest().joinToString("") { "%02x".format(it) }
                if (!actual.equals(expectedSha, ignoreCase = true)) {
                    markDownloadFailed(downloadId)
                    return@withContext false
                }
            }
            preferences.verified = true
            preferences.downloadFailed = false
            Log.i(TAG, "APK verification completed in ${SystemClock.elapsedRealtime() - startedAt} ms")
            true
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            Log.w(TAG, "APK verification failed", exception)
            markDownloadFailed(downloadId)
            false
        }
    }

    suspend fun verifyPendingDownloadIfNeeded(): Boolean? {
        val status = currentStatus()
        if (status.state == UpdateDownloadState.READY) return true
        if (status.state != UpdateDownloadState.VERIFYING) return null
        return verifyDownload(preferences.pendingDownloadId ?: return false)
    }

    fun requestInstall(activity: Activity): InstallLaunchResult {
        if (currentStatus().state != UpdateDownloadState.READY) return InstallLaunchResult.NO_VERIFIED_UPDATE

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !appContext.packageManager.canRequestPackageInstalls()) {
            val settings = Intent(
                android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${appContext.packageName}"),
            )
            return try {
                activity.startActivity(settings)
                preferences.pendingInstallPermission = true
                InstallLaunchResult.PERMISSION_REQUIRED
            } catch (exception: ActivityNotFoundException) {
                InstallLaunchResult.INSTALLER_UNAVAILABLE
            } catch (exception: SecurityException) {
                InstallLaunchResult.FAILED
            }
        }

        preferences.pendingInstallPermission = false
        val id = preferences.pendingDownloadId ?: return InstallLaunchResult.FAILED
        val apkUri = downloadManager.getUriForDownloadedFile(id) ?: return InstallLaunchResult.FAILED
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        if (appContext.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) == null) {
            return InstallLaunchResult.INSTALLER_UNAVAILABLE
        }
        return try {
            activity.startActivity(intent)
            InstallLaunchResult.LAUNCHED
        } catch (exception: ActivityNotFoundException) {
            InstallLaunchResult.INSTALLER_UNAVAILABLE
        } catch (exception: RuntimeException) {
            Log.w(TAG, "Install launch failed", exception)
            InstallLaunchResult.FAILED
        }
    }
}
