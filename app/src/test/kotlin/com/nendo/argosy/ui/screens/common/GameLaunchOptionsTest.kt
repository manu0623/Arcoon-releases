package com.nendo.argosy.ui.screens.common

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameLaunchOptionsTest {

    @Test
    fun `android 11 has no splash style to ask for`() {
        assertFalse(GameLaunchOptions.solidSplashOn(30))
    }

    @Test
    fun `android 12 and 12L keep the theme splash because the style request is not public there`() {
        assertFalse(GameLaunchOptions.solidSplashOn(31))
        assertFalse(GameLaunchOptions.solidSplashOn(32))
    }

    @Test
    fun `android 13 and later ask for the solid colour splash`() {
        assertTrue(GameLaunchOptions.solidSplashOn(33))
        assertTrue(GameLaunchOptions.solidSplashOn(35))
    }

    @Test
    fun `the minimum sdk asks for nothing`() {
        assertFalse(GameLaunchOptions.solidSplashOn(26))
    }
}
