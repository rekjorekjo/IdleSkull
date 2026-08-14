package com.idleskull.app.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.idleskull.app.TimerViewModel
import com.idleskull.app.ui.components.PixelButton
import com.idleskull.app.ui.components.PixelChoice
import com.idleskull.app.ui.components.PixelCutShape
import com.idleskull.app.ui.components.PixelPanel
import com.idleskull.app.ui.components.PixelText

@Composable
fun SettingsScreen(viewModel: TimerViewModel) {
    var showClearConfirm by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }

    if (showAbout) {
        AboutScreen(
            darkMode = viewModel.darkMode,
            onBack = { showAbout = false },
        )
        return
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PixelText(
            text = stringResource(com.idleskull.app.R.string.copy_settings_title),
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
        )

        PixelPanel {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PixelText("外观", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PixelChoice("浅色", !viewModel.darkMode, { viewModel.updateDarkMode(false) }, Modifier.weight(1f))
                    PixelChoice("深色", viewModel.darkMode, { viewModel.updateDarkMode(true) }, Modifier.weight(1f))
                }
            }
        }

        PixelPanel {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PixelText("数据", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                PixelButton("清空历史记录", { showClearConfirm = true }, Modifier.fillMaxWidth())
            }
        }

        PixelPanel {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PixelText("应用", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                PixelButton("关于", { showAbout = true }, Modifier.fillMaxWidth(), inverted = true)
            }
        }

        Spacer(Modifier.height(8.dp))
    }

    if (showClearConfirm) {
        ClearHistoryDialog(
            onDismiss = { showClearConfirm = false },
            onConfirm = {
                viewModel.clearSessions()
                showClearConfirm = false
            },
        )
    }
}

@Composable
private fun ClearHistoryDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = PixelCutShape,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            modifier = Modifier
                .fillMaxWidth()
                .border(2.dp, MaterialTheme.colorScheme.outline, PixelCutShape),
        ) {
            Column(
                Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                PixelText("清空历史记录？", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                PixelText(
                    "此操作无法撤销。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PixelButton("取消", onDismiss, Modifier.weight(1f), inverted = true)
                    PixelButton("清空", onConfirm, Modifier.weight(1f))
                }
            }
        }
    }
}
