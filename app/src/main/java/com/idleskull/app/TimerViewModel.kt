package com.idleskull.app

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.idleskull.app.data.TimerRepository
import com.idleskull.app.debug.DebugDataSeeder
import com.idleskull.app.model.ActiveTimer
import com.idleskull.app.model.ActivityType
import com.idleskull.app.model.EndReason
import com.idleskull.app.model.SkullProjection
import com.idleskull.app.model.SkullRules
import com.idleskull.app.model.SkullState
import com.idleskull.app.model.TimeSession
import com.idleskull.app.model.TimerMode
import com.idleskull.app.model.TimerStatus
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

    var defeatAnimationSerial: Long by mutableLongStateOf(0L)
        private set

    var defeatAnimationFromLevel: Int by mutableIntStateOf(1)
        private set

    var defeatAnimationToLevel: Int by mutableIntStateOf(2)
        private set

    var defeatAnimationNextMaxHp: Long by mutableLongStateOf(SkullRules.maxHp(2))
        private set

    private var observedTimerStartedAt: Long? = active?.startedAt
    private var observedDefeatedCount: Int = active
        ?.takeIf {
            it.activity == ActivityType.GRIND &&
                it.elapsedAt(System.currentTimeMillis()) >= SkullRules.MIN_VALID_SESSION_MS
        }
        ?.projectedSkullAt(System.currentTimeMillis())
        ?.defeated
        ?: 0

    val statsSessions: List<TimeSession>
        get() = DebugDataSeeder.mergeForStats(sessions)

    fun projectedSkull(now: Long = System.currentTimeMillis()): SkullProjection =
        active?.projectedSkullAt(now) ?: SkullProjection(skullState, 0)

    fun startCountUp(activity: ActivityType) {
        active = repository.startCountUp(activity)
        bindDefeatObserver(active, includeCurrent = false)
        refreshWidget()
    }

    fun startCountdown(activity: ActivityType, plannedMs: Long) {
        repository.setLastCountdownMs(plannedMs)
        lastCountdownMs = plannedMs
        active = repository.startCountdown(activity, plannedMs)
        bindDefeatObserver(active, includeCurrent = false)
        refreshWidget()
    }

    fun rememberCountdownDuration(plannedMs: Long) {
        repository.setLastCountdownMs(plannedMs)
        lastCountdownMs = plannedMs
    }

    fun pauseOrResume() {
        observeDefeat()
        active = repository.pauseOrResume()
        if (active == null) reloadStateOnly()
        refreshWidget()
    }

    fun end() {
        observeDefeat()
        repository.finish(EndReason.MANUAL)
        active = null
        reloadStateOnly()
        refreshWidget()
    }

    fun tick(now: Long = System.currentTimeMillis()) {
        observeDefeat(now)
        val current = active ?: return
        val countdownFinished =
            current.mode == TimerMode.COUNT_DOWN &&
                current.status == TimerStatus.RUNNING &&
                current.plannedMs != null &&
                current.elapsedAt(now) >= current.plannedMs
        if (countdownFinished) {
            repository.finish(EndReason.COUNTDOWN_FINISHED, now)
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
        val now = System.currentTimeMillis()
        observeDefeat(now)
        repository.ensureCountdownComplete(now)
        val reloaded = repository.loadActive()
        if (reloaded?.startedAt != observedTimerStartedAt) {
            bindDefeatObserver(reloaded, includeCurrent = true, now = now)
        } else if (reloaded == null) {
            observedTimerStartedAt = null
            observedDefeatedCount = 0
        }
        active = reloaded
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
        observedTimerStartedAt = null
        observedDefeatedCount = 0
        refreshWidget()
    }

    private fun observeDefeat(now: Long = System.currentTimeMillis()) {
        val current = active ?: return
        if (current.startedAt != observedTimerStartedAt) {
            bindDefeatObserver(current, includeCurrent = false, now = now)
        }
        if (current.activity != ActivityType.GRIND) return
        if (current.elapsedAt(now) < SkullRules.MIN_VALID_SESSION_MS) return

        val projection = current.projectedSkullAt(now)
        if (projection.defeated > observedDefeatedCount) {
            val defeatedLevel = current.skullAtStart.level + projection.defeated - 1
            val nextLevel = defeatedLevel + 1
            defeatAnimationFromLevel = defeatedLevel
            defeatAnimationToLevel = nextLevel
            defeatAnimationNextMaxHp = SkullRules.maxHp(nextLevel)
            observedDefeatedCount = projection.defeated
            defeatAnimationSerial += 1L
        }
    }

    private fun bindDefeatObserver(
        timer: ActiveTimer?,
        includeCurrent: Boolean,
        now: Long = System.currentTimeMillis(),
    ) {
        observedTimerStartedAt = timer?.startedAt
        observedDefeatedCount = if (
            includeCurrent &&
            timer?.activity == ActivityType.GRIND &&
            timer.elapsedAt(now) >= SkullRules.MIN_VALID_SESSION_MS
        ) {
            timer.projectedSkullAt(now).defeated
        } else {
            0
        }
    }

    private fun reloadStateOnly() {
        sessions = repository.loadSessions()
        skullState = repository.loadSkullState()
    }

    private fun refreshWidget() {
        TimerWidgetProvider.refreshAll(getApplication())
    }
}
