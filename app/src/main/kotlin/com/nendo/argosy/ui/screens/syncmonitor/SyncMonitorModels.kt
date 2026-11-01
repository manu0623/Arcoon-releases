package com.nendo.argosy.ui.screens.syncmonitor

import com.nendo.argosy.data.remote.romm.PlatformSyncState
import java.time.Instant

/**
 * One platform as this screen shows it: what the library holds for it, and what a running sync is
 * doing to it. The counts are local facts and stay readable with no server and no sync.
 */
data class SyncMonitorRow(
    val platformId: Long,
    val name: String,
    val slug: String,
    val syncEnabled: Boolean,
    val games: Int = 0,
    val downloaded: Int = 0,
    val withSaves: Int = 0,
    val state: PlatformSyncState = PlatformSyncState.IDLE,
    val gamesDone: Int = 0,
    val gamesTotal: Int = 0,
    val added: Int = 0,
    val updated: Int = 0,
    val removed: Int = 0,
    val error: String? = null
)

/**
 * [followActive] tracks the syncing row on its own until the user moves, at which point their
 * choice of row outranks it. Landing back on the active row hands following back, so the mode is
 * always something the user can see rather than something a timer decides.
 */
data class SyncMonitorUiState(
    val isSyncing: Boolean = false,
    val isConnected: Boolean = true,
    val libraryBusy: Boolean = false,
    val busyPlatformIds: Set<Long> = emptySet(),
    val enabledRows: List<SyncMonitorRow> = emptyList(),
    val disabledRows: List<SyncMonitorRow> = emptyList(),
    val focusedIndex: Int = 0,
    val followActive: Boolean = true,
    val lastSyncedAt: Instant? = null
) {
    val rows: List<SyncMonitorRow> get() = enabledRows + disabledRows

    val activeIndex: Int
        get() = rows.indexOfFirst { it.state == PlatformSyncState.SYNCING }

    val focusedRow: SyncMonitorRow? get() = rows.getOrNull(focusedIndex)

    val hasRows: Boolean get() = rows.isNotEmpty()

    val totalGames: Int get() = rows.sumOf { it.games }

    val totalDownloaded: Int get() = rows.sumOf { it.downloaded }

    val platformsDone: Int
        get() = enabledRows.count {
            it.state == PlatformSyncState.DONE ||
                it.state == PlatformSyncState.ALREADY_SYNCED ||
                it.state == PlatformSyncState.FAILED
        }

    val failedCount: Int get() = enabledRows.count { it.state == PlatformSyncState.FAILED }

    /**
     * A platform can be asked for while another is running; the queue runs them in turn. Only a
     * library pass blocks one, because it covers every platform already.
     */
    fun canSyncRow(row: SyncMonitorRow): Boolean =
        isConnected &&
            row.syncEnabled &&
            !libraryBusy &&
            row.platformId !in busyPlatformIds

    val canSyncFocused: Boolean
        get() = focusedRow?.let { canSyncRow(it) } ?: false

    val canSyncAll: Boolean get() = isConnected && !libraryBusy

    /**
     * The syncing platform's own progress, not a count of finished rows; those survive earlier
     * passes and would read a single-platform sync against them.
     */
    val passFraction: Float
        get() {
            val active = rows.getOrNull(activeIndex) ?: return 0f
            if (active.gamesTotal <= 0) return 0f
            return (active.gamesDone.toFloat() / active.gamesTotal).coerceIn(0f, 1f)
        }
}
