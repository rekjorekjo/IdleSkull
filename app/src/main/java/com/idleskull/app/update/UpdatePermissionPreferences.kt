package com.idleskull.app.update

import android.content.Context

object UpdatePermissionPreferences {
    private const val PREFS = "idle_skull_update"
    private const val KEY_NOTIFICATION_PERMISSION_PROMPTED = "notification_permission_prompted"

    fun hasPrompted(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_NOTIFICATION_PERMISSION_PROMPTED, false)

    fun markPrompted(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_NOTIFICATION_PERMISSION_PROMPTED, true)
            .apply()
    }
}
