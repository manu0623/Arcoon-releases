package com.nendo.argosy.util

import com.nendo.argosy.data.preferences.EmulatorDisplayTarget
import com.nendo.argosy.util.DisplayAffinityHelper.Companion.resolveDisplayTargetId
import com.nendo.argosy.util.DisplayAffinityHelper.Companion.pickLargestScreen
import com.nendo.argosy.util.DisplayAffinityHelper.Companion.resolveAppDefaultDisplayId
import com.nendo.argosy.util.DisplayAffinityHelper.Companion.resolveAppLaunchDisplayId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private const val BUILT_IN = 0
private const val LOWER = 4
private const val MONITOR = 7

/**
 * The Thor with a monitor attached, where the monitor holds the app-target role.
 */
class LaunchDisplayResolutionTest {

    @Test
    fun `an app takes the app-target screen`() {
        val resolved = resolveAppLaunchDisplayId(
            preferredDisplayId = null,
            defaultDisplayId = null,
            appTargetDisplayId = MONITOR,
            roleDisplayIds = LOWER to BUILT_IN,
            occupiedDisplayId = null
        )

        assertEquals(MONITOR, resolved)
    }

    @Test
    fun `an app falls back to the presentation screen when no app target is set`() {
        val resolved = resolveAppLaunchDisplayId(
            preferredDisplayId = null,
            defaultDisplayId = null,
            appTargetDisplayId = null,
            roleDisplayIds = LOWER to BUILT_IN,
            occupiedDisplayId = null
        )

        assertEquals(BUILT_IN, resolved)
    }

    @Test
    fun `an app pinned to a screen beats the app-target screen`() {
        val resolved = resolveAppLaunchDisplayId(
            preferredDisplayId = LOWER,
            defaultDisplayId = null,
            appTargetDisplayId = MONITOR,
            roleDisplayIds = LOWER to BUILT_IN,
            occupiedDisplayId = null
        )

        assertEquals(LOWER, resolved)
    }

    @Test
    fun `a pinned screen holding a game hands this launch to the next one`() {
        val resolved = resolveAppLaunchDisplayId(
            preferredDisplayId = MONITOR,
            defaultDisplayId = null,
            appTargetDisplayId = MONITOR,
            roleDisplayIds = LOWER to BUILT_IN,
            occupiedDisplayId = MONITOR
        )

        assertEquals(BUILT_IN, resolved)
    }

    @Test
    fun `an unplugged pinned screen resolves to nothing and falls through`() {
        val resolved = resolveAppLaunchDisplayId(
            preferredDisplayId = null,
            defaultDisplayId = null,
            appTargetDisplayId = null,
            roleDisplayIds = LOWER to BUILT_IN,
            occupiedDisplayId = BUILT_IN
        )

        assertEquals(LOWER, resolved)
    }

    @Test
    fun `a game on every screen still leaves the best one`() {
        val resolved = resolveAppLaunchDisplayId(
            preferredDisplayId = BUILT_IN,
            defaultDisplayId = null,
            appTargetDisplayId = null,
            roleDisplayIds = BUILT_IN to BUILT_IN,
            occupiedDisplayId = BUILT_IN
        )

        assertEquals(BUILT_IN, resolved)
    }

    @Test
    fun `an app on a single-screen device names no screen`() {
        val resolved = resolveAppLaunchDisplayId(
            preferredDisplayId = null,
            defaultDisplayId = null,
            appTargetDisplayId = null,
            roleDisplayIds = null,
            occupiedDisplayId = null
        )

        assertNull(resolved)
    }

    @Test
    fun `an app with no pin takes the global apps target over the app-target screen`() {
        val resolved = resolveAppLaunchDisplayId(
            preferredDisplayId = null,
            defaultDisplayId = resolveAppDefaultDisplayId(
                target = EmulatorDisplayTarget.PRIMARY,
                roleDisplayIds = LOWER to BUILT_IN,
                appScreenDisplayId = MONITOR
            ),
            appTargetDisplayId = MONITOR,
            roleDisplayIds = LOWER to BUILT_IN,
            occupiedDisplayId = null
        )

        assertEquals(LOWER, resolved)
    }

    @Test
    fun `a pinned screen beats the global apps target`() {
        val resolved = resolveAppLaunchDisplayId(
            preferredDisplayId = MONITOR,
            defaultDisplayId = LOWER,
            appTargetDisplayId = MONITOR,
            roleDisplayIds = LOWER to BUILT_IN,
            occupiedDisplayId = null
        )

        assertEquals(MONITOR, resolved)
    }

