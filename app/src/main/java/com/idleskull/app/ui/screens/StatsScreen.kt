package com.idleskull.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.idleskull.app.TimerViewModel
import com.idleskull.app.model.SlackingSession
import com.idleskull.app.ui.StatsEngine
import com.idleskull.app.ui.components.PixelButton
import com.idleskull.app.ui.components.PixelCutShape
import com.idleskull.app.ui.components.PixelPanel
import com.idleskull.app.ui.components.PixelInputField
import com.idleskull.app.ui.components.PixelText
import com.idleskull.app.ui.export.StatsExportSpec
import com.idleskull.app.ui.export.StatsExportStyle
import com.idleskull.app.ui.export.StatsShareManager
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class StatsRange { DAY, WEEK, MONTH, YEAR }

@Composable
fun StatsScreen(viewModel: TimerViewModel) {
    val context = LocalContext.current
    val sessions = viewModel.statsSessions
    val today = LocalDate.now()

    var rangeName by rememberSaveable { mutableStateOf(StatsRange.DAY.name) }
    val range = StatsRange.valueOf(rangeName)
    var showExportStyle by remember { mutableStateOf(false) }

    // Each range remembers its own cursor. Switching from June's month view back to
    // day view no longer drags the day cursor into June.
    var dayEpoch by rememberSaveable { mutableLongStateOf(today.toEpochDay()) }
    var weekEpoch by rememberSaveable { mutableLongStateOf(today.toEpochDay()) }
    var monthEpoch by rememberSaveable { mutableLongStateOf(today.withDayOfMonth(1).toEpochDay()) }
    var yearEpoch by rememberSaveable { mutableLongStateOf(today.withDayOfYear(1).toEpochDay()) }

    val anchorDate = when (range) {
        StatsRange.DAY -> LocalDate.ofEpochDay(dayEpoch)
        StatsRange.WEEK -> LocalDate.ofEpochDay(weekEpoch)
        StatsRange.MONTH -> LocalDate.ofEpochDay(monthEpoch)
        StatsRange.YEAR -> LocalDate.ofEpochDay(yearEpoch)
    }

    fun updateAnchor(date: LocalDate) {
        val safe = date.coerceAtMost(today)
        when (range) {
            StatsRange.DAY -> dayEpoch = safe.toEpochDay()
            StatsRange.WEEK -> weekEpoch = safe.toEpochDay()
            StatsRange.MONTH -> monthEpoch = safe.withDayOfMonth(1).toEpochDay()
            StatsRange.YEAR -> yearEpoch = safe.withDayOfYear(1).toEpochDay()
        }
    }

    val totals = remember(sessions) { StatsEngine.dailyTotals(sessions) }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PixelText(
                stringResource(com.idleskull.app.R.string.copy_stats_title),
                modifier = Modifier.weight(1f),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
            )
            PixelButton(
                text = stringResource(com.idleskull.app.R.string.copy_export),
                onClick = { showExportStyle = true },
                padding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                inverted = true,
            )
        }
        Spacer(Modifier.height(10.dp))

        StatsRangeSelector(
            range = range,
            onRangeSelected = { rangeName = it.name },
        )

        Spacer(Modifier.height(10.dp))
        PeriodNavigator(
            range = range,
            label = periodLabel(range, anchorDate),
            anchorDate = anchorDate,
            canGoNext = !isCurrentPeriod(range, anchorDate),
            onDateSelected = ::updateAnchor,
            onPrevious = { updateAnchor(shiftAnchor(range, anchorDate, -1)) },
            onNext = { updateAnchor(shiftAnchor(range, anchorDate, 1)) },
        )
        Spacer(Modifier.height(10.dp))

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (range) {
                StatsRange.DAY -> DayStats(
                    sessions = sessions,
                    totals = totals,
                    day = anchorDate,
                    onRename = viewModel::renameSession,
                )
                StatsRange.WEEK -> WeekStats(totals, anchorDate)
                StatsRange.MONTH -> MonthStats(totals, YearMonth.from(anchorDate))
                StatsRange.YEAR -> YearStats(totals, anchorDate.year)
            }
        }
    }

    if (showExportStyle) {
        ExportStyleDialog(
            onDismiss = { showExportStyle = false },
            onSelected = { style ->
                showExportStyle = false
                val spec = StatsExportSpec(
                    range = range,
                    anchorDate = anchorDate,
                    sessions = sessions,
                    darkMode = viewModel.darkMode,
                    style = style,
                )
                val result = runCatching { StatsShareManager.share(context, spec) }
                if (result.isFailure) {
                    Toast.makeText(
                        context,
                        "生成分享图片失败：${result.exceptionOrNull()?.message ?: "未知错误"}",
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            },
        )
    }
}

@Composable
private fun StatsRangeSelector(
    range: StatsRange,
    onRangeSelected: (StatsRange) -> Unit,
) {
    val items = listOf(
        StatsRange.DAY to "日",
        StatsRange.WEEK to "周",
        StatsRange.MONTH to "月",
        StatsRange.YEAR to "年",
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, PixelCutShape)
            .border(2.dp, MaterialTheme.colorScheme.outline, PixelCutShape)
            .padding(2.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(androidx.compose.foundation.layout.IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEachIndexed { index, (value, label) ->
                val selected = value == range
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(
                            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                        )
                        .clickable { onRangeSelected(value) }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    PixelText(
                        text = label,
                        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                }
                if (index < items.lastIndex) {
                    Box(
                        modifier = Modifier
                            .width(2.dp)
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)),
                    )
                }
            }
        }
    }
}

