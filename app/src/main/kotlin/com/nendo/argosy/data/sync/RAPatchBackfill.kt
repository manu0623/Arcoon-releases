package com.nendo.argosy.data.sync

import com.nendo.argosy.data.repository.RAPatchCacheStore
import com.nendo.argosy.data.repository.RAPatchFetchResult
import com.nendo.argosy.data.repository.RetroAchievementsRepository
import com.nendo.argosy.util.Logger
import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "RAPatchBackfill"

/**
 * Caches RetroAchievements definitions for installed games that have none, one `r=patch` request
 * at a time with [REQUEST_SPACING_MS] between requests. A game cached by another path while the
 * pass runs is skipped. The pass stops at the first answer that asks to wait.
 */
@Singleton
class RAPatchBackfill @Inject constructor(
    private val raRepository: RetroAchievementsRepository,
    private val patchCacheStore: RAPatchCacheStore
) {
    enum class Outcome { COMPLETE, RETRY_LATER }

    suspend fun run(): Outcome {
        val ownerUserId = raRepository.activeOwnerUserId()
        val pending = patchCacheStore.uncachedInstalledRaGameIds(ownerUserId)
        if (pending.isEmpty()) return Outcome.COMPLETE
        Logger.info(TAG, "Backfilling RA definitions for ${pending.size} installed games")

        var requested = 0
        for (raGameId in pending) {
            if (patchCacheStore.contains(raGameId, ownerUserId)) continue
            if (requested > 0) delay(REQUEST_SPACING_MS)
            requested++
            if (raRepository.fetchPatchData(raGameId) == RAPatchFetchResult.RetryLater) {
                Logger.info(TAG, "Backfill paused at RA game $raGameId after $requested requests")
                return Outcome.RETRY_LATER
            }
        }
        Logger.info(TAG, "Backfill finished after $requested requests")
        return Outcome.COMPLETE
    }

    companion object {
        const val REQUEST_SPACING_MS = 2_000L
    }
}
