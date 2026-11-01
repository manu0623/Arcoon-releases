package com.nendo.argosy.ui.common

import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.local.entity.GameListItem
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.data.repository.DownloadFileStatusRepository
import com.nendo.argosy.ui.screens.gamedetail.toGameDetailUi
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class ServerRemovedMappingTest {

    private val downloadStatus = mockk<DownloadFileStatusRepository>(relaxed = true)

    private fun entity(rommId: Long?, localPath: String?) = GameEntity(
        id = 9L,
        platformId = 1L,
        platformSlug = "saturn",
        title = "Rockman X4",
        sortTitle = "rockman x4",
        localPath = localPath,
        rommId = rommId,
        igdbId = null,
        source = if (localPath == null) GameSource.ROMM_REMOTE else GameSource.ROMM_SYNCED
    )

    private fun listItem(rommId: Long?, localPath: String?) = GameListItem(
        id = 9L,
        platformId = 1L,
        platformSlug = "saturn",
        title = "Rockman X4",
        sortTitle = "rockman x4",
        localPath = localPath,
        source = if (localPath == null) GameSource.ROMM_REMOTE else GameSource.ROMM_SYNCED,
        isFavorite = false,
        isHidden = false,
        isMultiDisc = false,
        rommId = rommId,
        steamAppId = null,
        packageName = null,
        steamLauncher = null,
        playCount = 0,
        playTimeMinutes = 0,
        lastPlayed = null,
        genre = null,
        players = null,
        rating = null,
        userRating = 0,
        userDifficulty = 0,
        releaseYear = null,
        addedAt = Instant.EPOCH,
        achievementCount = 0,
        earnedAchievementCount = 0,
        completion = 0,
        status = null,
        developer = null,
        igdbId = null,
        timeToBeatMainSec = null
    )

    private fun GameEntity.detail() = toGameDetailUi(
        art = null,
        screenshotRows = emptyList(),
        platformName = "Saturn",
        emulatorName = null,
        canPlay = false
    )

    @Test
    fun `a server-removed game with no rom path maps as removable on every surface`() = runTest {
        val game = entity(rommId = -9L, localPath = null)

        assertTrue(game.toHomeGameUi(downloadStatus, art = null, firstScreenshotUrl = null).canRemoveFromLibrary)
        assertTrue(game.toLibraryGameUi(downloadStatus, art = null).canRemoveFromLibrary)
        assertTrue(listItem(rommId = -9L, localPath = null).toHomeGameUi(downloadStatus, art = null).canRemoveFromLibrary)
        assertTrue(listItem(rommId = -9L, localPath = null).toLibraryGameUi(downloadStatus, art = null).canRemoveFromLibrary)
        assertTrue(game.detail().canRemoveFromLibrary)
        assertTrue(game.detail().isRemovedFromRomm)
    }

    @Test
    fun `a server-removed game with a rom path is marked but not removable`() = runTest {
        val game = entity(rommId = -9L, localPath = "/roms/saturn/x4.cue")

        assertTrue(game.detail().isRemovedFromRomm)
        assertFalse(game.detail().canRemoveFromLibrary)
        assertFalse(game.toLibraryGameUi(downloadStatus, art = null).canRemoveFromLibrary)
        assertFalse(listItem(rommId = -9L, localPath = "/roms/saturn/x4.cue").toHomeGameUi(downloadStatus, art = null).canRemoveFromLibrary)
    }

    @Test
    fun `a game still on RomM is neither marked nor removable`() = runTest {
        val game = entity(rommId = 42L, localPath = null)

        assertFalse(game.detail().isRemovedFromRomm)
        assertFalse(game.toHomeGameUi(downloadStatus, art = null, firstScreenshotUrl = null).canRemoveFromLibrary)
        assertFalse(listItem(rommId = 42L, localPath = null).toLibraryGameUi(downloadStatus, art = null).canRemoveFromLibrary)
    }
}
