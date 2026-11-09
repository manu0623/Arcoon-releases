package com.nendo.argosy.data.sync

import com.nendo.argosy.data.download.DownloadManager
import com.nendo.argosy.util.Logger
import com.nendo.argosy.data.emulator.EmulatorResolver
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.repository.SaveInventoryUnavailableException
import com.nendo.argosy.data.repository.SaveSyncRepository
import com.nendo.argosy.util.SafeCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "SaveSyncDownloadObserver"

/** How long a first sync waits for RomM to connect before leaving it to the launch check. */
private const val CONNECT_WAIT_MS = 120_000L
private const val CONNECT_POLL_MS = 5_000L

/**
 * Brings a game's save from RomM onto the device as soon as the game is on it, like Steam Cloud:
 * whether it was downloaded through Arcoon, found by a storage scan or matched in a library sync.
 *
 * For a game this device has never synced ([CloudSaveLineage]): with no save of its own here, the
 * newest save in RomM is placed right away (its older saves kept as history); with a save of its
 * own, nothing is overwritten and the player is asked once at launch. A game RomM has no save for
 * becomes linked, so the first save made here is the one that is uploaded.
 */
@Singleton
class SaveSyncDownloadObserver @Inject constructor(
    private val downloadManager: DownloadManager,
    private val saveSyncRepository: dagger.Lazy<SaveSyncRepository>,
    private val gameDao: GameDao,
    private val emulatorResolver: EmulatorResolver,
    private val accountSwitchMarkerStore: com.nendo.argosy.data.preferences.AccountSwitchMarkerStore,
    private val lineage: CloudSaveLineage,
    private val userPreferencesRepository: UserPreferencesRepository
) {
    private val scope = SafeCoroutineScope(Dispatchers.IO, "SaveSyncDownloadObserver")
    private val locks = ConcurrentHashMap<Long, Mutex>()
    /** Games looked at this run, so the same one isn't checked against RomM over and over. */
    private val checkedThisRun = ConcurrentHashMap.newKeySet<Long>()

    fun start() {
        scope.launch {
            downloadManager.completionEvents.collect { event ->
                handleDownloadCompletion(event)
            }
        }
        scope.launch { watchInstalledGames() }
        Logger.debug(TAG, "Started observing download completions and installed games")
    }

    private suspend fun handleDownloadCompletion(
        event: com.nendo.argosy.data.download.DownloadCompletionEvent
    ) {
        if (event.isDiscDownload) {
            // A disc of a multi-disc game: the game itself is picked up once it counts as installed.
            Logger.debug(TAG, "handleDownloadCompletion: disc download for game ${event.gameId}; left to the install watcher")
            return
        }

        if (accountSwitchMarkerStore.isSwitching()) {
            Logger.info(TAG, "handleDownloadCompletion: skipped - account switch in progress for game ${event.gameId}")
            return
        }

        val game = gameDao.getById(event.gameId)
        if (game == null) {
            Logger.error(TAG, "handleDownloadCompletion: game ${event.gameId} not found")
            return
        }

        val emulatorId = emulatorResolver.getEmulatorIdForGame(event.gameId, game.platformId, game.platformSlug)
        if (emulatorId == null) {
            Logger.debug(TAG, "handleDownloadCompletion: no emulator for platform ${game.platformSlug}")
            return
        }

        lockFor(event.gameId).withLock {
            if (!lineage.isLinked(event.gameId)) {
                // Never synced here: the same first sync as any other way of installing it.
                firstSyncLocked(event.gameId, "downloaded")
                return
            }
            Logger.debug(TAG, "handleDownloadCompletion: triggering save sync for game ${event.gameId} with emulator $emulatorId")
            try {
                saveSyncRepository.get().syncSavesForNewDownload(event.gameId, event.rommId, emulatorId)
            } catch (e: Exception) {
                Logger.error(TAG, "handleDownloadCompletion: save sync failed", e)
            }
        }
    }

    /**
     * Every game that becomes installed, however that happened. The first look at start-up covers
     * the games already on the device, so a fresh install picks up the ROMs it finds.
     */
    private suspend fun watchInstalledGames() {
        var known: Set<Long>? = null
        gameDao.observeInstalledRommGameIds().distinctUntilChanged().collect { ids ->
            val now = ids.toSet()
            val previous = known
            known = now
            val added = if (previous == null) now else now - previous
            if (added.isEmpty()) return@collect
            val reason = if (previous == null) "on the device at start" else "newly installed"
            for (gameId in added) {
                if (lineage.isLinked(gameId)) continue
                lockFor(gameId).withLock { firstSyncLocked(gameId, reason) }
            }
        }
    }

    /** The first sync of one game this device never synced. Call with the game's lock held. */
    private suspend fun firstSyncLocked(gameId: Long, reason: String) {
        if (lineage.isLinked(gameId) || !checkedThisRun.add(gameId)) return
        if (!userPreferencesRepository.preferences.first().saveSyncEnabled) {
            checkedThisRun.remove(gameId)
            return
        }
        if (accountSwitchMarkerStore.isSwitching()) {
            checkedThisRun.remove(gameId)
            return
        }
        val game = gameDao.getById(gameId) ?: return
        val rommId = game.rommId ?: return
        if (game.localPath == null) return
        val emulatorId = emulatorResolver.getEmulatorIdForGame(gameId, game.platformId, game.platformSlug)
        if (emulatorId == null) {
            // No emulator set up yet: checked again at launch, once there is one.
            checkedThisRun.remove(gameId)
            return
        }
        val repo = saveSyncRepository.get()
        if (!awaitConnection(repo)) {
            Logger.info(TAG, "[SaveSync] FIRST_SYNC gameId=$gameId | RomM not connected; left to the launch check")
            checkedThisRun.remove(gameId)
            return
        }
        val cloud = try {
            repo.cloudSaveToProtect(gameId, null)
        } catch (e: SaveInventoryUnavailableException) {
            Logger.warn(TAG, "[SaveSync] FIRST_SYNC gameId=$gameId | RomM's saves can't be read (${e.message}); left to the launch check")
            checkedThisRun.remove(gameId)
            return
        }
        if (cloud == null) {
            lineage.markLinked(gameId, "RomM has no save for it yet")
            return
        }
        when (val local = repo.lookupSystemSave(game, emulatorId)) {
            is SaveLookup.Found -> Logger.info(
                TAG,
                "[SaveSync] FIRST_SYNC gameId=$gameId ($reason) | this device has its own save at ${local.path}; " +
                    "the player picks one at launch (cloud save ${cloud.id})"
            )
            is SaveLookup.Unreadable -> Logger.info(
                TAG,
                "[SaveSync] FIRST_SYNC gameId=$gameId ($reason) | save folder can't be read yet; left to the launch check"
            )
            SaveLookup.Absent -> {
                Logger.info(TAG, "[SaveSync] FIRST_SYNC gameId=$gameId ($reason) | placing the save from RomM (save ${cloud.id})")
                try {
                    repo.syncSavesForNewDownload(gameId, rommId, emulatorId)
                } catch (e: Exception) {
                    Logger.error(TAG, "[SaveSync] FIRST_SYNC gameId=$gameId | placing the save failed", e)
                    checkedThisRun.remove(gameId)
                }
            }
        }
    }

    /** True once RomM is connected, waiting a while for a connection still coming up. */
    private suspend fun awaitConnection(repo: SaveSyncRepository): Boolean {
        var waited = 0L
        while (repo.getApi() == null || repo.getDeviceId() == null) {
            if (waited >= CONNECT_WAIT_MS) return false
            delay(CONNECT_POLL_MS)
            waited += CONNECT_POLL_MS
        }
        return true
    }

    private fun lockFor(gameId: Long): Mutex = locks.getOrPut(gameId) { Mutex() }
}
