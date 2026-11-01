package com.nendo.argosy.data.repository

import android.content.Context
import com.nendo.argosy.data.remote.romm.RomMState
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class StateCacheManagerStateNameTest {

    private val manager = StateCacheManager(
        context = mockk<Context>(relaxed = true),
        gameDao = mockk(relaxed = true),
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
        saveSyncApiClient = mockk(relaxed = true),
        payloadCodec = mockk(relaxed = true),
        attributionRepository = mockk(relaxed = true),
        stateOwnershipTracker = mockk(relaxed = true)
    )

    @Test
    fun `a web player state outranks an older Argosy state`() {
        val browser = manager.parseStateFileTimestamp("Game [2026-10-23 13-29-53-473].state")
        val argosy = manager.parseStateFileTimestamp("Game [2026-10-19_15-00-58].state0")

        assertNotNull(browser)
        assertNotNull(argosy)
        assertTrue(browser!! > argosy!!)
    }

    @Test
    fun `an Argosy state outranks an older web player state`() {
        val browser = manager.parseStateFileTimestamp("Game [2026-10-19 15-00-57-999].state")
        val argosy = manager.parseStateFileTimestamp("Game [2026-10-19_15-00-58].state0")

        assertTrue(argosy!! > browser!!)
    }

    @Test
    fun `milliseconds order two states stamped in the same second`() {
        val later = manager.parseStateFileTimestamp("Game [2026-10-23_13-29-53-473].state")
        val earlier = manager.parseStateFileTimestamp("Game [2026-10-23_13-29-53-012].state")

        assertTrue(later!! > earlier!!)
    }

    @Test
    fun `a name without a capture time reads as none`() {
        assertNull(manager.parseStateFileTimestamp("Game.state"))
    }

    @Test
    fun `a web player state parses to its rom and the default slot`() {
        val parsed = manager.parseStateFileName("Game [2026-10-23 13-29-53-473].state")

        assertEquals("Game", parsed.romBaseName)
        assertEquals(null, parsed.channelName)
        assertEquals(0, parsed.slotNumber)
    }

    @Test
    fun `a server tagged channel state keeps its channel and slot`() {
        val parsed = manager.parseStateFileName("Game [2026-10-23_13-29-53-473].autosave.state2")

        assertEquals("Game", parsed.romBaseName)
        assertEquals("autosave", parsed.channelName)
        assertEquals(2, parsed.slotNumber)
    }

    @Test
    fun `a state with no capture time in its name ranks by the server time`() {
        val written = manager.serverStateWrittenAt(serverState("Game.state", "2026-10-23T04:29:53Z"))

        assertEquals(Instant.parse("2026-10-23T04:29:53Z"), written)
    }

    private fun serverState(fileName: String, updatedAt: String) = RomMState(
        id = 1L,
        romId = 2L,
        userId = 3L,
        emulator = "snes9x",
        fileName = fileName,
        updatedAt = updatedAt
    )
}
