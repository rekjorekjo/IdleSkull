package com.idleskull.app.ui.screens

import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
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
import com.idleskull.app.ui.components.TimerPixelText
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
    val homeStatus = when {
        active == null -> stringResource(com.idleskull.app.R.string.copy_home_idle)
        active.status == TimerStatus.PAUSED -> stringResource(com.idleskull.app.R.string.copy_home_paused)
        active.mode == TimerMode.COUNT_DOWN -> stringResource(com.idleskull.app.R.string.copy_home_countdown_running)
        else -> stringResource(com.idleskull.app.R.string.copy_home_running)
    }

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
        val effectStage = activeSkullEffectStage(active, now)
        SkullBackdrop(
            darkMode = viewModel.darkMode,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .size(320.dp),
            alpha = if (viewModel.darkMode) 0.48f else 0.80f,
        )
        if (effectStage > 0) {
            SkullRedEyeOverlay(
                stage = effectStage,
                now = now,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .size(320.dp),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PixelText(
                text = stringResource(com.idleskull.app.R.string.copy_home_title),
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

            TimerPixelText(
                text = formatClock(displayMs),
                fontSize = 42.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            PixelText(
                text = homeStatus,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
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
                    text = stringResource(com.idleskull.app.R.string.copy_start),
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
                        text = if (active.status == TimerStatus.RUNNING) stringResource(com.idleskull.app.R.string.copy_pause) else stringResource(com.idleskull.app.R.string.copy_resume),
                        onClick = viewModel::pauseOrResume,
                        modifier = Modifier.weight(1f),
                        inverted = true,
                    )
                    PixelButton(
                        text = stringResource(com.idleskull.app.R.string.copy_end),
                        onClick = viewModel::end,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            Spacer(Modifier.height(286.dp))
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
                    stringResource(com.idleskull.app.R.string.copy_count_up)
                } else {
                    "${stringResource(com.idleskull.app.R.string.copy_count_down)} · ${formatClock(countdownMs)}"
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
                        text = stringResource(com.idleskull.app.R.string.copy_count_up),
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
                        text = stringResource(com.idleskull.app.R.string.copy_count_down),
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
                    text = stringResource(com.idleskull.app.R.string.copy_countdown_dialog_title),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    DurationField(stringResource(com.idleskull.app.R.string.copy_hour), hours, Modifier.weight(1f)) { hours = sanitizeNumber(it, 2) }
                    Spacer(Modifier.width(8.dp))
                    DurationField(stringResource(com.idleskull.app.R.string.copy_minute), minutes, Modifier.weight(1f)) { minutes = sanitizeNumber(it, 2) }
                    Spacer(Modifier.width(8.dp))
                    DurationField(stringResource(com.idleskull.app.R.string.copy_second), seconds, Modifier.weight(1f)) { seconds = sanitizeNumber(it, 2) }
                }

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    PixelButton(stringResource(com.idleskull.app.R.string.copy_cancel), onDismiss, Modifier.weight(1f), inverted = true)
                    PixelButton(
                        text = stringResource(com.idleskull.app.R.string.copy_confirm),
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


@Composable
private fun SkullRedEyeOverlay(
    stage: Int,
    now: Long,
    modifier: Modifier = Modifier,
) {
    val pulse = 0.78f + 0.22f * kotlin.math.sin(now / 360.0).toFloat()
    val intensity = (0.22f + stage * 0.10f).coerceAtMost(0.90f)
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        fun drawEye(cxRatio: Float, cyRatio: Float, rxRatio: Float, ryRatio: Float) {
            val cx = w * cxRatio
            val cy = h * cyRatio
            val rx = w * rxRatio
            val ry = h * ryRatio
            drawOval(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFFFE3A5).copy(alpha = 0.10f * pulse * intensity),
                        Color(0xFFFF3C1F).copy(alpha = 0.40f * intensity),
                        Color(0xFF8B0000).copy(alpha = 0.14f * intensity),
                        Color.Transparent,
                    ),
                    center = Offset(cx, cy),
                    radius = rx * 2.8f,
                ),
                topLeft = Offset(cx - rx * 2.0f, cy - ry * 2.0f),
                size = Size(rx * 4.0f, ry * 4.0f),
            )

            val horizontal = rx * (2.5f + stage * 0.10f)
            val vertical = ry * (2.6f + stage * 0.10f)
            val innerX = rx * 0.42f
            val innerY = ry * 0.42f
            val star = Path().apply {
                moveTo(cx, cy - vertical)
                lineTo(cx + innerX, cy - innerY)
                lineTo(cx + horizontal, cy)
                lineTo(cx + innerX, cy + innerY)
                lineTo(cx, cy + vertical)
                lineTo(cx - innerX, cy + innerY)
                lineTo(cx - horizontal, cy)
                lineTo(cx - innerX, cy - innerY)
                close()
            }
            drawPath(
                path = star,
                color = Color(0xFFFF2B18).copy(alpha = (0.64f * intensity * pulse).coerceAtMost(0.92f)),
            )
            drawOval(
                color = Color(0xFFFFF3D0).copy(alpha = (0.52f * intensity * pulse).coerceAtMost(0.82f)),
                topLeft = Offset(cx - rx * 0.34f, cy - ry * 0.34f),
                size = Size(rx * 0.68f, ry * 0.68f),
            )
        }
        drawEye(0.405f, 0.385f, 0.023f + stage * 0.0010f, 0.015f + stage * 0.0008f)
        drawEye(0.595f, 0.385f, 0.023f + stage * 0.0010f, 0.015f + stage * 0.0008f)
    }
}

private fun activeSkullEffectStage(active: ActiveTimer?, now: Long): Int {
    val timer = active ?: return 0
    val minutes = (timer.elapsedAt(now).coerceAtLeast(0L) / 60_000L).toInt()
    return (minutes / 10).coerceIn(0, 6)
}

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


private fun formatClock(ms: Long): String {
    val total = ms.coerceAtLeast(0L) / 1000L
    val hours = total / 3600L
    val minutes = (total % 3600L) / 60L
    val seconds = total % 60L
    return String.format(Locale.ROOT, "%02d:%02d:%02d", hours, minutes, seconds)
}

private fun formatShort(ms: Long): String {
    val totalSeconds = (ms.coerceAtLeast(0L) / 1000L)
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        "${hours}h ${minutes}m ${seconds}s"
    } else {
        "${minutes}m ${seconds}s"
    }
}
