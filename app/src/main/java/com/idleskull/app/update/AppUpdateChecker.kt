package com.idleskull.app.update

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

object AppUpdateChecker {
    private const val TAG = "IdleSkullUpdate"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val checking = AtomicBoolean(false)

    fun checkInBackground(context: Context) {
        if (!checking.compareAndSet(false, true)) return
        val appContext = context.applicationContext
        scope.launch {
            try {
                val manager = AppUpdateManager(appContext)
                val preferences = UpdatePreferences(appContext)
                when (val result = manager.checkForUpdate()) {
                    is UpdateCheckResult.UpdateAvailable -> {
                        val version = result.release.versionName
                        if (preferences.lastNotifiedAvailableVersion != version) {
                            if (UpdateNotifier.notifyIfNeeded(appContext, result.release)) {
                                preferences.lastNotifiedAvailableVersion = version
                            }
                        }
                    }
                    UpdateCheckResult.UpToDate -> {
                        preferences.lastNotifiedAvailableVersion = null
                        UpdateNotifier.cancelUpdateAvailableNotification(appContext)
                    }
                    UpdateCheckResult.CheckFailed -> Unit
                }
            } catch (exception: Exception) {
                Log.d(TAG, "Startup update check failed", exception)
            } finally {
                checking.set(false)
            }
        }
    }
}
