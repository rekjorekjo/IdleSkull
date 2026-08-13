package com.idleskull.app.model

enum class TimerMode { COUNT_UP, COUNT_DOWN }
enum class TimerStatus { RUNNING, PAUSED }
enum class EndReason { MANUAL, COUNTDOWN_FINISHED }

data class TimeSegment(
    val startAt: Long,
    val endAt: Long,
) {
    val durationMs: Long get() = (endAt - startAt).coerceAtLeast(0L)
}

data class ActiveTimer(
    val mode: TimerMode,
    val status: TimerStatus,
    val startedAt: Long,
    val anchorAt: Long,
    val completedSegments: List<TimeSegment>,
    val plannedMs: Long? = null,
) {
    fun completedDurationMs(): Long = completedSegments.sumOf { it.durationMs }

    fun elapsedAt(now: Long): Long {
        val current = if (status == TimerStatus.RUNNING) (now - anchorAt).coerceAtLeast(0L) else 0L
        return completedDurationMs() + current
    }

    fun displayMsAt(now: Long): Long {
        val elapsed = elapsedAt(now)
        return if (mode == TimerMode.COUNT_DOWN) {
            ((plannedMs ?: 0L) - elapsed).coerceAtLeast(0L)
        } else {
            elapsed
        }
    }

    fun severityAt(now: Long): Int {
        val elapsed = elapsedAt(now)
        return when {
            elapsed >= 8 * 60 * 60 * 1000L -> 4
            elapsed >= 4 * 60 * 60 * 1000L -> 3
            elapsed >= 60 * 60 * 1000L -> 2
            elapsed >= 30 * 60 * 1000L -> 1
            else -> 0
        }
    }
}

data class SlackingSession(
    val id: Long,
    val mode: TimerMode,
    val startedAt: Long,
    val endedAt: Long,
    val segments: List<TimeSegment>,
    val plannedMs: Long?,
    val endReason: EndReason,
) {
    val durationMs: Long get() = segments.sumOf { it.durationMs }
}
