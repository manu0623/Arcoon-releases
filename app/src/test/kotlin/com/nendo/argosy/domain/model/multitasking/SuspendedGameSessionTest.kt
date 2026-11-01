package com.nendo.argosy.domain.model.multitasking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SuspendedGameSessionTest {

    @Test
    fun `formattedElapsedTime formats minutes and seconds correctly`() {
        val session = SuspendedGameSession(
            gameId = 101L,
            title = "Super Mario World",
            platformName = "SNES",
            packageName = "com.retroarch",
            processPid = 1234,
            startTimeMillis = 1000000L,
            elapsedTimeSeconds = 183L // 3 minutes 3 seconds
        )

        assertEquals("3:03", session.formattedElapsedTime)
    }

    @Test
    fun `achievementProgress calculates ratio correctly`() {
        val session = SuspendedGameSession(
            gameId = 102L,
            title = "Metroid Fusion",
            platformName = "Game Boy Advance",
            packageName = "com.retroarch",
            processPid = 5678,
            startTimeMillis = 1000000L,
            elapsedTimeSeconds = 60L,
            achievementsUnlocked = 5,
            achievementsTotal = 20
        )

        assertEquals(0.25f, session.achievementProgress, 0.001f)
    }

    @Test
    fun `TopScreenUiState isCardCentered returns true only when focused with session`() {
        val session = SuspendedGameSession(
            gameId = 103L,
            title = "Pokemon Emerald",
            platformName = "GBA",
            packageName = "com.retroarch",
            processPid = 9999,
            startTimeMillis = 1000000L,
            elapsedTimeSeconds = 120L
        )

        val stateFocused = TopScreenUiState(
            suspendedSession = session,
            processState = ProcessState.SUSPENDED,
            highlightedGameId = 103L,
            isSuspendedGameFocused = true
        )
        assertTrue(stateFocused.isCardCentered)

        val stateUnfocused = TopScreenUiState(
            suspendedSession = session,
            processState = ProcessState.SUSPENDED,
            highlightedGameId = 104L,
            isSuspendedGameFocused = false
        )
        assertFalse(stateUnfocused.isCardCentered)
    }
}
