package com.nendo.argosy.hardware

import android.view.Display
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForeignAppPolicyTest {

    private val secondary = 2

    private fun cause(
        displayId: Int = secondary,
        finishing: Boolean = false,
        recreating: Boolean = false,
        displayPresent: Boolean = true,
        displayOn: Boolean = true,
        deviceInteractive: Boolean = true,
        gameMoving: Boolean = false,
        sessionActive: Boolean = false,
        gameBoundForThisDisplay: Boolean = false
    ) = ForeignAppPolicy.stopCause(
        displayId = displayId,
        finishing = finishing,
        recreating = recreating,
        displayPresent = displayPresent,
        displayOn = displayOn,
        deviceInteractive = deviceInteractive,
        gameMoving = gameMoving,
        sessionActive = sessionActive,
        gameBoundForThisDisplay = gameBoundForThisDisplay
    )

    @Test
    fun `a stop with nothing of Argosy's in play is a foreign app`() {
        assertEquals(CompanionStopCause.FOREIGN_APP, cause())
    }

    @Test
    fun `a finishing companion is never a foreign app`() {
        assertEquals(CompanionStopCause.FINISHING, cause(finishing = true))
    }

    @Test
    fun `a recreating companion is never a foreign app`() {
        assertEquals(CompanionStopCause.RECREATING, cause(recreating = true))
    }

    @Test
    fun `a stop on a removed display is never a foreign app`() {
        assertEquals(CompanionStopCause.DISPLAY_GONE, cause(displayPresent = false))
    }

    @Test
    fun `a companion pushed onto the default display is never a foreign app`() {
        assertEquals(CompanionStopCause.DISPLAY_GONE, cause(displayId = Display.DEFAULT_DISPLAY))
    }

    @Test
    fun `device sleep is never a foreign app`() {
        assertEquals(CompanionStopCause.DEVICE_ASLEEP, cause(deviceInteractive = false, displayOn = false))
        assertEquals(CompanionStopCause.DEVICE_ASLEEP, cause(deviceInteractive = false))
    }

    @Test
    fun `the companion display turning off is never a foreign app`() {
        assertEquals(CompanionStopCause.DISPLAY_OFF, cause(displayOn = false))
    }

    @Test
    fun `a live game move is never a foreign app`() {
        assertEquals(CompanionStopCause.GAME_MOVE, cause(gameMoving = true))
    }

    @Test
    fun `a running or starting game is never a foreign app`() {
        assertEquals(CompanionStopCause.GAME_SESSION, cause(sessionActive = true))
        assertEquals(CompanionStopCause.GAME_SESSION, cause(gameBoundForThisDisplay = true))
    }

    @Test
    fun `finishing outranks every other cause`() {
        assertEquals(
            CompanionStopCause.FINISHING,
            cause(
                finishing = true,
                recreating = true,
                displayPresent = false,
                displayOn = false,
                deviceInteractive = false,
                gameMoving = true,
                sessionActive = true
            )
        )
    }

    @Test
    fun `a removed display releases the record`() {
        assertTrue(ForeignAppPolicy.displayChangeReleases(secondary, false, false, true))
        assertTrue(ForeignAppPolicy.displayChangeReleases(secondary, false, false, false))
    }

    @Test
    fun `the secondary display turning off while awake releases the record`() {
        assertTrue(ForeignAppPolicy.displayChangeReleases(secondary, true, false, true))
    }

    @Test
    fun `device sleep keeps the record`() {
        assertFalse(ForeignAppPolicy.displayChangeReleases(secondary, true, false, false))
    }

    @Test
    fun `the secondary display staying on keeps the record`() {
        assertFalse(ForeignAppPolicy.displayChangeReleases(secondary, true, true, true))
    }

    @Test
    fun `default display changes never release the record`() {
        assertFalse(ForeignAppPolicy.displayChangeReleases(Display.DEFAULT_DISPLAY, true, false, true))
        assertFalse(ForeignAppPolicy.displayChangeReleases(Display.DEFAULT_DISPLAY, false, false, true))
    }
}
