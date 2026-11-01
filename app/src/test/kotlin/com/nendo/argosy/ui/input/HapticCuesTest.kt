package com.nendo.argosy.ui.input

import com.nendo.argosy.core.input.SoundType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HapticCuesTest {

    @Test
    fun `launching a game plays no haptic cue`() {
        assertNull(SoundType.LAUNCH_GAME.hapticCue)
    }

    @Test
    fun `select still plays the selection tick`() {
        assertEquals(HapticPattern.SELECTION, SoundType.SELECT.hapticCue)
    }
}
