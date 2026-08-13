package com.idleskull.app.data

import android.content.Context
import com.idleskull.app.model.ActiveTimer
import com.idleskull.app.model.EndReason
import com.idleskull.app.model.SlackingSession
import com.idleskull.app.model.TimeSegment
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

    fun startCountUp(now: Long = System.currentTimeMillis()): ActiveTimer = synchronized(lock) {
        val active = ActiveTimer(
            mode = TimerMode.COUNT_UP,
            status = TimerStatus.RUNNING,
            startedAt = now,
            anchorAt = now,
            completedSegments = emptyList(),
        )
        saveActive(active)
        active
    }

    fun startCountdown(plannedMs: Long, now: Long = System.currentTimeMillis()): ActiveTimer = synchronized(lock) {
        val active = ActiveTimer(
            mode = TimerMode.COUNT_DOWN,
            status = TimerStatus.RUNNING,
            startedAt = now,
            anchorAt = now,
            completedSegments = emptyList(),
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

    fun finish(reason: EndReason = EndReason.MANUAL, now: Long = System.currentTimeMillis()): SlackingSession? = synchronized(lock) {
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

    fun loadSessions(): List<SlackingSession> = synchronized(lock) {
        val raw = prefs.getString(KEY_SESSIONS, "[]") ?: "[]"
        runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) add(decodeSession(arr.getJSONObject(i)))
            }.sortedByDescending { it.startedAt }
        }.getOrDefault(emptyList())
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

    private fun shouldFinishCountdown(active: ActiveTimer, now: Long): Boolean =
        active.mode == TimerMode.COUNT_DOWN && active.status == TimerStatus.RUNNING &&
            active.elapsedAt(now) >= (active.plannedMs ?: Long.MAX_VALUE)

    private fun finishUnlocked(active: ActiveTimer, reason: EndReason, now: Long): SlackingSession {
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

        val session = SlackingSession(
            id = System.nanoTime(),
            mode = active.mode,
            startedAt = active.startedAt,
            endedAt = effectiveEnd,
            segments = segments,
            plannedMs = active.plannedMs,
            endReason = if (active.mode == TimerMode.COUNT_DOWN && active.plannedMs != null && segments.sumOf { it.durationMs } >= active.plannedMs) {
                EndReason.COUNTDOWN_FINISHED
            } else reason,
            name = "未命名",
        )
        val sessions = loadSessionsUnlocked().toMutableList().apply { add(0, session) }
        saveSessions(sessions.take(3000))
        prefs.edit().remove(KEY_ACTIVE).commit()
        return session
    }

    private fun loadActiveUnlocked(): ActiveTimer? {
        val raw = prefs.getString(KEY_ACTIVE, null) ?: return null
        return runCatching { decodeActive(JSONObject(raw)) }.getOrNull()
    }

    private fun loadSessionsUnlocked(): List<SlackingSession> {
        val raw = prefs.getString(KEY_SESSIONS, "[]") ?: "[]"
        return runCatching {
            val arr = JSONArray(raw)
            buildList { for (i in 0 until arr.length()) add(decodeSession(arr.getJSONObject(i))) }
        }.getOrDefault(emptyList())
    }

    private fun saveActive(active: ActiveTimer) {
        prefs.edit().putString(KEY_ACTIVE, encodeActive(active).toString()).commit()
    }

    private fun saveSessions(sessions: List<SlackingSession>) {
        val arr = JSONArray()
        sessions.forEach { arr.put(encodeSession(it)) }
        prefs.edit().putString(KEY_SESSIONS, arr.toString()).commit()
    }

    private fun encodeActive(active: ActiveTimer) = JSONObject().apply {
        put("mode", active.mode.name)
        put("status", active.status.name)
        put("startedAt", active.startedAt)
        put("anchorAt", active.anchorAt)
        put("plannedMs", active.plannedMs ?: JSONObject.NULL)
        put("segments", encodeSegments(active.completedSegments))
    }

    private fun decodeActive(json: JSONObject) = ActiveTimer(
        mode = TimerMode.valueOf(json.getString("mode")),
        status = TimerStatus.valueOf(json.getString("status")),
        startedAt = json.getLong("startedAt"),
        anchorAt = json.getLong("anchorAt"),
        completedSegments = decodeSegments(json.getJSONArray("segments")),
        plannedMs = if (json.isNull("plannedMs")) null else json.getLong("plannedMs"),
    )

    private fun encodeSession(session: SlackingSession) = JSONObject().apply {
        put("id", session.id)
        put("mode", session.mode.name)
        put("startedAt", session.startedAt)
        put("endedAt", session.endedAt)
        put("segments", encodeSegments(session.segments))
        put("plannedMs", session.plannedMs ?: JSONObject.NULL)
        put("endReason", session.endReason.name)
        put("name", session.name)
    }

    private fun decodeSession(json: JSONObject) = SlackingSession(
        id = json.getLong("id"),
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
        private val lock = Any()
    }
}
