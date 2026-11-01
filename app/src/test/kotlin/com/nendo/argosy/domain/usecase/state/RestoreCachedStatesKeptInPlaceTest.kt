package com.nendo.argosy.domain.usecase.state

import com.nendo.argosy.data.emulator.ArchiveRomNaming
import com.nendo.argosy.data.emulator.EmulatorDetector
import com.nendo.argosy.data.emulator.EmulatorRegistry
import com.nendo.argosy.data.emulator.LibretroStatePathResolver
import com.nendo.argosy.libretro.LibretroStateSlots
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.local.entity.StateCacheEntity
import com.nendo.argosy.data.repository.StateCacheManager
import com.nendo.argosy.data.sync.StateClaim
import com.nendo.argosy.data.sync.StateOwnershipTracker
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class RestoreCachedStatesKeptInPlaceTest {

    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun `a live state that could not be moved aside is never overwritten by the restore`() = runTest {
        val liveDir = temp.newFolder("live")
        val romFile = File("/roms/snes/Game.sfc")
        val stateName = LibretroStateSlots.fileName(ArchiveRomNaming.liveBaseName(romFile, "snes"), 1)
        val live = File(liveDir, stateName).apply { writeText("live progress") }
        val cached = File(temp.newFolder("cache"), stateName).apply { writeText("older cache") }

        val game = mockk<GameEntity>(relaxed = true) {
            every { localPath } returns romFile.absolutePath
            every { platformSlug } returns "snes"
        }
        val state = mockk<StateCacheEntity>(relaxed = true) { every { slotNumber } returns 1 }
        val gameDao = mockk<GameDao> { coEvery { getById(1L) } returns game }
        val detector = mockk<EmulatorDetector> {
            every { getByPackage(EmulatorRegistry.BUILTIN_PACKAGE) } returns EmulatorRegistry.getById(EmulatorRegistry.BUILTIN_ID)
        }
        val stateCacheManager = mockk<StateCacheManager>(relaxed = true) {
            coEvery { getStatesForChannelAndCore(1L, null, any()) } returns listOf(state)
            every { getCacheFile(state) } returns cached
            every { displaceLiveState(any()) } returns false
        }
        val tracker = mockk<StateOwnershipTracker>(relaxed = true) {
            coEvery { claim(any(), any()) } returns StateClaim.Mine
        }
        val pathResolver = mockk<LibretroStatePathResolver> {
            coEvery { liveStateBaseDir(1L) } returns liveDir
        }

        val useCase = RestoreCachedStatesUseCase(
            stateCacheManager = stateCacheManager,
            gameDao = gameDao,
            emulatorSaveConfigDao = mockk(relaxed = true),
            emulatorDetector = detector,
            coreVersionExtractor = mockk(relaxed = true),
            retroArchConfigParser = mockk(relaxed = true),
            retroArchPathResolver = mockk(relaxed = true),
            libretroStatePathResolver = pathResolver,
            stateOwnershipTracker = tracker
        )

        useCase(gameId = 1L, channelName = null, emulatorPackage = EmulatorRegistry.BUILTIN_PACKAGE, coreId = "snes9x")

        assertEquals("live progress", live.readText())
    }
}
