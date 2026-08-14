package com.idleskull.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.idleskull.app.ui.components.PixelButton
import com.idleskull.app.ui.screens.HomeScreen
import com.idleskull.app.ui.screens.SettingsScreen
import com.idleskull.app.ui.screens.StatsScreen
import com.idleskull.app.ui.theme.IdleSkullTheme

private enum class MainTab { TIMER, STATS, SETTINGS }

@Composable
fun IdleSkullApp(viewModel: TimerViewModel = viewModel()) {
    var tab by remember { mutableStateOf(MainTab.TIMER) }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.reload()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    IdleSkullTheme(darkMode = viewModel.darkMode) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding(),
            ) {
                Box(Modifier.weight(1f)) {
                    when (tab) {
                        MainTab.TIMER -> HomeScreen(viewModel)
                        MainTab.STATS -> StatsScreen(viewModel)
                        MainTab.SETTINGS -> SettingsScreen(viewModel)
                    }
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.background)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                ) {
                    PixelButton(stringResource(R.string.copy_nav_timer), { tab = MainTab.TIMER }, Modifier.weight(1f), inverted = tab != MainTab.TIMER)
                    PixelButton(stringResource(R.string.copy_nav_log), { tab = MainTab.STATS }, Modifier.weight(1f), inverted = tab != MainTab.STATS)
                    PixelButton(stringResource(R.string.copy_nav_settings), { tab = MainTab.SETTINGS }, Modifier.weight(1f), inverted = tab != MainTab.SETTINGS)
                }
            }
        }
    }
}
