package com.idleskull.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.idleskull.app.model.SlackingSession
import com.idleskull.app.ui.StatsEngine
import com.idleskull.app.ui.components.PixelChoice
import com.idleskull.app.ui.components.PixelPanel
import com.idleskull.app.ui.components.PixelText
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class StatsRange { DAY, WEEK, MONTH, YEAR }

@Composable
fun StatsScreen(sessions: List<SlackingSession>) {
    var range by remember { mutableStateOf(StatsRange.DAY) }
    val totals = remember(sessions) { StatsEngine.dailyTotals(sessions) }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        PixelText("摆烂统计", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(
                StatsRange.DAY to "日",
                StatsRange.WEEK to "周",
                StatsRange.MONTH to "月",
                StatsRange.YEAR to "年",
            ).forEach { (value, label) ->
                PixelChoice(label, range == value, { range = value }, Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(14.dp))

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (range) {
                StatsRange.DAY -> DayStats(sessions, totals)
                StatsRange.WEEK -> WeekStats(totals)
                StatsRange.MONTH -> MonthStats(totals)
                StatsRange.YEAR -> YearStats(totals)
            }
        }
    }
}

@Composable
private fun DayStats(sessions: List<SlackingSession>, totals: Map<LocalDate, Long>) {
    val today = LocalDate.now()
    val todaySessions = StatsEngine.sessionsOnDay(sessions, today)
    val total = totals[today] ?: 0L
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { SummaryPanel("今天", total, todaySessions.size) }
        if (todaySessions.isEmpty()) {
            item { PixelPanel { Text("今天还没有历史记录。") } }
        } else {
            items(todaySessions) { session -> SessionRow(session) }
        }
    }
}

@Composable
private fun WeekStats(totals: Map<LocalDate, Long>) {
    val days = StatsEngine.weekDays()
    val max = days.maxOfOrNull { totals[it] ?: 0L }?.coerceAtLeast(1L) ?: 1L
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { SummaryPanel("本周", days.sumOf { totals[it] ?: 0L }, days.count { (totals[it] ?: 0L) > 0L }) }
        item {
            PixelPanel {
                Row(
                    Modifier.fillMaxWidth().height(180.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    days.forEach { day ->
                        val value = totals[day] ?: 0L
                        Column(
                            Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Bottom,
                        ) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height((150f * value / max).coerceAtLeast(if (value > 0) 3f else 0f).dp)
                                    .background(MaterialTheme.colorScheme.primary),
                            )
                            Spacer(Modifier.height(5.dp))
                            PixelText(day.dayOfWeek.name.take(1), fontSize = 10.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthStats(totals: Map<LocalDate, Long>) {
    val month = YearMonth.now()
    val days = StatsEngine.monthDays(month)
    val max = days.maxOfOrNull { totals[it] ?: 0L }?.coerceAtLeast(1L) ?: 1L
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { SummaryPanel("${month.monthValue} 月", days.sumOf { totals[it] ?: 0L }, days.count { (totals[it] ?: 0L) > 0L }) }
        item {
            PixelPanel {
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    PixelText("像素热力图", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    days.chunked(7).forEach { week ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            week.forEach { day ->
                                val value = totals[day] ?: 0L
                                val alpha = if (value == 0L) {
                                    0.08f
                                } else {
                                    (0.25f + 0.75f * value / max).toFloat().coerceIn(0.25f, 1f)
                                }
                                Box(
                                    Modifier
                                        .weight(1f)
                                        .height(38.dp)
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = alpha))
                                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    PixelText(
                                        day.dayOfMonth.toString(),
                                        color = if (alpha > 0.55f) {
                                            MaterialTheme.colorScheme.onPrimary
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        },
                                        fontSize = 10.sp,
                                        textAlign = TextAlign.Center,
                                    )
                                }
                            }
                            repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun YearStats(totals: Map<LocalDate, Long>) {
    val months = StatsEngine.yearMonths()
    val monthTotals = months.associateWith { month ->
        StatsEngine.monthDays(month).sumOf { totals[it] ?: 0L }
    }
    val max = monthTotals.values.maxOrNull()?.coerceAtLeast(1L) ?: 1L
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { SummaryPanel("今年", monthTotals.values.sum(), monthTotals.count { it.value > 0L }) }
        item {
            PixelPanel {
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    months.forEach { month ->
                        val value = monthTotals[month] ?: 0L
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            PixelText(
                                String.format(Locale.ROOT, "%02d", month.monthValue),
                                modifier = Modifier.size(width = 30.dp, height = 22.dp),
                                fontSize = 10.sp,
                            )
                            Box(
                                Modifier
                                    .weight(1f)
                                    .height(18.dp)
                                    .border(1.dp, MaterialTheme.colorScheme.outline),
                            ) {
                                Box(
                                    Modifier
                                        .fillMaxWidth((value.toFloat() / max.toFloat()).coerceIn(0f, 1f))
                                        .height(18.dp)
                                        .background(MaterialTheme.colorScheme.primary),
                                )
                            }
                            PixelText(
                                " ${formatShort(value)}",
                                modifier = Modifier.size(width = 78.dp, height = 22.dp),
                                fontSize = 9.sp,
                                textAlign = TextAlign.End,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryPanel(title: String, total: Long, activeDaysOrCount: Int) {
    PixelPanel {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                PixelText(title, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(5.dp))
                PixelText(formatShort(total), fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }
            Text("记录 $activeDaysOrCount", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SessionRow(session: SlackingSession) {
    val zone = ZoneId.systemDefault()
    val start = Instant.ofEpochMilli(session.startedAt).atZone(zone)
    val end = Instant.ofEpochMilli(session.endedAt).atZone(zone)
    PixelPanel {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                PixelText(
                    "${start.format(DateTimeFormatter.ofPattern("HH:mm"))} → ${end.format(DateTimeFormatter.ofPattern("HH:mm"))}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    if (session.mode.name == "COUNT_DOWN") "倒计时" else "正计时",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            PixelText(formatShort(session.durationMs), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

private fun formatShort(ms: Long): String {
    val minutes = ms / 60_000
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h > 0 -> "${h}h ${m}m"
        else -> "${m}m"
    }
}