@Composable
private fun PeriodNavigator(
    range: StatsRange,
    label: String,
    anchorDate: LocalDate,
    canGoNext: Boolean,
    onDateSelected: (LocalDate) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    var showPicker by remember(range, anchorDate) { mutableStateOf(false) }

    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PixelButton(
            "‹",
            onPrevious,
            modifier = Modifier.width(48.dp),
            inverted = true,
            padding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp),
        )
        PixelButton(
            text = "$label  ▼",
            onClick = { showPicker = true },
            modifier = Modifier.weight(1f),
            inverted = true,
            padding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp),
        )
        PixelButton(
            "›",
            onNext,
            modifier = Modifier.width(48.dp),
            enabled = canGoNext,
            inverted = true,
            padding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp),
        )
    }

    if (showPicker) {
        when (range) {
            StatsRange.DAY, StatsRange.WEEK -> PixelCalendarDialog(
                range = range,
                initialDate = anchorDate,
                onDismiss = { showPicker = false },
                onConfirm = {
                    onDateSelected(it)
                    showPicker = false
                },
            )
            StatsRange.MONTH -> PixelMonthInputDialog(
                initialMonth = YearMonth.from(anchorDate),
                onDismiss = { showPicker = false },
                onConfirm = {
                    onDateSelected(it.atDay(1))
                    showPicker = false
                },
            )
            StatsRange.YEAR -> PixelYearInputDialog(
                initialYear = anchorDate.year,
                onDismiss = { showPicker = false },
                onConfirm = {
                    onDateSelected(LocalDate.of(it, 1, 1))
                    showPicker = false
                },
            )
        }
    }
}

