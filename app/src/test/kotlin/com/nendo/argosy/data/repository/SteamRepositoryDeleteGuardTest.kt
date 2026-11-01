package com.nendo.argosy.data.repository

import android.content.Context
import android.util.Log
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.model.FileOrigin
import com.nendo.argosy.data.model.GameSource
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory

class SteamRepositoryDeleteGuardTest {

    private lateinit var tempDir: File
    private val gameDao = mockk<GameDao>(relaxed = true)
    private val context = mockk<Context>(relaxed = true)

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0
        tempDir = createTempDirectory("steam_delete_guard").toFile()
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
        unmockkAll()
    }

    private fun repository() = SteamRepository(
        context = context,
        gameDao = gameDao,
        platformDao = mockk(relaxed = true),
        imageCacheManager = mockk(relaxed = true),
        syncPreferencesRepository = mockk(relaxed = true),
        gameArtDao = mockk(relaxed = true),
        gameScreenshotDao = mockk(relaxed = true)
    )

    private fun installedGame(origin: FileOrigin): Pair<GameEntity, File> {
        val installDir = File(tempDir, "Hades").apply { mkdirs() }
        File(installDir, "Hades.exe").writeText("payload")
        val game = GameEntity(
            id = 7L,
            platformId = 1L,
            platformSlug = "steam",
            title = "Hades",
            sortTitle = "hades",
            localPath = installDir.absolutePath,
            fileOrigin = origin,
            rommId = null,
            igdbId = null,
            steamAppId = 1145360L,
            source = GameSource.STEAM
        )
        coEvery { gameDao.getBySteamAppId(1145360L) } returns game
        return game to installDir
    }

    @Test
    fun `uninstalling an Argosy download deletes its files`() = runTest {
        val (game, installDir) = installedGame(FileOrigin.STEAM_DOWNLOAD)

        repository().uninstallGame(1145360L)

        assertFalse(installDir.exists())
        coVerify(exactly = 1) { gameDao.clearLocalPath(game.id) }
    }

    @Test
    fun `uninstalling an adopted install only unlinks it`() = runTest {
        val (game, installDir) = installedGame(FileOrigin.ADOPTED)

        repository().uninstallGame(1145360L)

        assertTrue(installDir.exists())
        assertTrue(File(installDir, "Hades.exe").exists())
        coVerify(exactly = 1) { gameDao.clearLocalPath(game.id) }
    }
}
