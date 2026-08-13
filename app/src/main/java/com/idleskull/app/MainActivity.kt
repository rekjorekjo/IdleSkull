package com.idleskull.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import com.idleskull.app.update.AppUpdateChecker
import com.idleskull.app.update.UpdateNotifier
import com.idleskull.app.update.UpdatePermissionPreferences

class MainActivity : ComponentActivity() {
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        AppUpdateChecker.checkInBackground(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        UpdateNotifier.createChannel(this)
        setContent { IdleSkullApp() }
        startUpdateCheck()
    }

    private fun startUpdateCheck() {
        if (
            Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            !UpdatePermissionPreferences.hasPrompted(this)
        ) {
            UpdatePermissionPreferences.markPrompted(this)
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            AppUpdateChecker.checkInBackground(this)
        }
    }
}
