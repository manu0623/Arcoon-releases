package com.nendo.argosy.ui.screens.settings

import com.nendo.argosy.data.local.entity.PlatformEntity
import com.nendo.argosy.data.platform.LocalPlatformIds
import com.nendo.argosy.data.preferences.EmulatorDisplayTarget
import com.nendo.argosy.domain.model.ScreenRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val UPPER = 0
private const val LOWER = 4
private const val MONITOR = 7

class LaunchTargetsLayoutTest {

    private val primary = LaunchTargetScreen("upper", UPPER, 1, ScreenRole.PRIMARY)
    private val presentation = LaunchTargetScreen("lower", LOWER, 2, ScreenRole.PRESENTATION)
    private val appScreen = LaunchTargetScreen("monitor", MONITOR, 3, ScreenRole.APP_TARGET)
    private val screens = listOf(primary, presentation, appScreen)

    @Test
    fun `entries group under the screen their resolved display belongs to`() {
        val state = LaunchTargetsState(
            screens = screens,
            platforms = listOf(platform(1, displayId = LOWER), platform(2, displayId = UPPER)),
            apps = listOf(app("browser", displayId = MONITOR)),
            appsLoaded = true
        )

        val groups = state.groups()

        assertEquals(listOf("platform:2"), groups[0].entries.map { it.key })
        assertEquals(listOf("platform:1"), groups[1].entries.map { it.key })
        assertEquals(listOf("app:browser"), groups[2].entries.map { it.key })
    }

    @Test
    fun `an entry resolving to no listed screen falls in the first group`() {
        val state = LaunchTargetsState(
            screens = screens,
            platforms = listOf(platform(1, displayId = 99)),
            appsLoaded = true
        )

        assertEquals(listOf("platform:1"), state.groups().first().entries.map { it.key })
    }

    @Test
    fun `apps sort by label while platforms keep the platforms list order`() {
        val state = LaunchTargetsState(
            screens = listOf(presentation),
            platforms = listOf(platform(9, displayId = LOWER), platform(3, displayId = LOWER)),
            apps = listOf(app("zeta", displayId = LOWER), app("Alpha", displayId = LOWER)),
            appsLoaded = true
        )

        val group = state.groups().single()

        assertEquals(listOf(9L, 3L), group.platforms.map { it.platformId })
        assertEquals(listOf("Alpha", "zeta"), group.apps.map { it.label })
    }

    @Test
    fun `a screen with nothing on it keeps its heading and an empty line but no focus span`() {
        val state = LaunchTargetsState(
            screens = listOf(primary, presentation),
            platforms = listOf(platform(1, displayId = LOWER)),
            appsLoaded = true
        )

        val layout = launchTargetsLayout(state)

        assertTrue(layout.rows.any { it is LaunchTargetsRow.Empty && it.number == 1 })
        assertTrue(layout.rows.any { it is LaunchTargetsRow.Heading && it.screen.number == 1 })
        assertEquals(2, layout.sections.size)
        assertEquals(1, layout.jumpSections.size)
        assertEquals(0, layout.jumpSections.single().focusStartIndex)
    }

    @Test
    fun `every group shows the loading line until the apps arrive`() {
        val state = LaunchTargetsState(
            screens = listOf(primary, presentation),
            platforms = listOf(platform(1, displayId = LOWER))
        )

        val layout = launchTargetsLayout(state)

        assertEquals(2, layout.rows.count { it is LaunchTargetsRow.Loading })
        assertTrue(layout.rows.none { it is LaunchTargetsRow.Empty })
    }

    @Test
    fun `a block with entries opens with its count and a block without them omits the label`() {
        val state = LaunchTargetsState(
            screens = listOf(presentation),
            platforms = listOf(platform(1, displayId = LOWER), platform(2, displayId = LOWER)),
            appsLoaded = true
        )

        val labels = launchTargetsLayout(state).rows.filterIsInstance<LaunchTargetsRow.BlockLabel>()

        assertEquals(listOf(LaunchTargetBlock.PLATFORMS to 2), labels.map { it.block to it.count })
    }

    @Test
    fun `focus indices map onto the rows that render the entries`() {
        val state = LaunchTargetsState(
            screens = listOf(primary, presentation),
            platforms = listOf(platform(1, displayId = UPPER), platform(2, displayId = LOWER)),
            appsLoaded = true
        )

        val layout = launchTargetsLayout(state)

        layout.entries.indices.forEach { focusIndex ->
            val row = layout.rows[layout.listIndexOf(focusIndex)]
            assertEquals(layout.entries[focusIndex].key, row.key)
        }
    }

    @Test
    fun `a role swap gives the primary role to the display the resolver now names`() {
        val swapped = LOWER to UPPER

        assertEquals(ScreenRole.PRIMARY, currentScreenRole(LOWER, ScreenRole.PRESENTATION, swapped, MONITOR))
        assertEquals(ScreenRole.PRESENTATION, currentScreenRole(UPPER, ScreenRole.PRIMARY, swapped, MONITOR))
        assertEquals(ScreenRole.APP_TARGET, currentScreenRole(MONITOR, ScreenRole.APP_TARGET, swapped, MONITOR))
    }

    @Test
    fun `an off screen stays off whatever the resolvers name`() {
        assertEquals(ScreenRole.OFF, currentScreenRole(MONITOR, ScreenRole.OFF, UPPER to LOWER, MONITOR))
    }

