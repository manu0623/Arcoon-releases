package com.nendo.argosy.data.repository

import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.data.storage.FileAccessLayer
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamInstallStatusTest {

    private val installDir = "/storage/emulated/0/roms/steam/Hades"
    private var presentPaths = emptySet<String>()
    private val fileAccessLayer = mockk<FileAccessLayer> {
        every { exists(any()) } answers { firstArg<String>() in presentPaths }
    }
    private val repository = DownloadFileStatusRepository(fileAccessLayer)

    private fun game(launcher: String? = GameEntity.LAUNCHER_UNSPECIFIED) = GameEntity(
        id = 7L,
        platformId = 1L,
        platformSlug = "steam",
        title = "Hades",
        sortTitle = "hades",
        localPath = installDir,
        rommId = null,
        igdbId = null,
        steamAppId = 1145360L,
        steamLauncher = launcher,
        source = GameSource.STEAM
    )

    @Test
    fun `a download left in progress is not installed`() = runTest {
        presentPaths = setOf(installDir, "$installDir/.download_in_progress")

        assertFalse(repository.isSteamInstalled(isExternallyManaged = false, localPath = installDir))
        assertFalse(repository.isContentAvailable(game()))
    }

    @Test
    fun `a completed download is installed`() = runTest {
        presentPaths = setOf(installDir, "$installDir/.download_complete")

        assertTrue(repository.isSteamInstalled(isExternallyManaged = false, localPath = installDir))
        assertTrue(repository.isContentAvailable(game()))
    }

    @Test
    fun `a launcher-managed game is installed without any marker`() = runTest {
        presentPaths = emptySet()
        val managed = game(launcher = "app.gamenative")

        assertTrue(repository.isSteamInstalled(isExternallyManaged = managed.isExternallyManaged, localPath = installDir))
        assertTrue(repository.isContentAvailable(managed))
    }
}
