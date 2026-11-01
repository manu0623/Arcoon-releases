package com.nendo.argosy.ui.components.recap

import com.nendo.argosy.domain.model.SessionUnlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class RecapCursorTest {

    private fun unlock(id: String, gameTitle: String? = null) = SessionUnlock(
        badgeId = id,
        title = "Title $id",
        points = 5,
        badgePath = "/badge/$id.png",
        isHardcore = true,
        unlockedAt = Instant.parse("2026-10-01T10:00:00Z"),
        description = "Description $id",
        type = null,
        gameTitle = gameTitle
    )

    private val unlocks = listOf(unlock("a"), unlock("b"), unlock("c"))

    @Test
    fun `a centred tile becomes the current one`() {
        assertEquals(2, RecapCursor().centered(2, 3).currentIndex)
    }

    @Test
    fun `a centred index outside the row is clamped to it`() {
        assertEquals(2, RecapCursor().centered(9, 3).currentIndex)
        assertEquals(0, RecapCursor().centered(-4, 3).currentIndex)
    }

    @Test
    fun `centring never counts as a scrub`() {
        assertEquals(0, RecapCursor().centered(1, 3).scrubs)
    }

    @Test
    fun `an empty row leaves the cursor alone`() {
        val cursor = RecapCursor(currentIndex = 1, scrubs = 2)
        assertEquals(cursor, cursor.centered(0, 0))
        assertEquals(cursor, cursor.scrubbed(1, 0))
    }

    @Test
    fun `a scrub moves from the current tile in either direction`() {
        val start = RecapCursor(currentIndex = 1)
        assertEquals(2, start.scrubbed(1, 3).currentIndex)
        assertEquals(0, start.scrubbed(-1, 3).currentIndex)
    }

    @Test
    fun `a scrub stops at either end of the row`() {
        assertEquals(2, RecapCursor(currentIndex = 2).scrubbed(1, 3).currentIndex)
        assertEquals(0, RecapCursor(currentIndex = 0).scrubbed(-1, 3).currentIndex)
    }

    @Test
    fun `every scrub is counted, including one held at an end`() {
        val once = RecapCursor(currentIndex = 2).scrubbed(1, 3)
        val twice = once.scrubbed(1, 3)
        assertEquals(1, once.scrubs)
        assertEquals(2, twice.scrubs)
    }

    @Test
    fun `a scrub after a settled touch scroll starts from where the touch left the row`() {
        val scrubbed = RecapCursor().scrubbed(1, 3)
        val touched = scrubbed.centered(0, 3)
        assertEquals(1, touched.scrubbed(1, 3).currentIndex)
    }

    @Test
    fun `the store reports whether a scrub moved the spotlight`() {
        val store = LaunchRecapCursor()
        assertTrue(store.scrub(1, 3))
        assertTrue(store.scrub(1, 3))
        assertFalse(store.scrub(1, 3))
        assertEquals(2, store.state.value.currentIndex)
        assertEquals(3, store.state.value.scrubs)
    }

    @Test
    fun `a reset puts the store back on the first tile with no scrubs`() {
        val store = LaunchRecapCursor()
        store.scrub(1, 3)
        store.reset()
        assertEquals(RecapCursor(), store.state.value)
    }

    @Test
    fun `the spotlight describes the current unlock and its place in the row`() {
        val spotlight = RecapSpotlight.of(unlocks, 1)!!
        assertEquals("b", spotlight.unlock.badgeId)
        assertEquals(2, spotlight.position)
    }

    @Test
    fun `every body scroll is counted with the direction of the latest one`() {
        val down = RecapCursor().bodyScrolled(1)
        val up = down.bodyScrolled(-1)
        assertEquals(1, down.bodyScrolls)
        assertEquals(1, down.bodyScrollDirection)
        assertEquals(2, up.bodyScrolls)
        assertEquals(-1, up.bodyScrollDirection)
    }

    @Test
    fun `a body scroll leaves the spotlight alone`() {
        val cursor = RecapCursor(currentIndex = 2, scrubs = 1)
        val scrolled = cursor.bodyScrolled(1)
        assertEquals(2, scrolled.currentIndex)
        assertEquals(1, scrolled.scrubs)
    }

    @Test
    fun `a body scroll without a direction is ignored`() {
        assertEquals(RecapCursor(), RecapCursor().bodyScrolled(0))
    }

    @Test
    fun `a reset clears the store's body scrolls`() {
        val store = LaunchRecapCursor()
        store.scrollBody(1)
        store.scrollBody(1)
        assertEquals(2, store.state.value.bodyScrolls)
        store.reset()
        assertEquals(0, store.state.value.bodyScrolls)
    }

    @Test
    fun `the spotlight follows the cursor across the row`() {
        val positions = (0..2).map { RecapSpotlight.of(unlocks, it)!!.unlock.badgeId }
        assertEquals(listOf("a", "b", "c"), positions)
    }

    @Test
    fun `a cursor past either end spotlights the nearest unlock`() {
        assertEquals("c", RecapSpotlight.of(unlocks, 7)!!.unlock.badgeId)
        assertEquals("a", RecapSpotlight.of(unlocks, -1)!!.unlock.badgeId)
    }

    @Test
    fun `the panel names the unlock's game only when asked to`() {
        val spotlight = RecapSpotlight.of(listOf(unlock("a", gameTitle = "Super Mario World")), 0)!!
        assertEquals("Super Mario World", spotlight.gameLine(showGame = true))
        assertNull(spotlight.gameLine(showGame = false))
    }

    @Test
    fun `an unlock without a game title draws no game line`() {
        assertNull(RecapSpotlight.of(listOf(unlock("a")), 0)!!.gameLine(showGame = true))
        assertNull(RecapSpotlight.of(listOf(unlock("a", gameTitle = " ")), 0)!!.gameLine(showGame = true))
    }

    @Test
    fun `no unlocks means no spotlight`() {
        assertNull(RecapSpotlight.of(emptyList(), 0))
    }
}
