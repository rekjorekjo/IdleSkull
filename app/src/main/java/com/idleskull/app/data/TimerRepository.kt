package com.idleskull.app.data

import android.content.Context
import com.idleskull.app.model.ActiveTimer
import com.idleskull.app.model.ActivityType
import com.idleskull.app.model.EndReason
import com.idleskull.app.model.SkullRules
import com.idleskull.app.model.SkullState
import com.idleskull.app.model.TimeSegment
import com.idleskull.app.model.TimeSession
import com.idleskull.app.model.TimerMode
import com.idleskull.app.model.TimerStatus
import org.json.JSONArray
import org.json.JSONObject

class TimerRepository(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun loadActive(): ActiveTimer? = synchronized(lock) {
        val raw = prefs.getString(KEY_ACTIVE, null) ?: return@synchronized null
        runCatching { decodeActive(JSONObject(raw)) }.getOrNull()
    }

    fun loadSkullState(): SkullState = synchronized(lock) {
        SkullState(
            level = prefs.getInt(KEY_SKULL_LEVEL, 1).coerceAtLeast(1),
            hp = prefs.getLong(KEY_SKULL_HP, SkullRules.maxHp(1)),
        ).let { SkullState(it.level, it.hp.coerceIn(1L, SkullRules.maxHp(it.level))) }
    }

    fun startCountUp(
        activity: ActivityType,
        now: Long = System.currentTimeMillis(),
    ): ActiveTimer = synchronized(lock) {
        val active = ActiveTimer(
            activity = activity,
            mode = TimerMode.COUNT_UP,
            status = TimerStatus.RUNNING,
            startedAt = now,
            anchorAt = now,
            completedSegments = emptyList(),
            skullAtStart = loadSkullStateUnlocked(),
        )
        saveActive(active)
        active
    }

    fun startCountdown(
        activity: ActivityType,
        plannedMs: Long,
        now: Long = System.currentTimeMillis(),
    ): ActiveTimer = synchronized(lock) {
        val active = ActiveTimer(
            activity = activity,
            mode = TimerMode.COUNT_DOWN,
            status = TimerStatus.RUNNING,
            startedAt = now,
            anchorAt = now,
            completedSegments = emptyList(),
            skullAtStart = loadSkullStateUnlocked(),
            plannedMs = plannedMs,
        )
        saveActive(active)
        active
    }

    fun pauseOrResume(now: Long = System.currentTimeMillis()): ActiveTimer? = synchronized(lock) {
        val current = loadActiveUnlocked() ?: return@synchronized null
        if (shouldFinishCountdown(current, now)) {
            finishUnlocked(current, EndReason.COUNTDOWN_FINISHED, now)
            return@synchronized null
        }
        val next = if (current.status == TimerStatus.RUNNING) {
            current.copy(
                status = TimerStatus.PAUSED,
                anchorAt = 0L,
                completedSegments = current.completedSegments + TimeSegment(current.anchorAt, now),
            )
        } else {
            current.copy(
                status = TimerStatus.RUNNING,
                anchorAt = now,
            )
        }
        saveActive(next)
        next
    }

    fun finish(
        reason: EndReason = EndReason.MANUAL,
        now: Long = System.currentTimeMillis(),
    ): TimeSession? = synchronized(lock) {
        val current = loadActiveUnlocked() ?: return@synchronized null
        finishUnlocked(current, reason, now)
    }

    fun ensureCountdownComplete(now: Long = System.currentTimeMillis()): Boolean = synchronized(lock) {
        val current = loadActiveUnlocked() ?: return@synchronized false
        if (shouldFinishCountdown(current, now)) {
            finishUnlocked(current, EndReason.COUNTDOWN_FINISHED, now)
            true
        } else {
            false
        }
    }

    fun loadSessions(): List<TimeSession> = synchronized(lock) {
        loadSessionsUnlocked().sortedByDescending { it.startedAt }
    }

    fun isDarkMode(): Boolean = prefs.getBoolean(KEY_DARK_MODE, false)

    fun setDarkMode(dark: Boolean) {
        prefs.edit().putBoolean(KEY_DARK_MODE, dark).commit()
    }

    fun lastCountdownMs(): Long = prefs.getLong(KEY_LAST_COUNTDOWN_MS, 30 * 60_000L)

    fun setLastCountdownMs(durationMs: Long) {
        prefs.edit().putLong(KEY_LAST_COUNTDOWN_MS, durationMs.coerceAtLeast(1_000L)).commit()
    }

    fun renameSession(id: Long, name: String) = synchronized(lock) {
        val normalized = name.trim().ifBlank { "未命名" }.take(30)
        val sessions = loadSessionsUnlocked().map { session ->
            if (session.id == id) session.copy(name = normalized) else session
        }
        saveSessions(sessions)
    }

    fun clearSessions() = synchronized(lock) {
        prefs.edit().putString(KEY_SESSIONS, "[]").commit()
    }

    fun resetGame() = synchronized(lock) {
        prefs.edit()
            .remove(KEY_ACTIVE)
            .putString(KEY_SESSIONS, "[]")
            .putInt(KEY_SKULL_LEVEL, 1)
            .putLong(KEY_SKULL_HP, SkullRules.maxHp(1))
            .commit()
    }

    private fun shouldFinishCountdown(active: ActiveTimer, now: Long): Boolean =
        active.mode == TimerMode.COUNT_DOWN && active.status == TimerStatus.RUNNING &&
            active.elapsedAt(now) >= (active.plannedMs ?: Long.MAX_VALUE)

    private fun finishUnlocked(active: ActiveTimer, reason: EndReason, now: Long): TimeSession {
        val completedMs = active.completedDurationMs()
        val effectiveEnd = if (
            active.mode == TimerMode.COUNT_DOWN &&
            active.status == TimerStatus.RUNNING &&
            active.plannedMs != null &&
            completedMs + (now - active.anchorAt).coerceAtLeast(0L) >= active.plannedMs
        ) {
            active.anchorAt + (active.plannedMs - completedMs).coerceAtLeast(0L)
        } else now

        val segments = if (active.status == TimerStatus.RUNNING && effectiveEnd > active.anchorAt) {
            active.completedSegments + TimeSegment(active.anchorAt, effectiveEnd)
        } else active.completedSegments

        val durationMs = segments.sumOf { it.durationMs }
        val projection = SkullRules.apply(active.skullAtStart, active.activity, durationMs)
        saveSkullState(projection.state)

        val session = TimeSession(
            id = System.nanoTime(),
            activity = active.activity,
            mode = active.mode,
            startedAt = active.startedAt,
            endedAt = effectiveEnd,
            segments = segments,
            plannedMs = active.plannedMs,
            endReason = if (
                active.mode == TimerMode.COUNT_DOWN &&
                active.plannedMs != null &&
                durationMs >= active.plannedMs
            ) EndReason.COUNTDOWN_FINISHED else reason,
            name = "未命名",
        )
        val sessions = loadSessionsUnlocked().toMutableList().apply { add(0, session) }
        saveSessions(sessions.take(5000))
        prefs.edit().remove(KEY_ACTIVE).commit()
        return session
    }

    private fun loadActiveUnlocked(): ActiveTimer? {
        val raw = prefs.getString(KEY_ACTIVE, null) ?: return null
        return runCatching { decodeActive(JSONObject(raw)) }.getOrNull()
    }

    private fun loadSkullStateUnlocked(): SkullState {
        val level = prefs.getInt(KEY_SKULL_LEVEL, 1).coerceAtLeast(1)
        val max = SkullRules.maxHp(level)
        return SkullState(level, prefs.getLong(KEY_SKULL_HP, max).coerceIn(1L, max))
    }

    private fun loadSessionsUnlocked(): List<TimeSession> {
        val raw = prefs.getString(KEY_SESSIONS, "[]") ?: "[]"
        return runCatching {
            val arr = JSONArray(raw)
            buildList { for (i in 0 until arr.length()) add(decodeSession(arr.getJSONObject(i))) }
        }.getOrDefault(emptyList())
    }

    private fun saveActive(active: ActiveTimer) {
        prefs.edit().putString(KEY_ACTIVE, encodeActive(active).toString()).commit()
    }

    private fun saveSkullState(state: SkullState) {
        prefs.edit()
            .putInt(KEY_SKULL_LEVEL, state.level)
            .putLong(KEY_SKULL_HP, state.hp)
            .commit()
    }

    private fun saveSessions(sessions: List<TimeSession>) {
        val arr = JSONArray()
        sessions.forEach { arr.put(encodeSession(it)) }
        prefs.edit().putString(KEY_SESSIONS, arr.toString()).commit()
    }

    private fun encodeActive(active: ActiveTimer) = JSONObject().apply {
        put("activity", active.activity.name)
        put("mode", active.mode.name)
        put("status", active.status.name)
        put("startedAt", active.startedAt)
        put("anchorAt", active.anchorAt)
        put("plannedMs", active.plannedMs ?: JSONObject.NULL)
        put("segments", encodeSegments(active.completedSegments))
        put("skullLevel", active.skullAtStart.level)
        put("skullHp", active.skullAtStart.hp)
    }

    private fun decodeActive(json: JSONObject) = ActiveTimer(
        activity = ActivityType.valueOf(json.getString("activity")),
        mode = TimerMode.valueOf(json.getString("mode")),
        status = TimerStatus.valueOf(json.getString("status")),
        startedAt = json.getLong("startedAt"),
        anchorAt = json.getLong("anchorAt"),
        completedSegments = decodeSegments(json.getJSONArray("segments")),
        skullAtStart = SkullState(json.getInt("skullLevel"), json.getLong("skullHp")),
        plannedMs = if (json.isNull("plannedMs")) null else json.getLong("plannedMs"),
    )

    private fun encodeSession(session: TimeSession) = JSONObject().apply {
        put("id", session.id)
        put("activity", session.activity.name)
        put("mode", session.mode.name)
        put("startedAt", session.startedAt)
        put("endedAt", session.endedAt)
        put("segments", encodeSegments(session.segments))
        put("plannedMs", session.plannedMs ?: JSONObject.NULL)
        put("endReason", session.endReason.name)
        put("name", session.name)
    }

    private fun decodeSession(json: JSONObject) = TimeSession(
        id = json.getLong("id"),
        activity = ActivityType.valueOf(json.getString("activity")),
        mode = TimerMode.valueOf(json.getString("mode")),
        startedAt = json.getLong("startedAt"),
        endedAt = json.getLong("endedAt"),
        segments = decodeSegments(json.getJSONArray("segments")),
        plannedMs = if (json.isNull("plannedMs")) null else json.getLong("plannedMs"),
        endReason = EndReason.valueOf(json.getString("endReason")),
        name = json.optString("name", "未命名").ifBlank { "未命名" },
    )

    private fun encodeSegments(segments: List<TimeSegment>) = JSONArray().apply {
        segments.forEach { segment ->
            put(JSONArray().apply { put(segment.startAt); put(segment.endAt) })
        }
    }

    private fun decodeSegments(arr: JSONArray) = buildList {
        for (i in 0 until arr.length()) {
            val pair = arr.getJSONArray(i)
            add(TimeSegment(pair.getLong(0), pair.getLong(1)))
        }
    }

    companion object {
        private const val PREFS = "idle_skull_store"
        private const val KEY_ACTIVE = "active_timer"
        private const val KEY_SESSIONS = "sessions"
        private const val KEY_DARK_MODE = "dark_mode"
        private const val KEY_LAST_COUNTDOWN_MS = "last_countdown_ms"
        private const val KEY_SKULL_LEVEL = "skull_level"
        private const val KEY_SKULL_HP = "skull_hp"
        private val lock = Any()
    }
}
