package com.nendo.argosy.ui.screens.syncmonitor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nendo.argosy.core.input.SoundType
import com.nendo.argosy.data.local.entity.PlatformEntity
import com.nendo.argosy.data.preferences.SyncPreferencesRepository
import com.nendo.argosy.data.remote.romm.PlatformSyncRow
import com.nendo.argosy.data.remote.romm.PlatformSyncState
import com.nendo.argosy.data.remote.romm.RomMRepository
import com.nendo.argosy.data.remote.romm.SyncProgress
import com.nendo.argosy.data.repository.GameRepository
import com.nendo.argosy.data.repository.PlatformRepository
import com.nendo.argosy.data.repository.SaveSyncRepository
import com.nendo.argosy.data.sync.PlatformSyncQueue
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SyncMonitorViewModel @Inject constructor(
    private val romMRepository: RomMRepository,
    private val platformSyncQueue: PlatformSyncQueue,
    private val platformRepository: PlatformRepository,
    private val gameRepository: GameRepository,
    private val saveSyncRepository: SaveSyncRepository,
    private val syncPreferencesRepository: SyncPreferencesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SyncMonitorUiState())
    val uiState: StateFlow<SyncMonitorUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val library = combine(
                platformRepository.observeAllPlatforms(),
                gameRepository.observeCountsByPlatform(),
                gameRepository.observeDownloadedCountsByPlatform(),
                saveSyncRepository.observeSaveCountsByPlatform(),
                romMRepository.syncProgress
            ) { platforms, games, downloaded, saves, progress ->
                LibrarySnapshot(platforms.filter { it.id >= 0 }, games, downloaded, saves, progress)
            }
            val queue = combine(
                platformSyncQueue.isLibraryBusy,
                platformSyncQueue.busyPlatformIds
            ) { libraryBusy, busyIds -> libraryBusy to busyIds }

            combine(library, queue) { snapshot, (libraryBusy, busyIds) ->
                val activity = snapshot.progress.platforms.associateBy { it.platformId }
                val rows = snapshot.platforms.map { platform ->
                    buildRow(platform, snapshot, activity[platform.id], busyIds)
                }
                Triple(rows, snapshot.progress.isSyncing, libraryBusy to busyIds)
            }.collect { (rows, isSyncing, queueState) ->
                val (libraryBusy, busyIds) = queueState
                _uiState.update { state ->
                    val enabled = rows.filter { it.syncEnabled }
                    val disabled = rows.filterNot { it.syncEnabled }
                    val ordered = enabled + disabled
                    val active = ordered.indexOfFirst { it.state == PlatformSyncState.SYNCING }
                    state.copy(
                        isSyncing = isSyncing,
                        libraryBusy = libraryBusy,
                        busyPlatformIds = busyIds,
                        enabledRows = enabled,
                        disabledRows = disabled,
                        focusedIndex = resolveFocus(state, ordered, active)
                    )
                }
            }
        }
        viewModelScope.launch {
            syncPreferencesRepository.preferences.collect { prefs ->
                _uiState.update {
                    it.copy(
                        lastSyncedAt = prefs.lastRommSync,
                        isConnected = romMRepository.isConnected()
                    )
                }
            }
        }
    }

    private data class LibrarySnapshot(
        val platforms: List<PlatformEntity>,
        val games: Map<Long, Int>,
        val downloaded: Map<Long, Int>,
        val saves: Map<Long, Int>,
        val progress: SyncProgress
    )

    private fun buildRow(
        platform: PlatformEntity,
        snapshot: LibrarySnapshot,
        activity: PlatformSyncRow?,
        busyIds: Set<Long>
    ): SyncMonitorRow {
        val reported = activity?.state ?: PlatformSyncState.IDLE
        val state = if (reported == PlatformSyncState.IDLE && platform.id in busyIds) {
            PlatformSyncState.QUEUED
        } else {
            reported
        }
        return SyncMonitorRow(
            platformId = platform.id,
            name = platform.name,
            slug = platform.slug,
            syncEnabled = platform.syncEnabled,
            games = snapshot.games[platform.id] ?: 0,
            downloaded = snapshot.downloaded[platform.id] ?: 0,
            withSaves = snapshot.saves[platform.id] ?: 0,
            state = state,
            gamesDone = activity?.gamesDone ?: 0,
            gamesTotal = activity?.gamesTotal ?: 0,
            added = activity?.added ?: 0,
            updated = activity?.updated ?: 0,
            removed = activity?.removed ?: 0,
            error = activity?.error
        )
    }

    /**
     * Following moves focus with the sync; a user who has taken over keeps their row unless it
     * has gone out of range under them.
     */
    private fun resolveFocus(
        state: SyncMonitorUiState,
        rows: List<SyncMonitorRow>,
        activeIndex: Int
    ): Int = when {
        rows.isEmpty() -> state.focusedIndex
        state.followActive && activeIndex >= 0 -> activeIndex
        else -> state.focusedIndex.coerceIn(0, rows.lastIndex)
    }

    private fun moveFocus(delta: Int): Boolean {
        var moved = false
        _uiState.update { state ->
            if (state.rows.isEmpty()) return@update state
            val next = (state.focusedIndex + delta).coerceIn(0, state.rows.lastIndex)
            moved = next != state.focusedIndex
            state.copy(focusedIndex = next, followActive = next == state.activeIndex)
        }
        return moved
    }

    fun focusRow(index: Int) {
        _uiState.update { state ->
            if (index !in state.rows.indices) return@update state
            state.copy(focusedIndex = index, followActive = index == state.activeIndex)
        }
    }

    fun syncAll() {
        if (!_uiState.value.canSyncAll) return
        platformSyncQueue.enqueueLibrary()
        _uiState.update { it.copy(followActive = true) }
    }

    /**
     * Syncs one platform, enabling it first when it was excluded. A platform turned back on holds
     * no games until it is synced, so enabling several queues one job each and they run in turn.
     */
    fun activateFocusedRow() {
        val state = _uiState.value
        val row = state.focusedRow ?: return
        if (!row.syncEnabled) {
            viewModelScope.launch {
                platformRepository.updateSyncEnabled(row.platformId, true)
                if (!_uiState.value.isConnected) return@launch
                platformSyncQueue.enqueuePlatformAfterLibrary(row.platformId, row.name)
            }
            return
        }
        if (!state.canSyncFocused) return
        platformSyncQueue.enqueuePlatform(row.platformId, row.name)
    }

    fun createInputHandler(onBack: () -> Unit): InputHandler = object : InputHandler {
        override fun onUp(): InputResult =
            if (moveFocus(-1)) InputResult.HANDLED else InputResult.handled(SoundType.BOUNDARY)

        override fun onDown(): InputResult =
            if (moveFocus(1)) InputResult.HANDLED else InputResult.handled(SoundType.BOUNDARY)

        override fun onConfirm(): InputResult {
            val state = _uiState.value
            val row = state.focusedRow ?: return InputResult.UNHANDLED
            if (!row.syncEnabled || state.canSyncFocused) {
                activateFocusedRow()
                return InputResult.HANDLED
            }
            return InputResult.handled(SoundType.BOUNDARY)
        }

        override fun onSecondaryAction(): InputResult {
            if (!_uiState.value.canSyncAll) return InputResult.handled(SoundType.BOUNDARY)
            syncAll()
            return InputResult.HANDLED
        }

        override fun onBack(): InputResult {
            onBack()
            return InputResult.HANDLED
        }
    }
}
