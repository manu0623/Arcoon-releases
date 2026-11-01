package com.nendo.argosy.data.repository

import android.content.Context
import com.nendo.argosy.data.download.DownloadCompletionEvent
import com.nendo.argosy.data.download.DownloadManager
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.sync.RAPatchBackfillWorker
import com.nendo.argosy.util.Logger
import com.nendo.argosy.util.SafeCoroutineScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "RAPatchDownloadObserver"

/**
 * Caches RetroAchievements definitions for a game as soon as its download completes, so its first
 * launch can arm casual achievements without a connection. A disc of a game that already has
 * cached definitions fetches nothing; a fetch that has to wait hands the game to the backfill.
 */
@Singleton
class RAPatchDownloadObserver @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloadManager: DownloadManager,
    private val raRepository: dagger.Lazy<RetroAchievementsRepository>,
    private val gameDao: GameDao
) {
    private val scope = SafeCoroutineScope(Dispatchers.IO, TAG)

    fun start() {
        scope.launch {
            downloadManager.completionEvents.collect { event ->
                scope.launch { cachePatchFor(event) }
            }
        }
    }

    private suspend fun cachePatchFor(event: DownloadCompletionEvent) {
        val raGameId = gameDao.getById(event.gameId)?.effectiveRaId ?: return
        val repository = raRepository.get()
        if (!repository.isLoggedIn()) return
        if (event.isDiscDownload && repository.getCachedPatchData(raGameId) != null) return

        when (repository.fetchPatchData(raGameId)) {
            is RAPatchFetchResult.Fetched ->
                Logger.debug(TAG, "Cached RA definitions for game ${event.gameId} (RA $raGameId)")
            RAPatchFetchResult.RetryLater -> RAPatchBackfillWorker.schedule(context)
            RAPatchFetchResult.Unavailable ->
                Logger.debug(TAG, "No RA definitions for game ${event.gameId} (RA $raGameId)")
        }
    }
}
