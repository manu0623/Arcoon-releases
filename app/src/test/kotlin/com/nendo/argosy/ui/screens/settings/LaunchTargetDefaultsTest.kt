package com.nendo.argosy.ui.screens.settings

import com.nendo.argosy.data.preferences.EmulatorDisplayTarget
import com.nendo.argosy.ui.screens.common.EmulatorLaunchTargetResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LaunchTargetDefaultsTest {

    private val withAppScreen = LaunchTargetScreens(primary = 1, presentation = 2, appScreen = 3)
    private val withoutAppScreen = LaunchTargetScreens(primary = 1, presentation = 2)

    @Test
    fun `a game with nothing stored takes the global games target`() {
        val target = EmulatorLaunchTargetResolver.placementTarget(
            stored = EmulatorDisplayTarget.DEFAULT,
            gamesDefault = EmulatorDisplayTarget.PRIMARY
        )

        assertEquals(EmulatorDisplayTarget.PRIMARY, target)
    }

    @Test
    fun `a stored platform or game target beats the global games target`() {
        val target = EmulatorLaunchTargetResolver.placementTarget(
            stored = EmulatorDisplayTarget.PRESENTATION,
            gamesDefault = EmulatorDisplayTarget.PRIMARY
        )

        assertEquals(EmulatorDisplayTarget.PRESENTATION, target)
    }

    @Test
    fun `a global presentation target keeps the built-in game placement`() {
        val target = EmulatorLaunchTargetResolver.placementTarget(
            stored = EmulatorDisplayTarget.DEFAULT,
            gamesDefault = EmulatorDisplayTarget.PRESENTATION
        )

        assertEquals(EmulatorDisplayTarget.DEFAULT, target)
    }

    @Test
    fun `the inherited game target never reads as default`() {
        EmulatorDisplayTarget.entries.forEach { global ->
            val resolved = EmulatorLaunchTargetResolver.resolvedTarget(
                stored = EmulatorDisplayTarget.DEFAULT,
                gamesDefault = global
            )

            assertTrue(resolved != EmulatorDisplayTarget.DEFAULT)
        }
    }

    @Test
    fun `every games option survives a store and reload`() {
        withAppScreen.options.forEachIndexed { index, option ->
            val reloaded = EmulatorDisplayTarget.gamesDefaultFrom(option.name)

            assertEquals(index, withAppScreen.options.indexOf(withAppScreen.shown(reloaded)))
        }
    }

    @Test
    fun `every apps option survives a store and reload`() {
        withAppScreen.options.forEachIndexed { index, option ->
            val reloaded = EmulatorDisplayTarget.appsDefaultFrom(option.name)

            assertEquals(index, withAppScreen.options.indexOf(withAppScreen.shown(reloaded)))
        }
    }

    @Test
    fun `an unset games target reads as presentation`() {
        assertEquals(EmulatorDisplayTarget.PRESENTATION, EmulatorDisplayTarget.gamesDefaultFrom(null))
    }

    @Test
    fun `an unset apps target shows the app screen while one exists`() {
        val stored = EmulatorDisplayTarget.appsDefaultFrom(null)

        assertEquals(EmulatorDisplayTarget.APP_SCREEN, withAppScreen.shown(stored))
        assertEquals(EmulatorDisplayTarget.PRESENTATION, withoutAppScreen.shown(stored))
    }

    @Test
    fun `the app screen option is offered only while a screen holds that role`() {
        assertTrue(EmulatorDisplayTarget.APP_SCREEN in withAppScreen.options)
        assertFalse(EmulatorDisplayTarget.APP_SCREEN in withoutAppScreen.options)
    }

    @Test
    fun `a stored app screen with no app screen is kept and flagged`() {
        val shown = withoutAppScreen.shown(EmulatorDisplayTarget.APP_SCREEN)

        assertEquals(EmulatorDisplayTarget.APP_SCREEN, shown)
        assertTrue(withoutAppScreen.lacksAppScreen(shown))
    }
}
