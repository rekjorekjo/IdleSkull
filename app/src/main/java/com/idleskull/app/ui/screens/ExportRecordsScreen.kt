package com.idleskull.app.ui.screens

import androidx.activity.compose.BackHandler
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.idleskull.app.TimerViewModel
import com.idleskull.app.model.ActivityType
import com.idleskull.app.ui.components.PixelButton
import com.idleskull.app.ui.components.PixelChoice
import com.idleskull.app.ui.components.PixelCutShape
import com.idleskull.app.ui.components.PixelPanel
import com.idleskull.app.ui.components.PixelText
import com.idleskull.app.ui.export.StatsExportSpec
import com.idleskull.app.ui.export.StatsExportStyle
import com.idleskull.app.ui.export.StatsShareManager
import java.time.LocalDate

@Composable
fun ExportRecordsScreen(
    viewModel: TimerViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    BackHandler(onBack = onBack)
    val today = LocalDate.now()
    var activityName by rememberSaveable { mutableStateOf(ActivityType.SLACK.name) }
    val activity = ActivityType.valueOf(activityName)
    var rangeName by rememberSaveable { mutableStateOf(StatsRange.DAY.name) }
    val range = StatsRange.valueOf(rangeName)
    var styleName by rememberSaveable { mutableStateOf(StatsExportStyle.MONOCHROME.name) }
    val style = StatsExportStyle.valueOf(styleName)

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

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PixelButton("‹", onBack, Modifier.weight(0.22f), inverted = true)
            PixelText("导出记录", Modifier.weight(1f), fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }

        PixelPanel {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PixelText("记录类型", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                ActivityExportSelector(activity) { activityName = it.name }
            }
        }

        PixelPanel {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PixelText("统计范围", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                StatsRangeSelector(range) { rangeName = it.name }
                PeriodNavigator(
                    range = range,
                    label = periodLabel(range, anchorDate),
                    anchorDate = anchorDate,
                    canGoNext = !isCurrentPeriod(range, anchorDate),
                    onDateSelected = ::updateAnchor,
                    onPrevious = { updateAnchor(shiftAnchor(range, anchorDate, -1)) },
                    onNext = { updateAnchor(shiftAnchor(range, anchorDate, 1)) },
                )
            }
        }

        PixelPanel {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PixelText("导出样式", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PixelChoice(
                        "黑白",
                        style == StatsExportStyle.MONOCHROME,
                        { styleName = StatsExportStyle.MONOCHROME.name },
                        Modifier.weight(1f),
                    )
                    PixelChoice(
                        "彩色",
                        style == StatsExportStyle.COLOR,
                        { styleName = StatsExportStyle.COLOR.name },
                        Modifier.weight(1f),
                    )
                }
            }
        }

        PixelButton(
            text = "生成并分享",
            onClick = {
                val result = runCatching {
                    StatsShareManager.share(
                        context,
                        StatsExportSpec(
                            range = range,
                            anchorDate = anchorDate,
                            activity = activity,
                            sessions = viewModel.statsSessions,
                            darkMode = viewModel.darkMode,
                            style = style,
                        ),
                    )
                }
                if (result.isFailure) {
                    Toast.makeText(
                        context,
                        "生成分享图片失败：${result.exceptionOrNull()?.message ?: "未知错误"}",
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun ActivityExportSelector(
    activity: ActivityType,
    onSelected: (ActivityType) -> Unit,
) {
    val items = listOf(ActivityType.SLACK to "摆", ActivityType.GRIND to "卷")
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, PixelCutShape)
            .border(2.dp, MaterialTheme.colorScheme.outline, PixelCutShape)
            .padding(2.dp),
    ) {
        Row(Modifier.fillMaxWidth()) {
            items.forEachIndexed { index, (value, label) ->
                val selected = value == activity
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
                        .clickable { onSelected(value) }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    PixelText(
                        label,
                        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                }
                if (index < items.lastIndex) {
                    Box(
                        Modifier
                            .width(2.dp)
                            .height(38.dp)
                            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)),
                    )
                }
            }
        }
    }
}
