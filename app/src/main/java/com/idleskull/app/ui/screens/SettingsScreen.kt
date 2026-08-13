package com.idleskull.app.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.idleskull.app.BuildConfig
import com.idleskull.app.R
import com.idleskull.app.TimerViewModel
import com.idleskull.app.ui.components.PixelButton
import com.idleskull.app.ui.components.PixelChoice
import com.idleskull.app.ui.components.PixelCutShape
import com.idleskull.app.ui.components.PixelPanel
import com.idleskull.app.ui.components.PixelText

@Composable
fun SettingsScreen(viewModel: TimerViewModel) {
    var showUpdateNotes by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PixelText(
            text = "设置",
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
                PixelText("更新", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("当前版本", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    PixelText(
                        text = BuildConfig.VERSION_NAME,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.End,
                    )
                }
                PixelButton("查看更新说明", { showUpdateNotes = true }, Modifier.fillMaxWidth(), inverted = true)
            }
        }

        PixelPanel {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PixelText("数据", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                PixelButton("清空历史记录", { showClearConfirm = true }, Modifier.fillMaxWidth())
            }
        }

        Spacer(Modifier.height(12.dp))
        Text(
            "IdleSkull · ${BuildConfig.VERSION_NAME}",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(8.dp))
    }

    if (showUpdateNotes) {
        UpdateNotesDialog(onDismiss = { showUpdateNotes = false })
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
                Text("此操作无法撤销。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PixelButton("取消", onDismiss, Modifier.weight(1f), inverted = true)
                    PixelButton("清空", onConfirm, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun UpdateNotesDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val notes = remember {
        runCatching {
            context.resources.openRawResource(R.raw.update_notes)
                .bufferedReader(Charsets.UTF_8)
                .use { it.readText() }
        }.getOrElse { "暂无更新说明。" }
    }

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
                Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PixelText("更新说明", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(
                    notes,
                    modifier = Modifier
                        .heightIn(max = 380.dp)
                        .verticalScroll(rememberScrollState()),
                    style = MaterialTheme.typography.bodyMedium,
                )
                PixelButton("关闭", onDismiss, Modifier.fillMaxWidth())
            }
        }
    }
}
