package com.idleskull.app.debug

import com.idleskull.app.model.ActivityType
import com.idleskull.app.model.EndReason
import com.idleskull.app.model.TimeSegment
import com.idleskull.app.model.TimeSession
import com.idleskull.app.model.TimerMode
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.absoluteValue

/** Debug-only in-memory sample history. Nothing is persisted. */
object DebugDataSeeder {
    private val samples: List<TimeSession> by lazy { buildSamples() }

    fun mergeForStats(realSessions: List<TimeSession>): List<TimeSession> =
        (realSessions + samples).sortedByDescending { it.startedAt }

    private fun buildSamples(): List<TimeSession> {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now()
        return buildList {
            var id = -9_000_000L
            for (offset in 0..210) {
                val day = today.minusDays(offset.toLong())
                val fingerprint = (day.toEpochDay() * 1103515245L + 12345L).absoluteValue
                val count = when ((fingerprint % 7L).toInt()) {
                    0 -> 0
                    1, 2, 3 -> 1
                    4, 5 -> 2
                    else -> 3
                }
                repeat(count) { index ->
                    val startHour = 9 + ((fingerprint / (index + 1L) + index * 5L) % 12L).toInt()
                    val startMinute = ((fingerprint / 13L + index * 17L) % 55L).toInt()
                    val durationMinutes = 12L + ((fingerprint / 29L + index * 41L) % 150L)
                    val start = day.atTime(LocalTime.of(startHour.coerceAtMost(23), startMinute))
                        .atZone(zone).toInstant().toEpochMilli()
                    val end = start + durationMinutes * 60_000L
                    val mode = if ((fingerprint + index) % 4L == 0L) TimerMode.COUNT_DOWN else TimerMode.COUNT_UP
                    val activity = if ((fingerprint + index * 3L) % 5L < 2L) ActivityType.GRIND else ActivityType.SLACK
                    val slackNames = listOf("未命名", "午后发呆", "刷手机", "临时摸鱼", "躺一会儿")
                    val grindNames = listOf("高数", "写代码", "背单词", "复习", "专注一下")
                    val names = if (activity == ActivityType.SLACK) slackNames else grindNames
                    add(
                        TimeSession(
                            id = id--,
                            activity = activity,
                            mode = mode,
                            startedAt = start,
                            endedAt = end,
                            segments = listOf(TimeSegment(start, end)),
                            plannedMs = if (mode == TimerMode.COUNT_DOWN) durationMinutes * 60_000L else null,
                            endReason = if (mode == TimerMode.COUNT_DOWN) EndReason.COUNTDOWN_FINISHED else EndReason.MANUAL,
                            name = names[((fingerprint + index) % names.size).toInt()],
                        ),
                    )
                }
            }
        }
    }
}
