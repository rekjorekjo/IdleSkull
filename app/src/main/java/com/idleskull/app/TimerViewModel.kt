package com.idleskull.app

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.idleskull.app.data.TimerRepository
import com.idleskull.app.debug.DebugDataSeeder
import com.idleskull.app.model.ActiveTimer
import com.idleskull.app.model.EndReason
import com.idleskull.app.model.SlackingSession
import com.idleskull.app.widget.TimerWidgetProvider

class TimerViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = TimerRepository(application)

    var active: ActiveTimer? by mutableStateOf(repository.loadActive())
        private set

    var sessions: List<SlackingSession> by mutableStateOf(repository.loadSessions())
        private set

    var darkMode: Boolean by mutableStateOf(repository.isDarkMode())
        private set

    var lastCountdownMs: Long by mutableStateOf(repository.lastCountdownMs())
        private set

    /** Debug builds overlay deterministic sample history for charts without touching local data. */
    val statsSessions: List<SlackingSession>
        get() = DebugDataSeeder.mergeForStats(sessions)

    fun startCountUp() {
        active = repository.startCountUp()
        refreshWidget()
    }

    fun startCountdown(plannedMs: Long) {
        repository.setLastCountdownMs(plannedMs)
        lastCountdownMs = plannedMs
        active = repository.startCountdown(plannedMs)
        refreshWidget()
    }

    fun rememberCountdownDuration(plannedMs: Long) {
        repository.setLastCountdownMs(plannedMs)
        lastCountdownMs = plannedMs
    }

    fun pauseOrResume() {
        active = repository.pauseOrResume()
        if (active == null) sessions = repository.loadSessions()
        refreshWidget()
    }

    fun end() {
        repository.finish(EndReason.MANUAL)
        active = null
        sessions = repository.loadSessions()
        refreshWidget()
    }

    fun tick(now: Long = System.currentTimeMillis()) {
        if (repository.ensureCountdownComplete(now)) {
            active = null
            sessions = repository.loadSessions()
            refreshWidget()
        }
    }

    fun updateDarkMode(value: Boolean) {
        repository.setDarkMode(value)
        darkMode = value
        refreshWidget()
    }

    fun reload() {
        repository.ensureCountdownComplete()
        active = repository.loadActive()
        sessions = repository.loadSessions()
        darkMode = repository.isDarkMode()
        lastCountdownMs = repository.lastCountdownMs()
        refreshWidget()
    }

    fun renameSession(id: Long, name: String) {
        repository.renameSession(id, name)
        sessions = repository.loadSessions()
    }

    fun clearSessions() {
        repository.clearSessions()
        sessions = emptyList()
    }

    private fun refreshWidget() {
        TimerWidgetProvider.refreshAll(getApplication())
    }
}
