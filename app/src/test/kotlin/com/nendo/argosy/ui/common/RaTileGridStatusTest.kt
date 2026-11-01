package com.nendo.argosy.ui.common

import com.nendo.argosy.domain.model.RaFeaturedMode
import com.nendo.argosy.domain.model.RaLockedAchievement
import com.nendo.argosy.domain.model.RaTileContent
import com.nendo.argosy.domain.model.RaUnlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val GAME_ID = 5L

class RaTileGridStatusTest {

    @Test
    fun `signed out has nothing to browse`() {
        val signedOut: RaTileContent? = null

        val status = signedOut.toGridStatus()

        assertFalse(status.signedIn)
        assertFalse(status.tracksGame)
        assertEquals(0, status.browseCount)
    }

    @Test
    fun `an account browses its recent unlocks`() {
        val status = RaTileContent.Account(
            username = "nendo",
            featuredMode = RaFeaturedMode.SOFTCORE,
            points = 30,
            unlocks = 3,
            latestUnlock = unlock(1L),
            recentUnlocks = listOf(unlock(1L), unlock(2L), unlock(3L)),
            latestGameEarned = 1,
            latestGameTotal = 10
        ).toGridStatus()

        assertTrue(status.signedIn)
        assertFalse(status.tracksGame)
        assertEquals(3, status.browseCount)
    }

    @Test
    fun `a tracked game browses its latest unlock and what is still locked`() {
        val status = RaTileContent.TrackedGame(
            username = "nendo",
            featuredMode = RaFeaturedMode.HARDCORE,
            points = 40,
            unlocks = 1,
            latestUnlock = unlock(1L),
            gameId = GAME_ID,
            gameTitle = "Metroid",
            gameCoverPath = null,
            total = 4,
            nextLocked = listOf(locked(5L), locked(6L)),
            mastered = false
        ).toGridStatus()

        assertTrue(status.signedIn)
        assertTrue(status.tracksGame)
        assertEquals(3, status.browseCount)
    }

    private fun unlock(raId: Long) = RaUnlock(
        raId = raId,
        title = "Achievement $raId",
        description = null,
        points = 10,
        badgePath = null,
        unlockedAt = 1_000L + raId,
        hardcore = false,
        gameId = GAME_ID,
        gameTitle = "Metroid",
        gameCoverPath = null
    )

    private fun locked(raId: Long) = RaLockedAchievement(
        raId = raId,
        title = "Locked $raId",
        description = null,
        points = 5,
        badgeLockPath = null
    )
}
