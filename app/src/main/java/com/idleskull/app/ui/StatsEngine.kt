package com.idleskull.app.ui

import com.idleskull.app.model.SlackingSession
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

object StatsEngine {
    fun dailyTotals(sessions: List<SlackingSession>, zone: ZoneId = ZoneId.systemDefault()): Map<LocalDate, Long> {
        val out = mutableMapOf<LocalDate, Long>()
        sessions.forEach { session ->
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

    fun today(sessions: List<SlackingSession>): Long = dailyTotals(sessions)[LocalDate.now()] ?: 0L

    fun weekDays(today: LocalDate = LocalDate.now()): List<LocalDate> {
        val monday = today.with(java.time.DayOfWeek.MONDAY)
        return (0..6).map { monday.plusDays(it.toLong()) }
    }

    fun monthDays(month: YearMonth = YearMonth.now()): List<LocalDate> =
        (1..month.lengthOfMonth()).map(month::atDay)

    fun yearMonths(year: Int = LocalDate.now().year): List<YearMonth> =
        (1..12).map { YearMonth.of(year, it) }

    fun sessionsOnDay(sessions: List<SlackingSession>, day: LocalDate, zone: ZoneId = ZoneId.systemDefault()): List<SlackingSession> {
        val start = day.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return sessions.filter { it.segments.any { seg -> seg.startAt < end && seg.endAt > start } }
    }
}
