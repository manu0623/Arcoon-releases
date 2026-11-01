package com.nendo.argosy.ui.screens.syncmonitor

import com.nendo.argosy.data.remote.romm.PlatformSyncState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncMonitorUiStateTest {

    private fun row(
        id: Long,
        state: PlatformSyncState = PlatformSyncState.IDLE,
        done: Int = 0,
        total: Int = 0,
        syncEnabled: Boolean = true,
        games: Int = 0,
        downloaded: Int = 0
    ) = SyncMonitorRow(
        platformId = id,
        name = "p$id",
        slug = "p$id",
        syncEnabled = syncEnabled,
        games = games,
        downloaded = downloaded,
        state = state,
        gamesDone = done,
        gamesTotal = total
    )

    @Test
    fun `active index points at the syncing row across both groups`() {
        val state = SyncMonitorUiState(
            enabledRows = listOf(
                row(1, PlatformSyncState.DONE),
                row(2, PlatformSyncState.SYNCING)
            ),
            disabledRows = listOf(row(3, syncEnabled = false))
        )
        assertEquals(1, state.activeIndex)
        assertEquals(3, state.rows.size)
    }

    @Test
    fun `a resumed platform counts as finished for the header`() {
        val state = SyncMonitorUiState(
            enabledRows = listOf(
                row(1, PlatformSyncState.ALREADY_SYNCED),
                row(2, PlatformSyncState.FAILED),
                row(3, PlatformSyncState.QUEUED)
            )
        )
        assertEquals(2, state.platformsDone)
        assertEquals(1, state.failedCount)
    }

    @Test
    fun `pass fraction reads the syncing platform and not the rows finished before it`() {
        val state = SyncMonitorUiState(
            enabledRows = listOf(
                row(1, PlatformSyncState.DONE),
                row(2, PlatformSyncState.SYNCING, done = 50, total = 100),
                row(3, PlatformSyncState.QUEUED),
                row(4, PlatformSyncState.QUEUED)
            ),
            disabledRows = listOf(row(5, syncEnabled = false))
        )
        assertEquals(0.5f, state.passFraction, 0.0001f)
    }

    @Test
    fun `pass fraction stays in the unit range and survives a platform reporting no total`() {
        assertEquals(0f, SyncMonitorUiState().passFraction, 0.0001f)

        val overrun = SyncMonitorUiState(
            enabledRows = listOf(row(1, PlatformSyncState.SYNCING, done = 500, total = 100))
        )
        assertEquals(1f, overrun.passFraction, 0.0001f)

        val noTotal = SyncMonitorUiState(
            enabledRows = listOf(
                row(1, PlatformSyncState.DONE),
                row(2, PlatformSyncState.SYNCING, done = 7, total = 0)
            )
        )
        assertEquals(0f, noTotal.passFraction, 0.0001f)
    }

    @Test
    fun `one platform syncing does not read against results left by earlier passes`() {
        val state = SyncMonitorUiState(
            enabledRows = listOf(
                row(1, PlatformSyncState.DONE),
                row(2, PlatformSyncState.DONE),
                row(3, PlatformSyncState.SYNCING, done = 10, total = 40)
            )
        )
        assertEquals(0.25f, state.passFraction, 0.0001f)
    }

    @Test
    fun `library totals sum both groups because disabled platforms still hold games`() {
        val state = SyncMonitorUiState(
            enabledRows = listOf(row(1, games = 100, downloaded = 20)),
            disabledRows = listOf(row(2, syncEnabled = false, games = 40, downloaded = 5))
        )
        assertEquals(140, state.totalGames)
        assertEquals(25, state.totalDownloaded)
    }

    @Test
    fun `a platform cannot be synced while the library pass owns the queue`() {
        val base = SyncMonitorUiState(enabledRows = listOf(row(1)), focusedIndex = 0)
        assertTrue(base.canSyncFocused)
        assertFalse(base.copy(libraryBusy = true).canSyncFocused)
        assertFalse(base.copy(busyPlatformIds = setOf(1L)).canSyncFocused)
        assertFalse(base.copy(isConnected = false).canSyncFocused)
    }

    @Test
    fun `a second platform can be queued while another one syncs`() {
        val state = SyncMonitorUiState(
            isSyncing = true,
            enabledRows = listOf(
                row(1, state = PlatformSyncState.SYNCING),
                row(2)
            ),
            busyPlatformIds = setOf(1L),
            focusedIndex = 1
        )
        assertTrue(state.canSyncFocused)
        assertFalse(state.canSyncRow(state.enabledRows[0]))
        assertTrue(state.canSyncAll)
    }

    @Test
    fun `a disabled platform is never offered a sync`() {
        val state = SyncMonitorUiState(
            disabledRows = listOf(row(1, syncEnabled = false)),
            focusedIndex = 0
        )
        assertFalse(state.canSyncFocused)
    }
}
