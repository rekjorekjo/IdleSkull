package com.idleskull.app.model

enum class TimerMode { COUNT_UP, COUNT_DOWN }
enum class TimerStatus { RUNNING, PAUSED }
enum class ActivityType { SLACK, GRIND }
enum class EndReason { MANUAL, COUNTDOWN_FINISHED }

data class TimeSegment(
    val startAt: Long,
    val endAt: Long,
) {
    val durationMs: Long get() = (endAt - startAt).coerceAtLeast(0L)
}

data class SkullState(
    val level: Int = 1,
    val hp: Long = SkullRules.maxHp(1),
) {
    val maxHp: Long get() = SkullRules.maxHp(level)
    val hpRatio: Float get() = if (maxHp <= 0L) 0f else (hp.toFloat() / maxHp.toFloat()).coerceIn(0f, 1f)
}

data class SkullProjection(
    val state: SkullState,
    val defeated: Int,
)

object SkullRules {
    private const val BASE_HP = 1_800L
    private const val HP_STEP = 450L

    fun maxHp(level: Int): Long = BASE_HP + (level.coerceAtLeast(1) - 1L) * HP_STEP

    fun apply(
        start: SkullState,
        activity: ActivityType,
        durationMs: Long,
    ): SkullProjection {
        val seconds = durationMs.coerceAtLeast(0L) / 1_000L
        if (seconds <= 0L) return SkullProjection(start.normalized(), 0)

        var state = start.normalized()
        return when (activity) {
            ActivityType.SLACK -> {
                state = state.copy(hp = (state.hp + seconds).coerceAtMost(state.maxHp))
                SkullProjection(state, 0)
            }
            ActivityType.GRIND -> {
                var damage = seconds
                var defeated = 0
                while (damage >= state.hp) {
                    damage -= state.hp
                    val nextLevel = state.level + 1
                    state = SkullState(level = nextLevel, hp = maxHp(nextLevel))
                    defeated += 1
                    if (damage == 0L) break
                }
                if (damage > 0L) {
                    state = state.copy(hp = (state.hp - damage).coerceAtLeast(1L))
                }
                SkullProjection(state, defeated)
            }
        }
    }

    private fun SkullState.normalized(): SkullState {
        val safeLevel = level.coerceAtLeast(1)
        val max = maxHp(safeLevel)
        return SkullState(safeLevel, hp.coerceIn(1L, max))
    }
}

data class ActiveTimer(
    val activity: ActivityType,
    val mode: TimerMode,
    val status: TimerStatus,
    val startedAt: Long,
    val anchorAt: Long,
    val completedSegments: List<TimeSegment>,
    val skullAtStart: SkullState,
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

    fun projectedSkullAt(now: Long): SkullProjection =
        SkullRules.apply(skullAtStart, activity, elapsedAt(now))
}

data class TimeSession(
    val id: Long,
    val activity: ActivityType,
    val mode: TimerMode,
    val startedAt: Long,
    val endedAt: Long,
    val segments: List<TimeSegment>,
    val plannedMs: Long?,
    val endReason: EndReason,
    val name: String = "未命名",
) {
    val durationMs: Long get() = segments.sumOf { it.durationMs }
}
