package com.nendo.argosy.data.steam

import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.data.storage.AndroidDataAccessor
import com.nendo.argosy.data.storage.StorageVolumeDetector
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory

class SteamInstallPresenceTest {

    private lateinit var tempDir: File
    private lateinit var resolver: SteamPathResolver

    private val gameDao = mockk<GameDao>(relaxed = true)
    private val androidDataAccessor = mockk<AndroidDataAccessor>(relaxed = true)
    private val storageVolumeDetector = mockk<StorageVolumeDetector>(relaxed = true)

    private lateinit var steamBase: File

    @Before
    fun setUp() {
        tempDir = createTempDirectory("steam_presence").toFile()
        steamBase = File(tempDir, "roms/steam")

        every { androidDataAccessor.exists(any()) } returns false
        every { storageVolumeDetector.detectStorageVolumes() } returns emptyList()

        resolver = SteamPathResolver(
            gameDao = gameDao,
            androidDataAccessor = androidDataAccessor,
            storageVolumeDetector = storageVolumeDetector
        )
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    private fun game(localPath: String?) = GameEntity(
        id = 7L,
        platformId = 1L,
        platformSlug = "steam",
        title = "Hades",
        sortTitle = "hades",
        localPath = localPath,
        rommId = null,
        igdbId = null,
        steamAppId = 1145360L,
        steamLauncher = GameEntity.LAUNCHER_UNSPECIFIED,
        source = GameSource.STEAM
    )

    @Test
    fun `an unreachable volume does not count as a deleted install`() = runTest {
        val onMissingCard = File("/storage/ABCD-1234/roms/steam/Hades")

        val installed = resolver.isGameInstalled(game(onMissingCard.absolutePath))

        assertFalse(installed)
        coVerify(exactly = 0) { gameDao.update(any()) }
    }

    @Test
    fun `a reachable parent with the game gone clears the path`() = runTest {
        steamBase.mkdirs()
        val removed = File(steamBase, "Hades")

        val installed = resolver.isGameInstalled(game(removed.absolutePath))

        assertFalse(installed)
        coVerify(exactly = 1) { gameDao.update(match { it.localPath == null }) }
    }

    @Test
    fun `a present directory missing its marker is left alone`() = runTest {
        val present = File(steamBase, "Hades").apply { mkdirs() }

        val installed = resolver.isGameInstalled(game(present.absolutePath))

        assertFalse("no completion marker means not installed", installed)
        coVerify(exactly = 0) { gameDao.update(any()) }
    }

    @Test
    fun `a game without a stored path is not installed and is not touched`() = runTest {
        val installed = resolver.isGameInstalled(game(null))

        assertFalse(installed)
        coVerify(exactly = 0) { gameDao.update(any()) }
    }

    @Test
    fun `a completed install reports installed and is not cleared`() = runTest {
        val dir = File(steamBase, "Hades").apply { mkdirs() }
        File(dir, ".download_complete").createNewFile()

        val installed = resolver.isGameInstalled(game(dir.absolutePath))

        assertTrue(installed)
        coVerify(exactly = 0) { gameDao.update(match { it.localPath == null }) }
    }
}
