package com.nendo.argosy.util

/**
 * Notices when Arcoon keeps doing the same thing over and over within seconds, the sign that
 * something else keeps undoing it: typically a second copy of Arcoon (a debug build installed next
 * to a release) fighting over the lower screen. Each copy took the screen or the controller back
 * from the other about once a second, flipping focus, resuming screens and buzzing endlessly.
 * After [limit] times inside [windowMs], [allow] answers false for [backoffMs].
 */
class TugOfWarGuard(
    private val what: String,
    private val limit: Int = 4,
    private val windowMs: Long = 10_000L,
    private val backoffMs: Long = 60_000L,
) {
    private val times = ArrayDeque<Long>()
    private var backoffUntil = 0L

    @Synchronized
    fun allow(): Boolean {
        val now = android.os.SystemClock.uptimeMillis()
        if (now < backoffUntil) return false
        while (times.isNotEmpty() && now - times.first() > windowMs) times.removeFirst()
        if (times.size >= limit) {
            times.clear()
            backoffUntil = now + backoffMs
            ArcoonTrace.log("$what keeps being undone by another app (another Arcoon copy?): pausing it for ${backoffMs / 1000} s")
            return false
        }
        times.addLast(now)
        return true
    }
}
