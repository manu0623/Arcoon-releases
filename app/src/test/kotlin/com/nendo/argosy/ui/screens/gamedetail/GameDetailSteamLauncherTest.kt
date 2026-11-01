package com.nendo.argosy.ui.screens.gamedetail

import com.nendo.argosy.data.launcher.DroidDeckLauncher
import com.nendo.argosy.data.launcher.GameNativeLauncher
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.model.GameSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class GameDetailSteamLauncherTest {
    private val game = GameEntity(
        id = 1L,
        platformId = 1L,
        platformSlug = "steam",
        title = "Example",
        sortTitle = "Example",
        localPath = null,
        rommId = null,
        igdbId = null,
        steamAppId = 1L,
        steamLauncher = GameNativeLauncher.packageName,
        launcherSetManually = true,
        source = GameSource.STEAM
    )

    private fun detail(entity: GameEntity = game) = entity.toGameDetailUi(
        art = null,
        screenshotRows = emptyList(),
        platformName = "Steam",
        emulatorName = null,
        canPlay = true
    )

    @Test
    fun `retained manual pin survives lookup failure and another launcher report`() {
        val candidates = game.steamLaunchCandidates(
            reporting = listOf(DroidDeckLauncher.packageName),
            isInstalled = { pkg ->
                if (pkg == GameNativeLauncher.packageName) error("package lookup unavailable")
                pkg == DroidDeckLauncher.packageName
            }
        )
        val refreshed = detail().withSteamLauncher(game, candidates, pathExists = false)

        assertTrue(candidates.isEmpty())
        assertTrue(refreshed.steamLauncherSetManually)
        assertEquals(GameNativeLauncher.displayName, refreshed.steamLauncherName)
        assertEquals(GameNativeLauncher.packageName, refreshed.steamLauncherPackage)
        assertFalse(refreshed.canPlay)
    }

    @Test
    fun `auto resolves installed reported replacement`() {
        val auto = game.copy(launcherSetManually = false)
        val candidates = auto.steamLaunchCandidates(
            reporting = listOf(DroidDeckLauncher.packageName),
            isInstalled = { it == DroidDeckLauncher.packageName }
        )
        val refreshed = detail(auto).withSteamLauncher(auto, candidates, pathExists = false)

        assertFalse(refreshed.steamLauncherSetManually)
        assertEquals(DroidDeckLauncher.displayName, refreshed.steamLauncherName)
        assertTrue(refreshed.canPlay)
    }

    @Test
    fun `cleared or native association cannot retain a manual selection`() {
        for (launcher in listOf(null, GameEntity.LAUNCHER_UNSPECIFIED)) {
            val unlinked = game.copy(steamLauncher = launcher)
            val candidates = unlinked.steamLaunchCandidates(listOf(DroidDeckLauncher.packageName)) {
                it == DroidDeckLauncher.packageName
            }
            val refreshed = detail(unlinked).withSteamLauncher(unlinked, candidates, pathExists = false)

            assertFalse(refreshed.steamLauncherSetManually)
            assertEquals(DroidDeckLauncher.displayName, refreshed.steamLauncherName)
            assertEquals(listOf(DroidDeckLauncher.packageName), candidates)
            assertFalse(refreshed.canPlay)
        }
    }

    @Test
    fun `launcher refresh preserves unrelated detail fields`() {
        val before = detail().copy(title = "Current title", isFavorite = true, userRating = 8)
        val candidates = game.steamLaunchCandidates(listOf(DroidDeckLauncher.packageName)) { true }
        val refreshed = before.withSteamLauncher(game, candidates, pathExists = false)

        assertEquals(before.copy(
            steamLauncherName = GameNativeLauncher.displayName,
            steamLauncherPackage = GameNativeLauncher.packageName,
            managingLauncherDisplayName = GameNativeLauncher.displayName
        ), refreshed)
    }

    @Test
    fun `launcher refresh ignores another game`() {
        val before = detail().copy(id = 2L)

        assertSame(before, before.withSteamLauncher(game, emptyList(), false))
    }
}