@Composable
private fun PixelCalendarDialog(
    range: StatsRange,
    initialDate: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate) -> Unit,
) {
    val today = LocalDate.now()
    var selectedEpoch by remember(initialDate) { mutableLongStateOf(initialDate.toEpochDay()) }
    var visibleMonth by remember(initialDate) { mutableStateOf(YearMonth.from(initialDate)) }
    val selected = LocalDate.ofEpochDay(selectedEpoch)
    val todayMonth = YearMonth.from(today)
    val leading = visibleMonth.atDay(1).dayOfWeek.value - 1
    val monthDays = (1..visibleMonth.lengthOfMonth()).map(visibleMonth::atDay)
    val cells: List<LocalDate?> = List(leading) { null } + monthDays
    val selectedWeek = if (range == StatsRange.WEEK) StatsEngine.weekDays(selected).toSet() else emptySet()

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
                Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PixelText(
                    if (range == StatsRange.WEEK) "选择所在周" else "选择日期",
                    modifier = Modifier.fillMaxWidth(),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PixelButton(
                        "‹",
                        onClick = { visibleMonth = visibleMonth.minusMonths(1) },
                        modifier = Modifier.width(46.dp),
                        inverted = true,
                        padding = androidx.compose.foundation.layout.PaddingValues(vertical = 7.dp),
                    )
                    PixelText(
                        "${visibleMonth.year} 年 ${visibleMonth.monthValue} 月",
                        modifier = Modifier.weight(1f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                    PixelButton(
                        "›",
                        onClick = { visibleMonth = visibleMonth.plusMonths(1) },
                        modifier = Modifier.width(46.dp),
                        enabled = visibleMonth < todayMonth,
                        inverted = true,
                        padding = androidx.compose.foundation.layout.PaddingValues(vertical = 7.dp),
                    )
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("一", "二", "三", "四", "五", "六", "日").forEach { label ->
                        PixelText(
                            label,
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 9.sp,
                            textAlign = TextAlign.Center,
                        )
                    }
                }

                cells.chunked(7).forEach { week ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        week.forEach { day ->
                            if (day == null) {
                                Spacer(Modifier.weight(1f).height(40.dp))
                            } else {
                                val enabled = day <= today
                                val selectedCell = when (range) {
                                    StatsRange.WEEK -> day in selectedWeek
                                    else -> day == selected
                                }
                                val background = if (selectedCell) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.surface
                                }
                                val foreground = if (selectedCell) {
                                    MaterialTheme.colorScheme.onPrimary
                                } else if (enabled) {
                                    MaterialTheme.colorScheme.onSurface
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.28f)
                                }
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(40.dp)
                                        .background(background)
                                        .border(
                                            1.dp,
                                            MaterialTheme.colorScheme.outline.copy(
                                                alpha = if (selectedCell) 1f else 0.35f,
                                            ),
                                        )
                                        .clickable(enabled = enabled) { selectedEpoch = day.toEpochDay() },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    PixelText(
                                        day.dayOfMonth.toString(),
                                        color = foreground,
                                        fontSize = 10.sp,
                                        textAlign = TextAlign.Center,
                                    )
                                }
                            }
                        }
                        repeat(7 - week.size) { Spacer(Modifier.weight(1f).height(40.dp)) }
                    }
                }

                PixelText(
                    if (range == StatsRange.WEEK) {
                        val days = StatsEngine.weekDays(selected)
                        "${monthDay(days.first())} - ${monthDay(days.last())}"
                    } else {
                        "${selected.year}.${selected.monthValue}.${selected.dayOfMonth}"
                    },
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center,
                )

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PixelButton("取消", onDismiss, Modifier.weight(1f), inverted = true)
                    PixelButton("确定", { onConfirm(selected) }, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun PixelMonthInputDialog(
    initialMonth: YearMonth,
    onDismiss: () -> Unit,
    onConfirm: (YearMonth) -> Unit,
) {
    val current = YearMonth.now()
    var yearText by remember(initialMonth) { mutableStateOf(initialMonth.year.toString()) }
    var monthText by remember(initialMonth) { mutableStateOf(initialMonth.monthValue.toString()) }
    val selected = runCatching {
        YearMonth.of(yearText.toInt(), monthText.toInt())
    }.getOrNull()
    val valid = selected != null && selected.year >= 1 && selected <= current

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = PixelCutShape,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            modifier = Modifier.fillMaxWidth().border(2.dp, MaterialTheme.colorScheme.outline, PixelCutShape),
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                PixelText("跳转到月份", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(Modifier.weight(1.5f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        PixelText("年", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        PixelInputField(
                            value = yearText,
                            onValueChange = { yearText = it.filter { ch -> ch.isDigit() }.take(4) },
                            numeric = true,
                        )
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        PixelText("月", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        PixelInputField(
                            value = monthText,
                            onValueChange = { monthText = it.filter { ch -> ch.isDigit() }.take(2) },
                            numeric = true,
                        )
                    }
                }
                PixelText(
                    "例如：2026 年 6 月",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 9.sp,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PixelButton("取消", onDismiss, Modifier.weight(1f), inverted = true)
                    PixelButton(
                        "确定",
                        { selected?.let(onConfirm) },
                        Modifier.weight(1f),
                        enabled = valid,
                    )
                }
            }
        }
    }
}

@Composable
private fun PixelYearInputDialog(
    initialYear: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    val currentYear = LocalDate.now().year
    var yearText by remember(initialYear) { mutableStateOf(initialYear.toString()) }
    val year = yearText.toIntOrNull()
    val valid = year != null && year in 1..currentYear

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = PixelCutShape,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            modifier = Modifier.fillMaxWidth().border(2.dp, MaterialTheme.colorScheme.outline, PixelCutShape),
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                PixelText("跳转到年份", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                PixelInputField(
                    value = yearText,
                    onValueChange = { yearText = it.filter { ch -> ch.isDigit() }.take(4) },
                    numeric = true,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PixelButton("取消", onDismiss, Modifier.weight(1f), inverted = true)
                    PixelButton(
                        "确定",
                        { year?.let(onConfirm) },
                        Modifier.weight(1f),
                        enabled = valid,
                    )
                }
            }
        }
    }
}

