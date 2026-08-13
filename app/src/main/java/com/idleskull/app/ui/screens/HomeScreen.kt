package com.idleskull.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.idleskull.app.TimerViewModel
import com.idleskull.app.model.ActiveTimer
import com.idleskull.app.model.TimerMode
import com.idleskull.app.model.TimerStatus
import com.idleskull.app.ui.StatsEngine
import com.idleskull.app.ui.components.PixelButton
import com.idleskull.app.ui.components.PixelCutShape
import com.idleskull.app.ui.components.PixelText
import com.idleskull.app.ui.components.SkullBackdrop
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
fun HomeScreen(viewModel: TimerViewModel) {
    var selectedModeName by rememberSaveable { mutableStateOf(TimerMode.COUNT_UP.name) }
    var selectedCountdownMs by rememberSaveable { mutableStateOf(viewModel.lastCountdownMs) }
    var showCountdownDialog by rememberSaveable { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val selectedMode = TimerMode.valueOf(selectedModeName)
    val active = viewModel.active

    LaunchedEffect(active?.status, active?.anchorAt, active?.plannedMs) {
        while (true) {
            now = System.currentTimeMillis()
            viewModel.tick(now)
            delay(250)
        }
    }

    val displayMs = active?.displayMsAt(now) ?: 0L
    val todayTotal = StatsEngine.today(viewModel.sessions) + activeTodayContribution(active, now)

    Box(Modifier.fillMaxSize()) {
        SkullBackdrop(
            darkMode = viewModel.darkMode,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .size(340.dp),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PixelText(
                text = "今天你又摆烂了？",
                fontSize = 25.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(6.dp))
            PixelText(
                text = "今天  ${formatShort(todayTotal)}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(28.dp))

            PixelText(
                text = formatClock(displayMs),
                fontSize = 42.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = statusLine(active),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(26.dp))

            TimerModeSelector(
                mode = active?.mode ?: selectedMode,
                countdownMs = active?.plannedMs ?: selectedCountdownMs,
                enabled = active == null,
                onSelectCountUp = { selectedModeName = TimerMode.COUNT_UP.name },
                onSelectCountDown = { showCountdownDialog = true },
            )

            Spacer(Modifier.height(12.dp))

            if (active == null) {
                PixelButton(
                    text = "开始摆烂",
                    onClick = {
                        if (selectedMode == TimerMode.COUNT_UP) {
                            viewModel.startCountUp()
                        } else {
                            viewModel.startCountdown(selectedCountdownMs)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    PixelButton(
                        text = if (active.status == TimerStatus.RUNNING) "暂停" else "继续",
                        onClick = viewModel::pauseOrResume,
                        modifier = Modifier.weight(1f),
                        inverted = true,
                    )
                    PixelButton(
                        text = "结束摆烂",
                        onClick = viewModel::end,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            Spacer(Modifier.height(300.dp))
        }
    }

    if (showCountdownDialog) {
        CountdownDurationDialog(
            initialMs = selectedCountdownMs,
            onDismiss = { showCountdownDialog = false },
            onConfirm = { durationMs ->
                selectedModeName = TimerMode.COUNT_DOWN.name
                selectedCountdownMs = durationMs
                viewModel.rememberCountdownDuration(durationMs)
                showCountdownDialog = false
            },
        )
    }
}

@Composable
private fun TimerModeSelector(
    mode: TimerMode,
    countdownMs: Long,
    enabled: Boolean,
    onSelectCountUp: () -> Unit,
    onSelectCountDown: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val foreground = if (enabled) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    }

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val selectorWidth = maxWidth
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface, PixelCutShape)
                .border(2.dp, MaterialTheme.colorScheme.outline, PixelCutShape)
                .clickable(enabled = enabled) { expanded = true }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PixelText(
                text = if (mode == TimerMode.COUNT_UP) {
                    "正计时"
                } else {
                    "倒计时 · ${formatClock(countdownMs)}"
                },
                color = foreground,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            PixelText(
                text = if (expanded) "▲" else "▼",
                color = foreground,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.width(selectorWidth),
            containerColor = MaterialTheme.colorScheme.surface,
            shape = PixelCutShape,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            border = androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.outline),
        ) {
            DropdownMenuItem(
                text = {
                    PixelText(
                        text = "正计时",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                    )
                },
                onClick = {
                    expanded = false
                    onSelectCountUp()
                },
            )
            DropdownMenuItem(
                text = {
                    PixelText(
                        text = "倒计时",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                    )
                },
                onClick = {
                    expanded = false
                    onSelectCountDown()
                },
            )
        }
    }
}

@Composable
private fun CountdownDurationDialog(
    initialMs: Long,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit,
) {
    val totalSeconds = (initialMs / 1000L).coerceAtLeast(1L)
    var hours by rememberSaveable { mutableStateOf((totalSeconds / 3600L).coerceAtMost(99L).toString()) }
    var minutes by rememberSaveable { mutableStateOf(((totalSeconds % 3600L) / 60L).toString()) }
    var seconds by rememberSaveable { mutableStateOf((totalSeconds % 60L).toString()) }

    val h = hours.toIntOrNull()?.coerceIn(0, 99) ?: 0
    val m = minutes.toIntOrNull()?.coerceIn(0, 59) ?: 0
    val s = seconds.toIntOrNull()?.coerceIn(0, 59) ?: 0
    val durationMs = ((h * 3600L) + (m * 60L) + s) * 1000L

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
                modifier = Modifier.padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                PixelText(
                    text = "设置摆烂时间",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    DurationField("时", hours, Modifier.weight(1f)) { hours = sanitizeNumber(it, 2) }
                    Spacer(Modifier.width(8.dp))
                    DurationField("分", minutes, Modifier.weight(1f)) { minutes = sanitizeNumber(it, 2) }
                    Spacer(Modifier.width(8.dp))
                    DurationField("秒", seconds, Modifier.weight(1f)) { seconds = sanitizeNumber(it, 2) }
                }

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    PixelButton("取消", onDismiss, Modifier.weight(1f), inverted = true)
                    PixelButton(
                        text = "确定",
                        onClick = { onConfirm(durationMs) },
                        modifier = Modifier.weight(1f),
                        enabled = durationMs > 0L,
                    )
                }
            }
        }
    }
}

