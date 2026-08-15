package com.idleskull.app.update

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.idleskull.app.MainActivity
import com.idleskull.app.R

object UpdateNotifier {
    const val CHANNEL_ID = "app_updates_v2"
    private const val CHANNEL_NAME = "应用更新"
    private const val ID_AVAILABLE = 1000
    private const val ID_READY = 1001
    private const val ID_FAILED = 1002

    fun createChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH).apply {
                description = "IdleSkull 新版本提醒"
                enableVibration(true)
            },
        )
    }

    fun notifyIfNeeded(context: Context, release: GitHubRelease): Boolean {
        if (!canPostNotifications(context)) return false
        val pendingIntent = launchAppPendingIntent(context, ID_AVAILABLE)
        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_skull)
            .setContentTitle("IdleSkull 有新版本")
            .setContentText("${release.versionName} 已发布")
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_STATUS)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(ID_AVAILABLE, notification)
        return true
    }

    fun sendUpdateReadyNotification(context: Context, versionName: String) {
        if (!canPostNotifications(context)) return
        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_skull)
            .setContentTitle("更新已准备好")
            .setContentText("IdleSkull $versionName 已下载并校验完成")
            .setContentIntent(launchAppPendingIntent(context, ID_READY))
            .setAutoCancel(true)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(ID_READY, notification)
    }

    fun sendVerificationFailedNotification(context: Context) {
        if (!canPostNotifications(context)) return
        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_skull)
            .setContentTitle("更新下载失败")
            .setContentText("安装包下载或校验失败，请回到应用重试")
            .setContentIntent(launchAppPendingIntent(context, ID_FAILED))
            .setAutoCancel(true)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(ID_FAILED, notification)
    }

    fun cancelUpdateAvailableNotification(context: Context) {
        context.getSystemService(NotificationManager::class.java).cancel(ID_AVAILABLE)
    }

    fun cancelUpdateNotifications(context: Context) {
        context.getSystemService(NotificationManager::class.java).apply {
            cancel(ID_AVAILABLE)
            cancel(ID_READY)
            cancel(ID_FAILED)
        }
    }

    private fun launchAppPendingIntent(context: Context, requestCode: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun canPostNotifications(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
}