@Composable
private fun ExportStyleDialog(
    onDismiss: () -> Unit,
    onSelected: (StatsExportStyle) -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = PixelCutShape,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            modifier = Modifier.fillMaxWidth().border(2.dp, MaterialTheme.colorScheme.outline, PixelCutShape),
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                PixelText(stringResource(com.idleskull.app.R.string.copy_export_style_title), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PixelButton(
                        stringResource(com.idleskull.app.R.string.copy_export_mono),
                        { onSelected(StatsExportStyle.MONOCHROME) },
                        Modifier.weight(1f),
                        inverted = true,
                    )
                    PixelButton(
                        stringResource(com.idleskull.app.R.string.copy_export_color),
                        { onSelected(StatsExportStyle.COLOR) },
                        Modifier.weight(1f),
                    )
                }
                PixelButton(stringResource(com.idleskull.app.R.string.copy_cancel), onDismiss, Modifier.fillMaxWidth(), inverted = true)
            }
        }
    }
}

@Composable
private fun DayStats(
    sessions: List<SlackingSession>,
    totals: Map<LocalDate, Long>,
    day: LocalDate,
    onRename: (Long, String) -> Unit,
) {
    val daySessions = StatsEngine.sessionsOnDay(sessions, day)
    val total = totals[day] ?: 0L
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { SummaryPanel("总摆烂", total, daySessions.size) }
        if (daySessions.isEmpty()) {
            item { PixelPanel { PixelText(stringResource(com.idleskull.app.R.string.copy_empty_day), fontSize = 11.sp) } }
        } else {
            items(daySessions, key = { it.id }) { session ->
                SessionRow(session = session, onRename = onRename)
            }
        }
    }
}

