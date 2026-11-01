package com.nendo.argosy.data.emulator

import java.time.Instant
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

data class CapturedRichPresence(
    val raGameId: Long,
    val message: String,
    val at: Instant
)

/**
 * The last rich-presence line Argosy itself sent to RetroAchievements during the running session.
 * Holds one value per session: [clear] at session start, [take] at session end.
 */
@Singleton
class RichPresenceCapture @Inject constructor() {
    private val latest = AtomicReference<CapturedRichPresence?>(null)

    fun record(raGameId: Long, message: String, at: Instant) {
        if (message.isBlank()) return
        latest.set(CapturedRichPresence(raGameId, message.trim(), at))
    }

    fun clear() {
        latest.set(null)
    }

    /**
     * Returns and forgets the captured line when it was recorded inside [sessionStart, sessionEnd];
     * a line from outside that window belongs to another session and is dropped.
     */
    fun take(sessionStart: Instant, sessionEnd: Instant): CapturedRichPresence? {
        val captured = latest.getAndSet(null) ?: return null
        return captured.takeIf { !it.at.isBefore(sessionStart) && !it.at.isAfter(sessionEnd) }
    }
}
