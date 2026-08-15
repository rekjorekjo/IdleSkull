package com.idleskull.app

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.idleskull.app.data.TimerRepository
import com.idleskull.app.debug.DebugDataSeeder
import com.idleskull.app.model.ActiveTimer
import com.idleskull.app.model.ActivityType
import com.idleskull.app.model.EndReason
import com.idleskull.app.model.SkullProjection
import com.idleskull.app.model.SkullState
import com.idleskull.app.model.TimeSession
import com.idleskull.app.widget.TimerWidgetProvider

class TimerViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = TimerRepository(application)

    var active: ActiveTimer? by mutableStateOf(repository.loadActive())
        private set

    var sessions: List<TimeSession> by mutableStateOf(repository.loadSessions())
        private set

    var skullState: SkullState by mutableStateOf(repository.loadSkullState())
        private set

    var darkMode: Boolean by mutableStateOf(repository.isDarkMode())
        private set

    var lastCountdownMs: Long by mutableStateOf(repository.lastCountdownMs())
        private set

    val statsSessions: List<TimeSession>
        get() = DebugDataSeeder.mergeForStats(sessions)

    fun projectedSkull(now: Long = System.currentTimeMillis()): SkullProjection =
        active?.projectedSkullAt(now) ?: SkullProjection(skullState, 0)

    fun startCountUp(activity: ActivityType) {
        active = repository.startCountUp(activity)
        refreshWidget()
    }

    fun startCountdown(activity: ActivityType, plannedMs: Long) {
        repository.setLastCountdownMs(plannedMs)
        lastCountdownMs = plannedMs
        active = repository.startCountdown(activity, plannedMs)
        refreshWidget()
    }

    fun rememberCountdownDuration(plannedMs: Long) {
        repository.setLastCountdownMs(plannedMs)
        lastCountdownMs = plannedMs
    }

    fun pauseOrResume() {
        active = repository.pauseOrResume()
        if (active == null) reloadStateOnly()
        refreshWidget()
    }

    fun end() {
        repository.finish(EndReason.MANUAL)
        active = null
        reloadStateOnly()
        refreshWidget()
    }

    fun tick(now: Long = System.currentTimeMillis()) {
        if (repository.ensureCountdownComplete(now)) {
            active = null
            reloadStateOnly()
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
        reloadStateOnly()
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

    fun resetGame() {
        repository.resetGame()
        active = null
        sessions = emptyList()
        skullState = repository.loadSkullState()
        refreshWidget()
    }

    private fun reloadStateOnly() {
        sessions = repository.loadSessions()
        skullState = repository.loadSkullState()
    }

    private fun refreshWidget() {
        TimerWidgetProvider.refreshAll(getApplication())
    }
}