@Composable
private fun WeekStats(totals: Map<LocalDate, Long>, anchorDate: LocalDate) {
    val days = StatsEngine.weekDays(anchorDate)
    val max = days.maxOfOrNull { totals[it] ?: 0L }?.coerceAtLeast(1L) ?: 1L
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { SummaryPanel("周总计", days.sumOf { totals[it] ?: 0L }, days.count { (totals[it] ?: 0L) > 0L }) }
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
                            PixelText(weekdayLabel(day), fontSize = 10.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthStats(totals: Map<LocalDate, Long>, month: YearMonth) {
    val days = StatsEngine.monthDays(month)
    val max = days.maxOfOrNull { totals[it] ?: 0L }?.coerceAtLeast(1L) ?: 1L
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { SummaryPanel("月总计", days.sumOf { totals[it] ?: 0L }, days.count { (totals[it] ?: 0L) > 0L }) }
        item {
            PixelPanel {
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    PixelText("像素热力图", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        listOf("一", "二", "三", "四", "五", "六", "日").forEach { label ->
                            PixelText(
                                label,
                                modifier = Modifier.weight(1f),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 9.sp,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                    val leading = month.atDay(1).dayOfWeek.value - 1
                    val cells: List<LocalDate?> = List(leading) { null } + days
                    cells.chunked(7).forEach { week ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            week.forEach { day ->
                                if (day == null) {
                                    Spacer(Modifier.weight(1f).height(38.dp))
                                } else {
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
                            }
                            repeat(7 - week.size) { Spacer(Modifier.weight(1f).height(38.dp)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun YearStats(totals: Map<LocalDate, Long>, year: Int) {
    val months = StatsEngine.yearMonths(year)
    val monthTotals = months.associateWith { month ->
        StatsEngine.monthDays(month).sumOf { totals[it] ?: 0L }
    }
    val max = monthTotals.values.maxOrNull()?.coerceAtLeast(1L) ?: 1L
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { SummaryPanel("年总计", monthTotals.values.sum(), monthTotals.count { it.value > 0L }) }
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
                                modifier = Modifier.size(width = 100.dp, height = 22.dp),
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
            PixelText(
                "记录 $activeDaysOrCount",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SessionRow(
    session: SlackingSession,
    onRename: (Long, String) -> Unit,
) {
    var showRename by remember(session.id) { mutableStateOf(false) }
    val zone = ZoneId.systemDefault()
    val start = Instant.ofEpochMilli(session.startedAt).atZone(zone)
    val end = Instant.ofEpochMilli(session.endedAt).atZone(zone)
    val unnamed = stringResource(com.idleskull.app.R.string.copy_unnamed)

    PixelPanel(
        modifier = Modifier.combinedClickable(
            onClick = {},
            onLongClick = { showRename = true },
        ),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                PixelText(
                    session.name.ifBlank { unnamed },
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(4.dp))
                PixelText(
                    "${start.format(DateTimeFormatter.ofPattern("HH:mm"))} → ${end.format(DateTimeFormatter.ofPattern("HH:mm"))}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp,
                )
            }
            PixelText(formatShort(session.durationMs), modifier = Modifier.width(92.dp), textAlign = TextAlign.End, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }

    if (showRename) {
        RenameSessionDialog(
            initialName = session.name,
            onDismiss = { showRename = false },
            onConfirm = { name ->
                onRename(session.id, name)
                showRename = false
            },
        )
    }
}

@Composable
private fun RenameSessionDialog(
    initialName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember(initialName) { mutableStateOf(initialName.ifBlank { "未命名" }) }
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
                PixelText("给这次摆烂起个名字", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(30) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = PixelCutShape,
                    label = { Text("名称") },
                    placeholder = { Text("未命名") },
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PixelButton("取消", onDismiss, Modifier.weight(1f), inverted = true)
                    PixelButton("保存", { onConfirm(name.ifBlank { "未命名" }) }, Modifier.weight(1f))
                }
            }
        }
    }
}


private fun periodLabel(range: StatsRange, anchor: LocalDate): String {
    val today = LocalDate.now()
    return when (range) {
        StatsRange.DAY -> if (anchor == today) {
            "今天 · ${monthDay(anchor)}"
        } else {
            "${anchor.year}.${anchor.monthValue}.${anchor.dayOfMonth}"
        }
        StatsRange.WEEK -> {
            val days = StatsEngine.weekDays(anchor)
            val prefix = if (StatsEngine.weekDays(today).first() == days.first()) "本周 · " else ""
            prefix + "${monthDay(days.first())}-${monthDay(days.last())}"
        }
        StatsRange.MONTH -> {
            val prefix = if (YearMonth.from(anchor) == YearMonth.from(today)) "本月 · " else ""
            prefix + "${anchor.year}.${anchor.monthValue}"
        }
        StatsRange.YEAR -> {
            val prefix = if (anchor.year == today.year) "今年 · " else ""
            prefix + anchor.year.toString()
        }
    }
}

private fun monthDay(date: LocalDate): String = "${date.monthValue}.${date.dayOfMonth}"

private fun weekdayLabel(date: LocalDate): String = when (date.dayOfWeek.value) {
    1 -> "一"
    2 -> "二"
    3 -> "三"
    4 -> "四"
    5 -> "五"
    6 -> "六"
    else -> "日"
}

private fun shiftAnchor(range: StatsRange, date: LocalDate, amount: Long): LocalDate = when (range) {
    StatsRange.DAY -> date.plusDays(amount)
    StatsRange.WEEK -> date.plusWeeks(amount)
    StatsRange.MONTH -> date.plusMonths(amount)
    StatsRange.YEAR -> date.plusYears(amount)
}

private fun isCurrentPeriod(range: StatsRange, anchor: LocalDate): Boolean {
    val today = LocalDate.now()
    return when (range) {
        StatsRange.DAY -> anchor >= today
        StatsRange.WEEK -> StatsEngine.weekDays(anchor).first() >= StatsEngine.weekDays(today).first()
        StatsRange.MONTH -> YearMonth.from(anchor) >= YearMonth.from(today)
        StatsRange.YEAR -> anchor.year >= today.year
    }
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