    @Test
    fun `only platforms with games and a display target option are listed, android and steam left out`() {
        val platforms = listOf(
            platformEntity(1, "nes", gameCount = 3),
            platformEntity(2, "snes", gameCount = 0),
            platformEntity(LocalPlatformIds.ANDROID, "android", gameCount = 5),
            platformEntity(4, "steam", gameCount = 8),
            platformEntity(5, "gba", gameCount = 2, syncEnabled = false),
            platformEntity(6, "psx", gameCount = 1)
        )

        assertEquals(listOf(1L, 6L, 5L), launchTargetPlatforms(platforms, showsDisplayTarget = true).map { it.id })
        assertTrue(launchTargetPlatforms(platforms, showsDisplayTarget = false).isEmpty())
    }

    @Test
    fun `a platform pick stores the role the picked screen holds`() {
        val state = LaunchTargetsState(screens = screens)

        assertEquals(EmulatorDisplayTarget.DEFAULT, state.platformPick(0))
        assertEquals(EmulatorDisplayTarget.PRIMARY, state.platformPick(1))
        assertEquals(EmulatorDisplayTarget.PRESENTATION, state.platformPick(2))
        assertEquals(EmulatorDisplayTarget.APP_SCREEN, state.platformPick(3))
        assertNull(state.platformPick(4))
    }

    @Test
    fun `an app pick stores the picked screen and use default removes the pin`() {
        val state = LaunchTargetsState(screens = screens)

        assertNull(state.appPick(0))
        assertEquals("lower", state.appPick(2))
        assertTrue(state.isPickable(3))
        assertTrue(!state.isPickable(4))
    }

    @Test
    fun `the picker marks what each entry stores`() {
        val state = LaunchTargetsState(screens = screens)

        assertEquals(0, state.pickerSelection(platform(1, displayId = LOWER)))
        assertEquals(2, state.pickerSelection(platform(1, displayId = LOWER, stored = EmulatorDisplayTarget.PRESENTATION)))
        assertEquals(3, state.pickerSelection(app("browser", displayId = MONITOR, pinnedKey = "monitor")))
        assertEquals(-1, state.pickerSelection(app("browser", displayId = LOWER, pinnedKey = "gone")))
    }

    @Test
    fun `a stored app screen with no app screen marks nothing`() {
        val state = LaunchTargetsState(screens = listOf(primary, presentation))

        assertEquals(-1, state.pickerSelection(platform(1, displayId = LOWER, stored = EmulatorDisplayTarget.APP_SCREEN)))
    }

    @Test
    fun `a commit that keeps the entry on its screen keeps focus on it`() {
        val before = layoutOf(platform(1, LOWER), platform(2, LOWER), platform(3, LOWER))
        val after = layoutOf(platform(1, LOWER), platform(2, LOWER, stored = EmulatorDisplayTarget.PRESENTATION), platform(3, LOWER))

        assertEquals(1, refocusAfterCommit(before, after, "platform:2", focusIndex = 1))
    }

    @Test
    fun `a commit that moves the entry hands focus to the row that followed it`() {
        val before = layoutOf(platform(1, LOWER), platform(2, LOWER), platform(3, LOWER))
        val after = layoutOf(platform(1, LOWER), platform(2, UPPER), platform(3, LOWER))

        val focus = refocusAfterCommit(before, after, "platform:2", focusIndex = 1)

        assertEquals("platform:3", after.entryAt(focus)?.key)
    }

    @Test
    fun `a commit that moves the last entry hands focus to the row before it`() {
        val before = layoutOf(platform(1, LOWER), platform(2, LOWER))
        val after = layoutOf(platform(1, LOWER), platform(2, UPPER))

        val focus = refocusAfterCommit(before, after, "platform:2", focusIndex = 1)

        assertEquals("platform:1", after.entryAt(focus)?.key)
    }

    @Test
    fun `a reload follows the focused entry to its new group`() {
        val before = layoutOf(platform(1, UPPER), platform(2, LOWER))
        val after = layoutOf(platform(1, LOWER), platform(2, UPPER))

        val focus = refocusByKey(before, after, focusIndex = 0)

        assertEquals("platform:1", after.entryAt(focus)?.key)
    }

    private fun layoutOf(vararg platforms: LaunchTargetPlatformEntry): LaunchTargetsLayout =
        launchTargetsLayout(
            LaunchTargetsState(screens = listOf(primary, presentation), platforms = platforms.toList(), appsLoaded = true)
        )

    private fun platform(
        id: Long,
        displayId: Int,
        stored: EmulatorDisplayTarget = EmulatorDisplayTarget.DEFAULT
    ) = LaunchTargetPlatformEntry(
        platformId = id,
        slug = "slug$id",
        label = "Platform $id",
        stored = stored,
        gameOverrides = 0,
        displayId = displayId
    )

    private fun app(packageName: String, displayId: Int, pinnedKey: String? = null) = LaunchTargetAppEntry(
        packageName = packageName,
        label = packageName,
        pinnedKey = pinnedKey,
        pinnedScreen = screens.firstOrNull { it.key == pinnedKey },
        displayId = displayId
    )

    private fun platformEntity(id: Long, slug: String, gameCount: Int, syncEnabled: Boolean = true) =
        PlatformEntity(
            id = id,
            slug = slug,
            name = slug,
            shortName = slug,
            romExtensions = "",
            gameCount = gameCount,
            syncEnabled = syncEnabled
        )
}
