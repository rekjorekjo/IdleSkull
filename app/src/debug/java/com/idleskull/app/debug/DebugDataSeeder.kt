package com.idleskull.app.debug

import com.idleskull.app.model.EndReason
import com.idleskull.app.model.SlackingSession
import com.idleskull.app.model.TimeSegment
import com.idleskull.app.model.TimerMode
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.absoluteValue

/**
 * In-memory deterministic sample history used only by debug builds.
 *
 * It is deliberately NOT written into TimerRepository. This means existing local
 * sessions can coexist with the sample history, while release builds never see it.
 */
object DebugDataSeeder {
    private val samples: List<SlackingSession> by lazy { buildSamples() }

    fun mergeForStats(realSessions: List<SlackingSession>): List<SlackingSession> =
        (realSessions + samples).sortedByDescending { it.startedAt }

    private fun buildSamples(): List<SlackingSession> {
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
                    val names = listOf("未命名", "午后发呆", "刷手机", "临时摸鱼", "躺一会儿")
                    add(
                        SlackingSession(
                            id = id--,
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
