package com.idleskull.app.update

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import com.idleskull.app.R

object UpdateNotifier {
    const val CHANNEL_ID = "app_updates_v2"
    private const val CHANNEL_NAME = "应用更新"
    private const val PREFS = "idle_skull_update"
    private const val KEY_LAST_NOTIFIED_VERSION = "last_notified_version"

    fun createChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "IdleSkull 新版本提醒"
            enableVibration(true)
        }
        manager.createNotificationChannel(channel)
    }

    internal fun notifyIfNeeded(context: Context, update: AvailableUpdate) {
        if (!canPostNotifications(context)) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getInt(KEY_LAST_NOTIFIED_VERSION, 0) >= update.versionCode) return

        val openRelease = Intent(Intent.ACTION_VIEW, Uri.parse(update.apkUrl)).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            update.versionCode,
            openRelease,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_skull)
            .setContentTitle("IdleSkull 有新版本")
            .setContentText("${update.versionName} 已发布")
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_STATUS)
            .build()

        context.getSystemService(NotificationManager::class.java)
            .notify(update.versionCode, notification)
        prefs.edit().putInt(KEY_LAST_NOTIFIED_VERSION, update.versionCode).apply()
    }

    private fun canPostNotifications(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
}
