package com.nendo.argosy.ui.components.recap

import com.nendo.argosy.ui.theme.generated.ComponentDefaults

/**
 * One unlock tile at one moment of its reveal. [borderFraction] is how much of the border has been
 * drawn clockwise from the top, [glow] the glow strength from 0 to 1.
 */
data class UnlockTileFrame(
    val borderFraction: Float,
    val scale: Float,
    val glow: Float,
    val badgeAlpha: Float
) {
    companion object {
        val HIDDEN = UnlockTileFrame(borderFraction = 0f, scale = 1f, glow = 0f, badgeAlpha = 0f)
        val SETTLED = UnlockTileFrame(borderFraction = 1f, scale = 1f, glow = 0f, badgeAlpha = 1f)
    }
}

/**
 * The pacing of the unlock row's sequential reveal. Each tile owns a [perTileMs] slot: the row
 * scrolls to it at the slot start, the tile starts animating [scrollLeadMs] later, draws its border
 * over [borderDrawMs], pops to [popScale] with a glow over [popMs], then settles to full size over
 * [settleMs] while the badge fades in and the glow fades out.
 */
data class UnlockRevealTimeline(
    val scrollLeadMs: Long,
    val borderDrawMs: Long,
    val popMs: Long,
    val settleMs: Long,
    val perTileMs: Long,
    val popScale: Float
) {
    val tileAnimationMs: Long get() = borderDrawMs + popMs + settleMs

    fun slotStartMs(index: Int): Long = index * perTileMs

    fun tileStartMs(index: Int): Long = slotStartMs(index) + scrollLeadMs

    fun revealEndMs(count: Int): Long =
        if (count <= 0) 0L else tileStartMs(count - 1) + tileAnimationMs

    fun slotsStarted(elapsedMs: Long, count: Int): Int {
        if (elapsedMs < 0 || count <= 0) return 0
        return (elapsedMs / perTileMs + 1).coerceAtMost(count.toLong()).toInt()
    }

    /**
     * The tile the reveal is on at [elapsedMs]: the latest one whose slot has started, held on the
     * last tile once every slot has run.
     */
    fun currentSlot(elapsedMs: Long, count: Int): Int = (slotsStarted(elapsedMs, count) - 1).coerceAtLeast(0)

    fun frameOf(index: Int, elapsedMs: Long): UnlockTileFrame = frameAt(elapsedMs - tileStartMs(index))

    fun frameAt(tileElapsedMs: Long): UnlockTileFrame {
        if (tileElapsedMs <= 0L) return UnlockTileFrame.HIDDEN
        if (tileElapsedMs < borderDrawMs) {
            return UnlockTileFrame.HIDDEN.copy(borderFraction = easeOut(tileElapsedMs, borderDrawMs))
        }
        val afterBorder = tileElapsedMs - borderDrawMs
        if (afterBorder < popMs) {
            val pop = easeOut(afterBorder, popMs)
            return UnlockTileFrame(
                borderFraction = 1f,
                scale = 1f + (popScale - 1f) * pop,
                glow = pop,
                badgeAlpha = 0f
            )
        }
        val afterPop = afterBorder - popMs
        if (afterPop < settleMs) {
            val settle = easeOut(afterPop, settleMs)
            return UnlockTileFrame(
                borderFraction = 1f,
                scale = popScale - (popScale - 1f) * settle,
                glow = 1f - settle,
                badgeAlpha = settle
            )
        }
        return UnlockTileFrame.SETTLED
    }

    private fun easeOut(elapsed: Long, duration: Long): Float {
        if (duration <= 0L) return 1f
        val t = (elapsed.toFloat() / duration).coerceIn(0f, 1f)
        return 1f - (1f - t) * (1f - t)
    }

    companion object {
        val Default = UnlockRevealTimeline(
            scrollLeadMs = ComponentDefaults.UnlockRecap.scrollLeadMs.toLong(),
            borderDrawMs = ComponentDefaults.UnlockRecap.borderDrawMs.toLong(),
            popMs = ComponentDefaults.UnlockRecap.popMs.toLong(),
            settleMs = ComponentDefaults.UnlockRecap.settleMs.toLong(),
            perTileMs = ComponentDefaults.UnlockRecap.perTileMs.toLong(),
            popScale = ComponentDefaults.UnlockRecap.popScale
        )
    }
}
