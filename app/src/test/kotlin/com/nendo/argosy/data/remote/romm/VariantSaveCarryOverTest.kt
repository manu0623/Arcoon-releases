package com.nendo.argosy.data.remote.romm

import com.nendo.argosy.data.emulator.EmulatorRegistry
import com.nendo.argosy.data.emulator.LibretroSavePathResolver
import com.nendo.argosy.data.local.dao.EmulatorConfigDao
import com.nendo.argosy.data.local.entity.EmulatorSaveConfigEntity
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.local.entity.GameFileEntity
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.data.repository.EmulatorSaveConfigRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class VariantSaveCarryOverTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var emulatorConfigDao: EmulatorConfigDao
    private lateinit var saveConfigRepository: EmulatorSaveConfigRepository
    private lateinit var savePathResolver: LibretroSavePathResolver
    private lateinit var carryOver: VariantSaveCarryOver
    private lateinit var platformBase: File

    private val owner = GameEntity(
        id = 2L,
        platformId = 5L,
        platformSlug = "gb",
        title = "Blue",
        sortTitle = "blue",
        localPath = null,
        rommId = 200L,
        igdbId = 1511L,
        source = GameSource.ROMM_SYNCED
    )

    @Before
    fun setup() {
        platformBase = tmp.newFolder("saves", "gb")
        emulatorConfigDao = mockk(relaxed = true)
        saveConfigRepository = mockk(relaxed = true)
        savePathResolver = mockk(relaxed = true)
        coEvery { emulatorConfigDao.getSavePathForGame(any()) } returns null
        coEvery { saveConfigRepository.getByEmulator(any()) } returns null
        coEvery { savePathResolver.liveSaveBaseDir(any<Long>(), any()) } returns platformBase
        carryOver = VariantSaveCarryOver(emulatorConfigDao, saveConfigRepository, savePathResolver)
    }

    @Test
    fun `a variant save is copied into the platform save directory and the original stays`() = runBlocking {
        val source = write(platformBase, "variants/20/Blue (Germany).srm", "german")

        val copied = carryOver.carryOver(file(romDir = null), owner, emptySet())

        assertEquals(1, copied)
        assertEquals("german", File(platformBase, "Blue (Germany).srm").readText())
        assertTrue(source.exists())
    }

    @Test
    fun `the owner's per-game save path is the destination when it has one`() = runBlocking {
        val perGame = tmp.newFolder("per-game")
        coEvery { emulatorConfigDao.getSavePathForGame(2L) } returns perGame.absolutePath
        write(platformBase, "variants/20/Blue (Germany).srm", "german")

        carryOver.carryOver(file(romDir = null), owner, emptySet())

        assertEquals("german", File(perGame, "Blue (Germany).srm").readText())
        assertFalse(File(platformBase, "Blue (Germany).srm").exists())
    }

    @Test
    fun `a save already at the destination is never overwritten`() = runBlocking {
        write(platformBase, "variants/20/Blue (Germany).srm", "variant")
        write(platformBase, "Blue (Germany).srm", "existing")

        val copied = carryOver.carryOver(file(romDir = null), owner, emptySet())

        assertEquals(0, copied)
        assertEquals("existing", File(platformBase, "Blue (Germany).srm").readText())
    }

    @Test
    fun `a former game's per-game save path is searched for the variant directory`() = runBlocking {
        val formerDir = tmp.newFolder("former")
        coEvery { emulatorConfigDao.getSavePathForGame(1L) } returns formerDir.absolutePath
        write(formerDir, "variants/20/Blue (Germany).srm", "from former")

        val copied = carryOver.carryOver(file(romDir = null), owner, setOf(1L))

        assertEquals(1, copied)
        assertEquals("from former", File(platformBase, "Blue (Germany).srm").readText())
    }

    @Test
    fun `saves beside the rom resolve the platform base from the rom directory`() = runBlocking {
        coEvery { saveConfigRepository.getByEmulator(EmulatorRegistry.BUILTIN_ID) } returns
            EmulatorSaveConfigEntity(emulatorId = EmulatorRegistry.BUILTIN_ID, savePathPattern = "", isAutoDetected = false, savesBesideRom = true)

        carryOver.carryOver(file(romDir = "/roms/gb"), owner, emptySet())

        coVerify { savePathResolver.liveSaveBaseDir(platformId = 5L, besideRomDir = "/roms/gb") }
    }

    @Test
    fun `a failed copy does not stop the remaining files`() = runBlocking {
        write(platformBase, "variants/20/blocked/Blue.srm", "blocked")
        write(platformBase, "variants/20/Blue.rtc", "clock")
        write(platformBase, "blocked", "a file where a directory is needed")

        val copied = carryOver.carryOver(file(romDir = null), owner, emptySet())

        assertEquals(1, copied)
        assertEquals("clock", File(platformBase, "Blue.rtc").readText())
    }

    private fun write(base: File, relative: String, text: String): File =
        File(base, relative).apply {
            parentFile?.mkdirs()
            writeText(text)
        }

    private fun file(romDir: String?) = GameFileEntity(
        id = 20L,
        gameId = 2L,
        rommFileId = 1020L,
        romId = 200L,
        fileName = "Blue (Germany).gb",
        filePath = "gb/rom200",
        category = "game",
        fileSize = 1024L,
        localPath = romDir?.let { "$it/Blue (Germany).gb" },
        isLaunchTarget = true,
        versionGroup = "romm:200"
    )
}
