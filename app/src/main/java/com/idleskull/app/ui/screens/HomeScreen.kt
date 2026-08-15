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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.idleskull.app.R
import com.idleskull.app.TimerViewModel
import com.idleskull.app.model.ActiveTimer
import com.idleskull.app.model.ActivityType
import com.idleskull.app.model.SkullRules
import com.idleskull.app.model.SkullState
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

    LaunchedEffect(active?.status, active?.anchorAt, active?.plannedMs, active?.activity) {
        now = System.currentTimeMillis()
        if (active?.status != TimerStatus.RUNNING) return@LaunchedEffect

        while (true) {
            val current = System.currentTimeMillis()
            now = current
            viewModel.tick(current)
            val untilNextSecond = 1_000L - (current % 1_000L)
            delay(untilNextSecond.coerceIn(250L, 1_000L))
        }
    }

    val gameNow = (now / 5_000L) * 5_000L
    val summaryNow = (now / 10_000L) * 10_000L
    val displayMs = active?.displayMsAt(now) ?: 0L
    val projection = remember(gameNow, active, viewModel.skullState) {
        viewModel.projectedSkull(gameNow)
    }
    val skull = projection.state
    val homeStatusText = when {
        active == null -> stringResource(R.string.copy_home_idle)
        active.status == TimerStatus.PAUSED -> stringResource(R.string.copy_home_paused)
        active.activity == ActivityType.SLACK -> stringResource(R.string.copy_home_slacking)
        else -> stringResource(R.string.copy_home_grinding, skull.level)
    }
    val slackToday = remember(viewModel.sessions, active, summaryNow) {
        StatsEngine.today(viewModel.sessions, ActivityType.SLACK) +
            activeTodayContribution(active, ActivityType.SLACK, summaryNow)
    }
    val grindToday = remember(viewModel.sessions, active, summaryNow) {
        StatsEngine.today(viewModel.sessions, ActivityType.GRIND) +
            activeTodayContribution(active, ActivityType.GRIND, summaryNow)
    }

    Box(Modifier.fillMaxSize()) {
        SkullBackdrop(
            darkMode = viewModel.darkMode,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .size(320.dp),
            alpha = if (viewModel.darkMode) 0.48f else 0.80f,
        )
        SkullEyeOverlay(
            skull = skull,
            darkMode = viewModel.darkMode,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .size(320.dp),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PixelText(
                text = "今天  摆 ${formatShort(slackToday)} · 卷 ${formatShort(grindToday)}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(18.dp))

            TimerPixelText(
                text = formatClock(displayMs),
                fontSize = 42.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            PixelText(
                text = homeStatusText,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(20.dp))

            TimerModeSelector(
                mode = active?.mode ?: selectedMode,
                countdownMs = active?.plannedMs ?: selectedCountdownMs,
                enabled = active == null,
                onSelectCountUp = { selectedModeName = TimerMode.COUNT_UP.name },
                onSelectCountDown = { showCountdownDialog = true },
            )

            Spacer(Modifier.height(12.dp))

            if (active == null) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    PixelButton(
                        text = stringResource(R.string.copy_start_slack),
                        onClick = {
                            if (selectedMode == TimerMode.COUNT_UP) {
                                viewModel.startCountUp(ActivityType.SLACK)
                            } else {
                                viewModel.startCountdown(ActivityType.SLACK, selectedCountdownMs)
                            }
                        },
                        modifier = Modifier.weight(1f),
                    )
                    PixelButton(
                        text = stringResource(R.string.copy_start_grind),
                        onClick = {
                            if (selectedMode == TimerMode.COUNT_UP) {
                                viewModel.startCountUp(ActivityType.GRIND)
                            } else {
                                viewModel.startCountdown(ActivityType.GRIND, selectedCountdownMs)
                            }
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            } else {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    PixelButton(
                        text = if (active.status == TimerStatus.RUNNING) {
                            stringResource(R.string.copy_pause)
                        } else {
                            stringResource(R.string.copy_resume)
                        },
                        onClick = viewModel::pauseOrResume,
                        modifier = Modifier.weight(1f),
                        inverted = true,
                    )
                    PixelButton(
                        text = stringResource(R.string.copy_end),
                        onClick = viewModel::end,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            PixelText(
                text = "SKULL Lv.${skull.level}",
                modifier = Modifier.fillMaxWidth(),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            SkullHealthBar(
                skull = skull,
                modifier = Modifier.fillMaxWidth(0.84f),
            )

            // Reserve the lower stage for the large skull artwork. The game status now sits
            // between the controls and the skull instead of competing with the timer header.
            Spacer(Modifier.height(254.dp))
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
private fun SkullHealthBar(
    skull: SkullState,
    modifier: Modifier = Modifier,
) {
    val fill = Color(0xFF8E3030)
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(18.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, PixelCutShape)
                .border(2.dp, MaterialTheme.colorScheme.outline, PixelCutShape),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(skull.hpRatio)
                    .height(18.dp)
                    .background(fill, PixelCutShape),
            )
        }
        Spacer(Modifier.height(5.dp))
        PixelText(
            text = "${skull.hp}/${skull.maxHp}",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
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
                    stringResource(R.string.copy_count_up)
                } else {
                    "${stringResource(R.string.copy_count_down)} · ${formatClock(countdownMs)}"
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
                text = { PixelText(stringResource(R.string.copy_count_up), fontSize = 13.sp, fontWeight = FontWeight.Bold) },
                onClick = {
                    expanded = false
                    onSelectCountUp()
                },
            )
            DropdownMenuItem(
                text = { PixelText(stringResource(R.string.copy_count_down), fontSize = 13.sp, fontWeight = FontWeight.Bold) },
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
                    text = stringResource(R.string.copy_countdown_dialog_title),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    DurationField(stringResource(R.string.copy_hour), hours, Modifier.weight(1f)) { hours = sanitizeNumber(it, 2) }
                    Spacer(Modifier.width(8.dp))
                    DurationField(stringResource(R.string.copy_minute), minutes, Modifier.weight(1f)) { minutes = sanitizeNumber(it, 2) }
                    Spacer(Modifier.width(8.dp))
                    DurationField(stringResource(R.string.copy_second), seconds, Modifier.weight(1f)) { seconds = sanitizeNumber(it, 2) }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PixelButton(stringResource(R.string.copy_cancel), onDismiss, Modifier.weight(1f), inverted = true)
                    PixelButton(
                        text = stringResource(R.string.copy_confirm),
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

private fun sanitizeNumber(raw: String, maxDigits: Int): String = raw.filter(Char::isDigit).take(maxDigits)

@Composable
private fun SkullEyeOverlay(
    skull: SkullState,
    darkMode: Boolean,
    modifier: Modifier = Modifier,
) {
    val hpRatio = skull.hpRatio
    if (hpRatio <= 0.015f) return

    // Keep the glow tied to HP only. Avoid a continuous pulse so the static skull
    // does not force extra redraws while the timer is running.
    val pulse = 1f
    val intensity = if (darkMode) {
        (0.12f + hpRatio * 0.80f).coerceIn(0.12f, 0.92f)
    } else {
        (0.22f + hpRatio * 0.78f).coerceIn(0.22f, 1.0f)
    }
    val glowBoost = if (darkMode) 1.0f else 1.65f
    val core = if (darkMode) Color(0xFFFFE6B8) else Color(0xFFF3FFE9)
    val mid = if (darkMode) Color(0xFFFF3925) else Color(0xFF35FF49)
    val outer = if (darkMode) Color(0xFF7A0000) else Color(0xFF007A2A)

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        fun starPath(
            cx: Float,
            cy: Float,
            horizontal: Float,
            vertical: Float,
            innerX: Float,
            innerY: Float,
        ): Path = Path().apply {
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

        fun drawEye(cxRatio: Float, cyRatio: Float) {
            val cx = w * cxRatio
            val cy = h * cyRatio
            val rx = w * (0.0215f + hpRatio * 0.006f)
            val ry = h * (0.0135f + hpRatio * 0.004f)

            drawOval(
                brush = Brush.radialGradient(
                    colors = listOf(
                        core.copy(alpha = (0.08f * glowBoost * pulse * intensity).coerceAtMost(0.30f)),
                        mid.copy(alpha = (0.28f * glowBoost * intensity).coerceAtMost(0.68f)),
                        outer.copy(alpha = (0.14f * glowBoost * intensity).coerceAtMost(0.38f)),
                        Color.Transparent,
                    ),
                    center = Offset(cx, cy),
                    radius = rx * (2.6f + hpRatio * 0.9f),
                ),
                topLeft = Offset(cx - rx * 2.5f, cy - ry * 2.4f),
                size = Size(rx * 5.0f, ry * 4.8f),
            )

            val horizontal = rx * (2.0f + hpRatio * 1.25f)
            val vertical = ry * (2.1f + hpRatio * 1.35f)
            val innerX = rx * 0.42f
            val innerY = ry * 0.42f
            drawPath(
                path = starPath(cx, cy, horizontal, vertical, innerX, innerY),
                color = mid.copy(alpha = (0.22f * glowBoost * intensity * pulse).coerceAtMost(0.60f)),
            )
            drawPath(
                path = starPath(cx, cy, horizontal * 0.72f, vertical * 0.72f, innerX * 0.9f, innerY * 0.9f),
                color = mid.copy(alpha = (0.16f * glowBoost * intensity * pulse).coerceAtMost(0.44f)),
            )
            drawOval(
                brush = Brush.radialGradient(
                    colors = listOf(
                        core.copy(alpha = (0.72f * glowBoost * intensity * pulse).coerceAtMost(1f)),
                        mid.copy(alpha = (0.58f * glowBoost * intensity * pulse).coerceAtMost(0.94f)),
                        Color.Transparent,
                    ),
                    center = Offset(cx, cy),
                    radius = maxOf(rx, ry) * 0.78f,
                ),
                topLeft = Offset(cx - rx * 0.42f, cy - ry * 0.42f),
                size = Size(rx * 0.84f, ry * 0.84f),
            )
        }

        drawEye(0.405f, 0.385f)
        drawEye(0.582f, 0.385f)
    }
}

private fun activeTodayContribution(
    active: ActiveTimer?,
    activity: ActivityType,
    now: Long,
): Long {
    if (active == null || active.activity != activity) return 0L
    if (active.elapsedAt(now) < SkullRules.MIN_VALID_SESSION_MS) return 0L
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
    val totalSeconds = ms.coerceAtLeast(0L) / 1000L
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) "${hours}h ${minutes}m ${seconds}s" else "${minutes}m ${seconds}s"
}
