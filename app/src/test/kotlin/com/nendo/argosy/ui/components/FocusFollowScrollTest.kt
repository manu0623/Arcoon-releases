package com.nendo.argosy.ui.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusFollowScrollTest {

    @Test
    fun `a focus change made by touch leaves the list where it is`() {
        assertFalse(shouldFollowFocus(isInitialPass = false, isTouchMode = true))
    }

    @Test
    fun `a focus change made by the gamepad scrolls to the focused row`() {
        assertTrue(shouldFollowFocus(isInitialPass = false, isTouchMode = false))
    }

    @Test
    fun `the first pass places the list even in touch mode`() {
        assertTrue(shouldFollowFocus(isInitialPass = true, isTouchMode = true))
        assertTrue(shouldFollowFocus(isInitialPass = true, isTouchMode = false))
    }
}
