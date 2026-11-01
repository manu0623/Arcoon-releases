package com.nendo.argosy.domain.usecase

import com.nendo.argosy.data.local.dao.GameFileDao
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.local.entity.GameFileEntity
import com.nendo.argosy.data.local.entity.PlatformEntity
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.data.music.MusicDirectoryManager
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.repository.GameRepository
import com.nendo.argosy.data.repository.PlatformRepository
import com.nendo.argosy.data.storage.GameFileRelocator
import com.nendo.argosy.data.storage.GamePathRewriter
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class MigrateStorageUseCaseTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var gameRepository: GameRepository
    private lateinit var platformRepository: PlatformRepository
    private lateinit var customDir: File
    private lateinit var preferencesRepository: UserPreferencesRepository
    private lateinit var gameFileDao: GameFileDao
    private lateinit var pathRewriter: GamePathRewriter
    private lateinit var useCase: MigrateStorageUseCase

    private lateinit var oldDir: File
    private lateinit var newDir: File
    private val games = mutableMapOf<Long, GameEntity>()
    private val rewrites = mutableListOf<List<Pair<String, String>>>()

    @Before
    fun setup() {
        gameRepository = mockk(relaxed = true)
        platformRepository = mockk(relaxed = true)
        preferencesRepository = mockk(relaxed = true)
        gameFileDao = mockk(relaxed = true)
        pathRewriter = mockk(relaxed = true)
        val music = mockk<MusicDirectoryManager> {
            coEvery { resolveMusicDir() } returns tempFolder.newFolder("music")
        }
        oldDir = tempFolder.newFolder("old")
        newDir = tempFolder.newFolder("new")

        coEvery { gameRepository.getById(any()) } answers { games[firstArg()] }
        customDir = tempFolder.newFolder("custom-psx")
        coEvery { gameRepository.getDownloadDirForPlatformId(any()) } answers {
            when (firstArg<Long>()) {
                CUSTOM_PLATFORM -> customDir
                2L -> File(oldDir, "snes")
                else -> File(oldDir, "psx")
            }
        }
        coEvery { platformRepository.getById(any()) } answers {
            val custom = customDir.absolutePath.takeIf { firstArg<Long>() == CUSTOM_PLATFORM }
            mockk<PlatformEntity> { every { customRomPath } returns custom }
        }
        coEvery { gameRepository.getGamesWithLocalPaths() } answers { games.values.toList() }
        val captured = slot<List<Pair<String, String>>>()
        coEvery { pathRewriter.rewrite(capture(captured)) } answers { rewrites += captured.captured }

        useCase = MigrateStorageUseCase(
            gameRepository,
            platformRepository,
            preferencesRepository,
            mockk(relaxed = true),
            GameFileRelocator(gameFileDao, music, pathRewriter)
        )
    }

    private fun write(relative: String, text: String = "x") =
        File(oldDir, relative).apply { parentFile?.mkdirs(); writeText(text) }

    private fun add(game: GameEntity) { games[game.id] = game }

    private fun newPath(relative: String) = File(newDir, relative).absolutePath

    @Test
    fun `a single rom moves and its stored path is repointed`() = runTest {
        val rom = write("psx/game.chd", "rom data")
        add(createGameEntity(id = 123, localPath = rom.absolutePath))

        val result = useCase(oldDir.absolutePath, newDir.absolutePath)

        assertEquals(1, result.migrated)
        assertEquals("rom data", File(newPath("psx/game.chd")).readText())
        assertFalse(rom.exists())
        assertEquals(listOf(listOf(rom.absolutePath to newPath("psx/game.chd"))), rewrites)
    }

    @Test
    fun `a loose cue takes the tracks it lists`() = runTest {
        val cue = write("psx/Game.cue", "FILE \"Game (Track 1).bin\" BINARY\nFILE \"Game (Track 2).bin\" BINARY\n")
        write("psx/Game (Track 1).bin")
        write("psx/Game (Track 2).bin")
        write("psx/Other.bin")
        add(createGameEntity(localPath = cue.absolutePath, platformSlug = "psx"))

        useCase(oldDir.absolutePath, newDir.absolutePath)

        listOf("Game.cue", "Game (Track 1).bin", "Game (Track 2).bin").forEach {
            assertTrue(it, File(newPath("psx/$it")).exists())
            assertFalse(it, File(oldDir, "psx/$it").exists())
        }
        assertTrue(File(oldDir, "psx/Other.bin").exists())
        assertEquals(3, rewrites.single().size)
    }

    @Test
    fun `a game in its own folder moves the whole folder`() = runTest {
        val m3u = write("psx/Final Fantasy VII/Final Fantasy VII.m3u")
        write("psx/Final Fantasy VII/Disc 1.chd")
        write("psx/Final Fantasy VII/Disc 2.chd")
        add(createGameEntity(localPath = m3u.absolutePath, platformSlug = "psx"))

        useCase(oldDir.absolutePath, newDir.absolutePath)

        assertTrue(File(newPath("psx/Final Fantasy VII/Disc 2.chd")).exists())
        assertFalse(File(oldDir, "psx/Final Fantasy VII").exists())
        assertEquals(
            listOf(File(oldDir, "psx/Final Fantasy VII").absolutePath to newPath("psx/Final Fantasy VII")),
            rewrites.single()
        )
    }

    @Test
    fun `add-ons outside the game's folder move with it and music stays`() = runTest {
        val rom = write("psx/game.chd")
        val update = write("psx/extcontent/game-update.bin")
        coEvery { gameFileDao.getFilesForGame(1L) } returns listOf(
            GameFileEntity(gameId = 1L, fileName = "u", filePath = "u", category = "update", fileSize = 1, localPath = update.absolutePath),
            GameFileEntity(gameId = 1L, fileName = "s", filePath = "s", category = "soundtrack", fileSize = 1,
                localPath = File(tempFolder.root, "music/track.mp3").apply { writeText("m") }.absolutePath)
        )
        add(createGameEntity(localPath = rom.absolutePath, platformSlug = "psx"))

        useCase(oldDir.absolutePath, newDir.absolutePath)

        assertTrue(File(newPath("psx/extcontent/game-update.bin")).exists())
        assertTrue(File(tempFolder.root, "music/track.mp3").exists())
        assertEquals(2, rewrites.single().size)
    }

    @Test
    fun `a game in its platform's own folder stays put`() = runTest {
        val rom = File(customDir, "game.chd").apply { writeText("x") }
        add(createGameEntity(localPath = rom.absolutePath, platformId = CUSTOM_PLATFORM))

        val result = useCase(oldDir.absolutePath, newDir.absolutePath)

        assertTrue(rom.exists())
        assertEquals(1, result.skipped)
        assertTrue(rewrites.isEmpty())
    }

    @Test
    fun `a game left under the old root by a platform with its own folder moves`() = runTest {
        val rom = write("psx/game.chd")
        add(createGameEntity(localPath = rom.absolutePath, platformId = CUSTOM_PLATFORM))

        val result = useCase(oldDir.absolutePath, newDir.absolutePath)

        assertEquals(1, result.migrated)
        assertFalse(rom.exists())
    }

    @Test
    fun `a game already under the new root is counted without copying`() = runTest {
        val moved = File(newDir, "psx/game.chd").apply { parentFile?.mkdirs(); writeText("x") }
        add(createGameEntity(localPath = moved.absolutePath))

        val result = useCase(oldDir.absolutePath, newDir.absolutePath)

        assertEquals(1, result.migrated)
        assertTrue(rewrites.isEmpty())
    }

    @Test
    fun `missing files are skipped and their paths cleared`() = runTest {
        val missingPath = File(oldDir, "psx/missing.chd").absolutePath
        add(createGameEntity(localPath = missingPath))

        val result = useCase(oldDir.absolutePath, newDir.absolutePath)

        assertEquals(0, result.migrated)
        assertEquals(1, result.skipped)
        coVerify { gameRepository.clearLocalPathIfGenuinelyAbsent(1L, missingPath) }
    }

    @Test
    fun `preferences point at the new root on completion`() = runTest {
        useCase(oldDir.absolutePath, newDir.absolutePath)

        coVerify { preferencesRepository.setRomStoragePath(newDir.absolutePath) }
    }

    @Test
    fun `progress is reported per game`() = runTest {
        add(createGameEntity(id = 1, localPath = write("psx/game1.chd").absolutePath, title = "Game 1"))
        add(createGameEntity(id = 2, localPath = write("snes/game2.sfc").absolutePath, platformId = 2L, title = "Game 2"))

        val progress = mutableListOf<Triple<Int, Int, String>>()
        useCase(oldDir.absolutePath, newDir.absolutePath) { current, total, title ->
            progress.add(Triple(current, total, title))
        }

        assertEquals(listOf(Triple(0, 2, ""), Triple(1, 2, "Game 1"), Triple(2, 2, "Game 2")), progress)
    }

    @Test
    fun `games with no local path are skipped`() = runTest {
        add(createGameEntity(localPath = null))

        val result = useCase(oldDir.absolutePath, newDir.absolutePath)

        assertEquals(1, result.skipped)
    }

    private fun createGameEntity(
        id: Long = 1L,
        localPath: String? = null,
        platformId: Long = 1L,
        platformSlug: String = "psx",
        title: String = "Test Game"
    ) = GameEntity(
        id = id,
        platformId = platformId,
        platformSlug = platformSlug,
        title = title,
        sortTitle = title.lowercase(),
        localPath = localPath,
        rommId = null,
        igdbId = null,
        source = GameSource.LOCAL_ONLY
    )

    private companion object {
        const val CUSTOM_PLATFORM = 3L
    }
}
