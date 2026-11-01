package com.nendo.argosy.data.repository

import android.content.Context
import com.nendo.argosy.data.emulator.EmulatorRegistry
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.local.entity.StateCacheEntity
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.Instant

/**
 * RomM's web player lists a state only when its `emulator` names the core it runs, so a state made
 * by a libretro core is filed under that core. States answer this the way saves already do, or the
 * same game's two artifacts land under two different emulators on the server.
 */
class StateCacheManagerServerTagTest {

    private val gameDao = mockk<GameDao>()
    private val saveSyncApiClient = mockk<SaveSyncApiClient>()
    private val game = mockk<GameEntity>()
    private lateinit var manager: StateCacheManager

    @Before
    fun setUp() {
        every { game.id } returns GAME_ID
        coEvery { gameDao.getById(GAME_ID) } returns game
        manager = StateCacheManager(
            context = mockk<Context>(relaxed = true),
            gameDao = gameDao,
            stateCacheDao = mockk(relaxed = true),
            stateTombstoneDao = mockk(relaxed = true),
            saveCacheDao = mockk(relaxed = true),
            saveSyncDao = mockk(relaxed = true),
            pendingSyncQueueDao = mockk(relaxed = true),
            emulatorSaveConfigDao = mockk(relaxed = true),
            preferencesRepository = mockk(relaxed = true),
            syncPreferencesRepository = mockk(relaxed = true),
            coreVersionExtractor = mockk(relaxed = true),
            retroArchConfigParser = mockk(relaxed = true),
            retroArchPathResolver = mockk(relaxed = true),
            libretroStatePathResolver = mockk(relaxed = true),
            saveSyncApiClient = saveSyncApiClient,
            payloadCodec = mockk(relaxed = true),
            attributionRepository = mockk(relaxed = true),
            stateOwnershipTracker = mockk(relaxed = true)
        )
    }

    @Test
    fun `built-in state is filed under the core the game resolves to`() = runTest {
        resolvesCore(EmulatorRegistry.BUILTIN_ID, "snes9x")

        val tag = manager.serverEmulatorTag(state(EmulatorRegistry.BUILTIN_ID, coreId = EmulatorRegistry.BUILTIN_ID))

        assertEquals("snes9x", tag)
    }

    @Test
    fun `legacy built-in id resolves as the built-in emulator`() = runTest {
        resolvesCore(EmulatorRegistry.BUILTIN_ID, "snes9x")

        val tag = manager.serverEmulatorTag(state(EmulatorRegistry.LEGACY_BUILTIN_ID, coreId = null))

        assertEquals("snes9x", tag)
    }

    @Test
    fun `retroarch state is filed under the configured core, not the platform default`() = runTest {
        resolvesCore("retroarch", "bsnes")

        val tag = manager.serverEmulatorTag(state("retroarch", coreId = "snes9x"))

        assertEquals("bsnes", tag)
    }

    @Test
    fun `n64 gles core is filed under the core RomM knows`() = runTest {
        resolvesCore(EmulatorRegistry.BUILTIN_ID, "mupen64plus_next_gles3")

        val tag = manager.serverEmulatorTag(state(EmulatorRegistry.BUILTIN_ID, coreId = EmulatorRegistry.BUILTIN_ID))

        assertEquals("mupen64plus_next", tag)
    }

    @Test
    fun `state keeps its emulator id when no core resolves`() = runTest {
        resolvesCore(EmulatorRegistry.BUILTIN_ID, null)

        val tag = manager.serverEmulatorTag(state(EmulatorRegistry.BUILTIN_ID, coreId = EmulatorRegistry.BUILTIN_ID))

        assertEquals(EmulatorRegistry.BUILTIN_ID, tag)
    }

    @Test
    fun `standalone state keeps its emulator id`() = runTest {
        resolvesCore("ppsspp", null)

        val tag = manager.serverEmulatorTag(state("ppsspp", coreId = "ppsspp"))

        assertEquals("ppsspp", tag)
    }

    private fun resolvesCore(emulatorId: String, coreId: String?) {
        coEvery { saveSyncApiClient.resolveCoreForGame(game, emulatorId) } returns coreId
    }

    private fun state(emulatorId: String, coreId: String?) = StateCacheEntity(
        gameId = GAME_ID,
        platformSlug = "snes",
        emulatorId = emulatorId,
        slotNumber = 1,
        cachedAt = Instant.EPOCH,
        stateSize = 0,
        cachePath = "unused",
        coreId = coreId
    )

    private companion object {
        const val GAME_ID = 7L
    }
}