    @Test
    fun `a global apps target holding a game hands this launch to the next screen`() {
        val resolved = resolveAppLaunchDisplayId(
            preferredDisplayId = null,
            defaultDisplayId = BUILT_IN,
            appTargetDisplayId = MONITOR,
            roleDisplayIds = LOWER to BUILT_IN,
            occupiedDisplayId = BUILT_IN
        )

        assertEquals(MONITOR, resolved)
    }

    @Test
    fun `the unset apps target names the app screen while one exists`() {
        val resolved = resolveAppDefaultDisplayId(
            target = EmulatorDisplayTarget.DEFAULT,
            roleDisplayIds = LOWER to BUILT_IN,
            appScreenDisplayId = MONITOR
        )

        assertEquals(MONITOR, resolved)
    }

    @Test
    fun `the unset apps target names the presentation screen without an app screen`() {
        val resolved = resolveAppDefaultDisplayId(
            target = EmulatorDisplayTarget.DEFAULT,
            roleDisplayIds = LOWER to BUILT_IN,
            appScreenDisplayId = null
        )

        assertEquals(BUILT_IN, resolved)
    }

    @Test
    fun `a television outranks a built-in panel of the same resolution`() {
        val largest = pickLargestScreen(
            listOf(
                screen(BUILT_IN, 1920, 1080, builtIn = true),
                screen(MONITOR, 1920, 1080, builtIn = false)
            )
        )

        assertEquals(MONITOR, largest?.displayId)
    }

    @Test
    fun `a television outranks a higher-resolution built-in panel`() {
        val largest = pickLargestScreen(
            listOf(
                screen(BUILT_IN, 2560, 1440, builtIn = true),
                screen(MONITOR, 1280, 720, builtIn = false)
            )
        )

        assertEquals(MONITOR, largest?.displayId)
    }

    @Test
    fun `pixel area decides between two built-in panels`() {
        val largest = pickLargestScreen(
            listOf(
                screen(BUILT_IN, 1920, 1080, builtIn = true),
                screen(LOWER, 1240, 1080, builtIn = true)
            )
        )

        assertEquals(BUILT_IN, largest?.displayId)
    }

    @Test
    fun `pixel area decides between two external panels`() {
        val largest = pickLargestScreen(
            listOf(
                screen(MONITOR, 1920, 1080, builtIn = false),
                screen(LOWER, 3840, 2160, builtIn = false)
            )
        )

        assertEquals(LOWER, largest?.displayId)
    }

    @Test
    fun `no screens name no screen`() {
        assertNull(pickLargestScreen(emptyList()))
    }

    private fun screen(displayId: Int, widthPx: Int, heightPx: Int, builtIn: Boolean) =
        AttachedScreen(
            key = "display:$displayId:${widthPx}x$heightPx",
            displayId = displayId,
            number = displayId,
            widthPx = widthPx,
            heightPx = heightPx,
            builtIn = builtIn
        )

    @Test
    fun `the default target names no screen of its own`() {
        val resolved = resolveDisplayTargetId(
            target = EmulatorDisplayTarget.DEFAULT,
            roleDisplayIds = LOWER to BUILT_IN,
            appScreenDisplayId = MONITOR
        )

        assertNull(resolved)
    }

    @Test
    fun `the presentation target names the presentation screen`() {
        val resolved = resolveDisplayTargetId(
            target = EmulatorDisplayTarget.PRESENTATION,
            roleDisplayIds = LOWER to BUILT_IN,
            appScreenDisplayId = MONITOR
        )

        assertEquals(BUILT_IN, resolved)
    }

    @Test
    fun `the primary target names the primary screen`() {
        val resolved = resolveDisplayTargetId(
            target = EmulatorDisplayTarget.PRIMARY,
            roleDisplayIds = LOWER to BUILT_IN,
            appScreenDisplayId = MONITOR
        )

        assertEquals(LOWER, resolved)
    }

    @Test
    fun `the app-screen target names the app screen`() {
        val resolved = resolveDisplayTargetId(
            target = EmulatorDisplayTarget.APP_SCREEN,
            roleDisplayIds = LOWER to BUILT_IN,
            appScreenDisplayId = MONITOR
        )

        assertEquals(MONITOR, resolved)
    }

    @Test
    fun `the app-screen target falls back to the presentation screen when none is attached`() {
        val resolved = resolveDisplayTargetId(
            target = EmulatorDisplayTarget.APP_SCREEN,
            roleDisplayIds = LOWER to BUILT_IN,
            appScreenDisplayId = null
        )

        assertEquals(BUILT_IN, resolved)
    }

    @Test
    fun `a named target has no screen to take on a single-screen device`() {
        val resolved = resolveDisplayTargetId(
            target = EmulatorDisplayTarget.PRESENTATION,
            roleDisplayIds = null,
            appScreenDisplayId = null
        )

        assertNull(resolved)
    }
}
