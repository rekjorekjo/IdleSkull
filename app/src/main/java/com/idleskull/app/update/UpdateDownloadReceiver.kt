package com.idleskull.app.update

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class UpdateDownloadReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
        val downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
        if (downloadId < 0L) return

        val preferences = UpdatePreferences(context.applicationContext)
        if (preferences.pendingDownloadId != downloadId) return

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val verified = AppUpdateManager(context.applicationContext).verifyDownload(downloadId)
                val versionName = preferences.pendingVersionName
                if (verified && versionName != null) {
                    UpdateNotifier.sendUpdateReadyNotification(context.applicationContext, versionName)
                } else {
                    UpdateNotifier.sendVerificationFailedNotification(context.applicationContext)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
