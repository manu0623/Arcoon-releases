package com.nendo.argosy.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeAppComponentTest {

    @Test
    fun `the alias stays enabled while argosy is the default home whatever the setting says`() {
        assertTrue(HomeAppComponent.componentEnabled(available = false, isDefaultHome = true))
        assertTrue(HomeAppComponent.componentEnabled(available = true, isDefaultHome = true))
    }

    @Test
    fun `the alias follows the setting while argosy is not the default home`() {
        assertTrue(HomeAppComponent.componentEnabled(available = true, isDefaultHome = false))
        assertFalse(HomeAppComponent.componentEnabled(available = false, isDefaultHome = false))
    }

    @Test
    fun `first start defaults off only on a desktop mode device where argosy is not home`() {
        val cases = listOf(
            Triple(true, false, false),
            Triple(true, true, true),
            Triple(false, false, true),
            Triple(false, true, true)
        )
        cases.forEach { (desktopModeSupported, isDefaultHome, expected) ->
            val startup = HomeAppComponent.startupAvailability(
                stored = true,
                defaultApplied = false,
                desktopModeSupported = desktopModeSupported,
                isDefaultHome = isDefaultHome
            )
            assertEquals(
                "desktopMode=$desktopModeSupported home=$isDefaultHome",
                HomeAppStartup(available = expected, persistDefault = true),
                startup
            )
        }
    }

    @Test
    fun `once applied the stored choice wins in every arrangement and nothing is rewritten`() {
        for (stored in listOf(true, false)) {
            for (desktopModeSupported in listOf(true, false)) {
                for (isDefaultHome in listOf(true, false)) {
                    val startup = HomeAppComponent.startupAvailability(
                        stored = stored,
                        defaultApplied = true,
                        desktopModeSupported = desktopModeSupported,
                        isDefaultHome = isDefaultHome
                    )
                    assertEquals(
                        "stored=$stored desktopMode=$desktopModeSupported home=$isDefaultHome",
                        HomeAppStartup(available = stored, persistDefault = false),
                        startup
                    )
                }
            }
        }
    }

    @Test
    fun `a task rooted in either the real activity or the home alias is the launcher task`() {
        assertTrue(HomeAppComponent.isLauncherActivity("com.nendo.argosy.ArgosyActivity"))
        assertTrue(HomeAppComponent.isLauncherActivity("com.nendo.argosy.MainActivity"))
    }

    @Test
    fun `any other root is not the launcher task`() {
        assertFalse(HomeAppComponent.isLauncherActivity("com.nendo.argosy.hardware.SecondaryHomeActivity"))
        assertFalse(HomeAppComponent.isLauncherActivity("com.nendo.argosy.HomeActivity"))
        assertFalse(HomeAppComponent.isLauncherActivity(null))
    }
}
