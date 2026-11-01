package com.nendo.argosy.ui.components.recap

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UnlockRevealTimelineTest {

    private val timeline = UnlockRevealTimeline(
        scrollLeadMs = 250,
        borderDrawMs = 450,
        popMs = 120,
        settleMs = 180,
        perTileMs = 1100,
        popScale = 1.1f
    )

    @Test
    fun `a tile is hidden until its animation starts`() {
        assertEquals(UnlockTileFrame.HIDDEN, timeline.frameOf(0, 0L))
        assertEquals(UnlockTileFrame.HIDDEN, timeline.frameOf(0, 250L))
        assertEquals(UnlockTileFrame.HIDDEN, timeline.frameOf(1, 1100L + 250L))
    }

    @Test
    fun `the border draws first with no pop, glow or badge`() {
        val midBorder = timeline.frameAt(225L)
        assertTrue(midBorder.borderFraction > 0f && midBorder.borderFraction < 1f)
        assertEquals(1f, midBorder.scale, 0f)
        assertEquals(0f, midBorder.glow, 0f)
        assertEquals(0f, midBorder.badgeAlpha, 0f)
    }

    @Test
    fun `the border fraction only grows while it draws`() {
        val fractions = (0L..450L step 25L).map { timeline.frameAt(it).borderFraction }
        assertEquals(fractions.sorted(), fractions)
    }

    @Test
    fun `the pop peaks at the pop scale with full glow and no badge`() {
        val peak = timeline.frameAt(450L + 120L)
        assertEquals(1f, peak.borderFraction, 0f)
        assertEquals(1.1f, peak.scale, 0.0001f)
        assertEquals(1f, peak.glow, 0.0001f)
        assertEquals(0f, peak.badgeAlpha, 0.0001f)
    }

    @Test
    fun `the settle shrinks the tile while the badge fades in and the glow fades out`() {
        val mid = timeline.frameAt(450L + 120L + 90L)
        assertTrue(mid.scale > 1f && mid.scale < 1.1f)
        assertTrue(mid.glow > 0f && mid.glow < 1f)
        assertTrue(mid.badgeAlpha > 0f && mid.badgeAlpha < 1f)
    }

    @Test
    fun `a tile is settled once its animation has run`() {
        assertEquals(UnlockTileFrame.SETTLED, timeline.frameAt(750L))
        assertEquals(UnlockTileFrame.SETTLED, timeline.frameOf(0, 1100L))
    }

    @Test
    fun `tiles reveal one slot apart`() {
        assertEquals(0L, timeline.slotStartMs(0))
        assertEquals(1100L, timeline.slotStartMs(1))
        assertEquals(250L + 2200L, timeline.tileStartMs(2))
    }

    @Test
    fun `the row scrolls to each tile as its slot starts and never past the last`() {
        assertEquals(0, timeline.slotsStarted(-1L, 3))
        assertEquals(1, timeline.slotsStarted(0L, 3))
        assertEquals(1, timeline.slotsStarted(1099L, 3))
        assertEquals(2, timeline.slotsStarted(1100L, 3))
        assertEquals(3, timeline.slotsStarted(60_000L, 3))
        assertEquals(0, timeline.slotsStarted(500L, 0))
    }

    @Test
    fun `the reveal ends when the last tile has settled`() {
        assertEquals(0L, timeline.revealEndMs(0))
        assertEquals(250L + 750L, timeline.revealEndMs(1))
        assertEquals(2200L + 250L + 750L, timeline.revealEndMs(3))
        assertEquals(UnlockTileFrame.SETTLED, timeline.frameOf(2, timeline.revealEndMs(3)))
    }

    @Test
    fun `a slot is about 1_1 seconds and holds after the animation`() {
        assertTrue(timeline.scrollLeadMs + timeline.tileAnimationMs < timeline.perTileMs)
    }

    @Test
    fun `the current slot is the latest one started`() {
        assertEquals(0, timeline.currentSlot(0L, 3))
        assertEquals(0, timeline.currentSlot(1099L, 3))
        assertEquals(1, timeline.currentSlot(1100L, 3))
        assertEquals(2, timeline.currentSlot(2200L, 3))
    }

    @Test
    fun `the current slot holds on the last tile once every slot has run`() {
        assertEquals(2, timeline.currentSlot(60_000L, 3))
    }

    @Test
    fun `the current slot is the first before the reveal starts or with no tiles`() {
        assertEquals(0, timeline.currentSlot(-1L, 3))
        assertEquals(0, timeline.currentSlot(500L, 0))
    }

    @Test
    fun `the default pacing gives each unlock about two and a half seconds`() {
        assertEquals(2500L, UnlockRevealTimeline.Default.perTileMs)
    }

    @Test
    fun `the default pacing holds each settled unlock for over a second and a half before the next`() {
        val default = UnlockRevealTimeline.Default
        val hold = default.perTileMs - default.scrollLeadMs - default.tileAnimationMs
        assertTrue("hold was $hold ms", hold >= 1500L)
    }
}
