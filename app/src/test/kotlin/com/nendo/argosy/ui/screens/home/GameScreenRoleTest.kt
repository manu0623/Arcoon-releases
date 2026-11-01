package com.nendo.argosy.ui.screens.home

import com.nendo.argosy.data.preferences.EmulatorDisplayTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GameScreenRoleTest {

    private val roles = 0 to 4

    @Test
    fun `each role screen maps to its stored role`() {
        assertEquals(EmulatorDisplayTarget.PRIMARY, gameDisplayTargetFor(0, roles, 7))
        assertEquals(EmulatorDisplayTarget.PRESENTATION, gameDisplayTargetFor(4, roles, 7))
        assertEquals(EmulatorDisplayTarget.APP_SCREEN, gameDisplayTargetFor(7, roles, 7))
    }

    @Test
    fun `a swap moves the stored role with the screen`() {
        assertEquals(EmulatorDisplayTarget.PRESENTATION, gameDisplayTargetFor(0, 4 to 0, null))
    }

    @Test
    fun `a screen holding no role is not remembered`() {
        assertNull(gameDisplayTargetFor(9, roles, null))
        assertNull(gameDisplayTargetFor(0, null, null))
    }
}
