package com.nendo.argosy.ui.dualscreen

import com.nendo.argosy.hardware.CompanionInGameState
import org.junit.Assert.assertEquals
import org.junit.Test

class PresentationSlotOnDisplayTest {

    private val inGame = PresentationSlot.InGame(CompanionInGameState(), emptyList())

    @Test
    fun `the in-game panel draws on the screen the game did not take`() {
        assertEquals(inGame, inGame.onDisplay(displayId = 4, gameDisplayId = 0))
    }

    @Test
    fun `the game's own screen falls back instead of drawing the in-game panel`() {
        assertEquals(PresentationSlot.Fallback, inGame.onDisplay(displayId = 0, gameDisplayId = 0))
    }

    @Test
    fun `no screen draws the in-game panel once the game's screen is unknown`() {
        assertEquals(PresentationSlot.Fallback, inGame.onDisplay(displayId = 4, gameDisplayId = null))
    }

    @Test
    fun `other slots draw on every screen`() {
        assertEquals(PresentationSlot.Fallback, PresentationSlot.Fallback.onDisplay(displayId = 0, gameDisplayId = 0))
    }
}
