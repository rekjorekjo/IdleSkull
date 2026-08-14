package com.idleskull.app.debug

import com.idleskull.app.model.SlackingSession

/** Release APKs never expose synthetic history. */
object DebugDataSeeder {
    fun mergeForStats(realSessions: List<SlackingSession>): List<SlackingSession> = realSessions
}
