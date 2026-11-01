package com.nendo.argosy.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class RaFeaturedModeTest {

    private fun unlock(at: Long, hardcore: Boolean = false) = RaUnlock(
        raId = at,
        title = "Achievement $at",
        description = null,
        points = 5,
        badgePath = null,
        unlockedAt = at,
        hardcore = hardcore,
        gameId = 1L,
        gameTitle = "Game",
        gameCoverPath = null
    )

    private fun softcoreRun(count: Int, hardcoreAtIndex: Int = -1): List<RaUnlock> =
        (0 until count).map { index -> unlock(at = 1000L - index, hardcore = index == hardcoreAtIndex) }

    @Test
    fun `hardcore anywhere in the window features hardcore`() {
        assertEquals(RaFeaturedMode.HARDCORE, featuredModeFor(softcoreRun(10, hardcoreAtIndex = 6)))
    }

    @Test
    fun `a window of only softcore features softcore`() {
        assertEquals(RaFeaturedMode.SOFTCORE, featuredModeFor(softcoreRun(10)))
    }

    @Test
    fun `no unlocks features softcore`() {
        assertEquals(RaFeaturedMode.SOFTCORE, featuredModeFor(emptyList()))
    }

    @Test
    fun `the tenth newest unlock is still inside the window`() {
        assertEquals(
            RaFeaturedMode.HARDCORE,
            featuredModeFor(softcoreRun(11, hardcoreAtIndex = RA_FEATURED_MODE_WINDOW - 1))
        )
    }

    @Test
    fun `the eleventh newest unlock is outside the window`() {
        assertEquals(
            RaFeaturedMode.SOFTCORE,
            featuredModeFor(softcoreRun(11, hardcoreAtIndex = RA_FEATURED_MODE_WINDOW))
        )
    }

    @Test
    fun `input order does not decide what the window holds`() {
        val oldestFirst = softcoreRun(11, hardcoreAtIndex = RA_FEATURED_MODE_WINDOW).reversed()

        assertEquals(RaFeaturedMode.SOFTCORE, featuredModeFor(oldestFirst))
    }

    @Test
    fun `a tracked game widens the window to every unlock it has`() {
        val unlocks = softcoreRun(15, hardcoreAtIndex = 14)

        assertEquals(RaFeaturedMode.SOFTCORE, featuredModeFor(unlocks))
        assertEquals(RaFeaturedMode.HARDCORE, featuredModeFor(unlocks, window = unlocks.size))
    }
}
