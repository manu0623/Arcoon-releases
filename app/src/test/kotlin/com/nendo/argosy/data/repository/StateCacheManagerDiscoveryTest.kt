package com.nendo.argosy.data.repository

import android.content.Context
import com.nendo.argosy.data.emulator.CoreVersionExtractor
import com.nendo.argosy.data.emulator.EmulatorRegistry
import com.nendo.argosy.data.emulator.LibretroStatePathResolver
import com.nendo.argosy.data.emulator.RetroArchConfigParser
import com.nendo.argosy.data.emulator.RetroArchPathResolver
import com.nendo.argosy.data.local.dao.EmulatorSaveConfigDao
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.PendingSyncQueueDao
import com.nendo.argosy.data.local.dao.SaveCacheDao
import com.nendo.argosy.data.local.dao.SaveSyncDao
import com.nendo.argosy.data.local.dao.StateCacheDao
import com.nendo.argosy.data.local.dao.StateTombstoneDao
import com.nendo.argosy.data.preferences.SyncPreferencesRepository
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.sync.StateOwnershipTracker
import com.nendo.argosy.data.sync.SyncPayloadCodec
import com.nendo.argosy.libretro.LibretroStateSlots
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * The built-in core is launched with the entry extracted from a zip, so it names its states after
 * that entry. Discovery reads the game's stored path, which is the zip, and has to reach the same
 * name or the states never enter the cache.
 */
class StateCacheManagerDiscoveryTest {

    @Rule
    @JvmField
    val temp = TemporaryFolder()

    private lateinit var statesDir: File
    private lateinit var manager: StateCacheManager

    private val emulatorSaveConfigDao = mockk<EmulatorSaveConfigDao>(relaxed = true)
    private val libretroStatePathResolver = mockk<LibretroStatePathResolver>(relaxed = true)

    @Before
    fun setUp() {
        statesDir = temp.newFolder("states")
        coEvery { emulatorSaveConfigDao.getByEmulator(any()) } returns null
        coEvery { libretroStatePathResolver.liveStateBaseDir(any<Long>()) } returns statesDir
        manager = StateCacheManager(
            context = mockk<Context>(relaxed = true).also { every { it.filesDir } returns temp.root },
            gameDao = mockk<GameDao>(relaxed = true),
            stateCacheDao = mockk<StateCacheDao>(relaxed = true),
            stateTombstoneDao = mockk<StateTombstoneDao>(relaxed = true),
            saveCacheDao = mockk<SaveCacheDao>(relaxed = true),
            saveSyncDao = mockk<SaveSyncDao>(relaxed = true),
            pendingSyncQueueDao = mockk<PendingSyncQueueDao>(relaxed = true),
            emulatorSaveConfigDao = emulatorSaveConfigDao,
            preferencesRepository = mockk<UserPreferencesRepository>(relaxed = true),
            syncPreferencesRepository = mockk<SyncPreferencesRepository>(relaxed = true),
            coreVersionExtractor = mockk<CoreVersionExtractor>(relaxed = true),
            retroArchConfigParser = mockk<RetroArchConfigParser>(relaxed = true),
            retroArchPathResolver = mockk<RetroArchPathResolver>(relaxed = true),
            libretroStatePathResolver = libretroStatePathResolver,
            saveSyncApiClient = mockk(relaxed = true),
            payloadCodec = mockk<SyncPayloadCodec>(relaxed = true),
            attributionRepository = mockk(relaxed = true),
            stateOwnershipTracker = mockk<StateOwnershipTracker>(relaxed = true)
        )
    }

    private fun zipWith(archiveName: String, entryName: String): File {
        val archive = temp.newFile(archiveName)
        ZipOutputStream(archive.outputStream()).use { out ->
            out.putNextEntry(ZipEntry(entryName))
            out.write(byteArrayOf(1, 2, 3))
            out.closeEntry()
        }
        return archive
    }

    private fun stateFile(baseName: String, slot: Int): File =
        File(statesDir, LibretroStateSlots.fileName(baseName, slot)).apply { writeBytes(byteArrayOf(9)) }

    private suspend fun discoverBuiltin(rom: File, platform: String) =
        manager.discoverStatesForGame(
            gameId = 1L,
            emulatorId = EmulatorRegistry.BUILTIN_ID,
            romPath = rom.absolutePath,
            platformId = platform
        )

    @Test
    fun `states named after the zip entry are found for a zipped rom`() = runTest {
        val zip = zipWith("Game.zip", "Game (USA).gba")
        stateFile("Game (USA)", 1)
        stateFile("Game (USA)", LibretroStateSlots.AUTO_SLOT)

        val found = discoverBuiltin(zip, "gba")

        assertEquals(setOf(LibretroStateSlots.AUTO_SLOT, 1), found.map { it.slotNumber }.toSet())
    }

    @Test
    fun `states written under the archive name are still found when none carry the entry name`() = runTest {
        val zip = zipWith("Game.zip", "Game (USA).gba")
        stateFile("Game", 2)

        val found = discoverBuiltin(zip, "gba")

        assertEquals(listOf(2), found.map { it.slotNumber })
    }

    @Test
    fun `the entry name wins when both names have states`() = runTest {
        val zip = zipWith("Game.zip", "Game (USA).gba")
        stateFile("Game (USA)", 3)
        stateFile("Game", 4)

        val found = discoverBuiltin(zip, "gba")

        assertEquals(listOf(3), found.map { it.slotNumber })
    }

    @Test
    fun `a zip that is the rom keeps the archive name`() = runTest {
        val zip = zipWith("sf2.zip", "sf2.01")
        stateFile("sf2", 1)

        val found = discoverBuiltin(zip, "arcade")

        assertEquals(listOf(1), found.map { it.slotNumber })
    }

    @Test
    fun `a bare rom is found under its own name`() = runTest {
        val rom = temp.newFile("Game (USA).gba").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        stateFile("Game (USA)", 5)

        val found = discoverBuiltin(rom, "gba")

        assertEquals(listOf(5), found.map { it.slotNumber })
    }
}
