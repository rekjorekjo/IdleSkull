package com.idleskull.app.ui

import com.idleskull.app.model.ActivityType
import com.idleskull.app.model.TimeSession
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

object StatsEngine {
    fun dailyTotals(
        sessions: List<TimeSession>,
        activity: ActivityType? = null,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Map<LocalDate, Long> {
        val out = mutableMapOf<LocalDate, Long>()
        sessions.asSequence()
            .filter { activity == null || it.activity == activity }
            .forEach { session ->
                session.segments.forEach { segment ->
                    var cursor = segment.startAt
                    while (cursor < segment.endAt) {
                        val day = Instant.ofEpochMilli(cursor).atZone(zone).toLocalDate()
                        val nextDay = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
                        val end = minOf(segment.endAt, nextDay)
                        out[day] = (out[day] ?: 0L) + (end - cursor).coerceAtLeast(0L)
                        cursor = end
                    }
                }
            }
        return out
    }

    fun today(sessions: List<TimeSession>, activity: ActivityType): Long =
        dailyTotals(sessions, activity)[LocalDate.now()] ?: 0L

    fun weekDays(today: LocalDate = LocalDate.now()): List<LocalDate> {
        val monday = today.with(java.time.DayOfWeek.MONDAY)
        return (0..6).map { monday.plusDays(it.toLong()) }
    }

    fun monthDays(month: YearMonth = YearMonth.now()): List<LocalDate> =
        (1..month.lengthOfMonth()).map(month::atDay)

    fun yearMonths(year: Int = LocalDate.now().year): List<YearMonth> =
        (1..12).map { YearMonth.of(year, it) }

    fun sessionsOnDay(
        sessions: List<TimeSession>,
        day: LocalDate,
        activity: ActivityType? = null,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<TimeSession> {
        val start = day.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return sessions.filter { session ->
            (activity == null || session.activity == activity) &&
                session.segments.any { seg -> seg.startAt < end && seg.endAt > start }
        }
    }
}
