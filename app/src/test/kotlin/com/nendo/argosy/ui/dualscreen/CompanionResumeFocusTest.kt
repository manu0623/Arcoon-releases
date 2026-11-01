package com.nendo.argosy.ui.dualscreen

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompanionResumeFocusTest {

    @Test
    fun `an idle launcher hands focus to the companion`() {
        assertTrue(companionTakesFocusOnFirstResume(sessionActive = false, gameActive = false, launchLive = false))
    }

    @Test
    fun `a launch in flight before its session registers keeps focus on the game`() {
        assertFalse(companionTakesFocusOnFirstResume(sessionActive = false, gameActive = false, launchLive = true))
    }

    @Test
    fun `a stored session keeps focus on the game`() {
        assertFalse(companionTakesFocusOnFirstResume(sessionActive = true, gameActive = false, launchLive = false))
    }

    @Test
    fun `an active game keeps focus on the game`() {
        assertFalse(companionTakesFocusOnFirstResume(sessionActive = false, gameActive = true, launchLive = false))
    }
}