@Composable
private fun DurationField(
    unit: String,
    value: String,
    modifier: Modifier = Modifier,
    onValueChange: (String) -> Unit,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            textStyle = MaterialTheme.typography.titleMedium.copy(textAlign = TextAlign.Center),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            shape = PixelCutShape,
        )
        Spacer(Modifier.height(5.dp))
        PixelText(
            text = unit,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
        )
    }
}

private fun sanitizeNumber(raw: String, maxDigits: Int): String =
    raw.filter(Char::isDigit).take(maxDigits)

private fun activeTodayContribution(active: ActiveTimer?, now: Long): Long {
    if (active == null) return 0L
    val startOfDay = java.time.LocalDate.now()
        .atStartOfDay(java.time.ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli()
    var total = active.completedSegments.sumOf { seg ->
        (seg.endAt - maxOf(seg.startAt, startOfDay)).coerceAtLeast(0L)
    }
    if (active.status == TimerStatus.RUNNING) {
        total += (now - maxOf(active.anchorAt, startOfDay)).coerceAtLeast(0L)
    }
    return if (active.mode == TimerMode.COUNT_DOWN && active.plannedMs != null) {
        minOf(total, active.plannedMs)
    } else {
        total
    }
}

private fun statusLine(active: ActiveTimer?): String = when {
    active == null -> "还没开始。至少现在没有。"
    active.status == TimerStatus.PAUSED -> "摆烂暂停中"
    active.mode == TimerMode.COUNT_DOWN -> "倒计时摆烂中"
    else -> "摆烂计时中"
}

private fun formatClock(ms: Long): String {
    val total = ms.coerceAtLeast(0L) / 1000L
    val hours = total / 3600L
    val minutes = (total % 3600L) / 60L
    val seconds = total % 60L
    return String.format(Locale.ROOT, "%02d:%02d:%02d", hours, minutes, seconds)
}

private fun formatShort(ms: Long): String {
    val minutes = ms / 60_000L
    val h = minutes / 60L
    val m = minutes % 60L
    return if (h > 0L) "${h}h ${m}m" else "${m}m"
}
