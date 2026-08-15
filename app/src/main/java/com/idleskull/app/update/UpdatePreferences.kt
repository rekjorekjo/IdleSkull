package com.idleskull.app.update

import android.content.Context
import android.content.SharedPreferences

class UpdatePreferences(context: Context) {
    private val preferences: SharedPreferences =
        context.getSharedPreferences("idle_skull_updates", Context.MODE_PRIVATE)

    var pendingDownloadId: Long?
        get() {
            val id = preferences.getLong("pending_download_id", -1L)
            return if (id >= 0L) id else null
        }
        set(value) = preferences.edit().putLong("pending_download_id", value ?: -1L).apply()

    var pendingVersionName: String?
        get() = preferences.getString("pending_version_name", null)
        set(value) = preferences.edit().putString("pending_version_name", value).apply()

    var pendingTagName: String?
        get() = preferences.getString("pending_tag_name", null)
        set(value) = preferences.edit().putString("pending_tag_name", value).apply()

    var pendingAssetName: String?
        get() = preferences.getString("pending_asset_name", null)
        set(value) = preferences.edit().putString("pending_asset_name", value).apply()

    var pendingSha256: String?
        get() = preferences.getString("pending_sha256", null)
        set(value) = preferences.edit().putString("pending_sha256", value).apply()

    var pendingApkSize: Long
        get() = preferences.getLong("pending_apk_size", 0L)
        set(value) = preferences.edit().putLong("pending_apk_size", value).apply()

    var verified: Boolean
        get() = preferences.getBoolean("verified", false)
        set(value) = preferences.edit().putBoolean("verified", value).apply()

    var downloadFailed: Boolean
        get() = preferences.getBoolean("download_failed", false)
        set(value) = preferences.edit().putBoolean("download_failed", value).apply()

    var pendingInstallPermission: Boolean
        get() = preferences.getBoolean("pending_install_permission", false)
        set(value) = preferences.edit().putBoolean("pending_install_permission", value).apply()

    var lastNotifiedAvailableVersion: String?
        get() = preferences.getString("last_notified_available_version", null)
        set(value) = preferences.edit().putString("last_notified_available_version", value).apply()

    fun clearPendingUpdate() {
        preferences.edit().apply {
            putLong("pending_download_id", -1L)
            remove("pending_version_name")
            remove("pending_tag_name")
            remove("pending_asset_name")
            remove("pending_sha256")
            putLong("pending_apk_size", 0L)
            putBoolean("verified", false)
            putBoolean("download_failed", false)
            apply()
        }
    }
}
