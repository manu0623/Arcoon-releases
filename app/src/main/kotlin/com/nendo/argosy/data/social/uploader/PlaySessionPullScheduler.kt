package com.nendo.argosy.data.social.uploader

import com.nendo.argosy.data.preferences.SyncPreferencesRepository
import com.nendo.argosy.data.remote.romm.ConnectionState
import com.nendo.argosy.data.remote.romm.RomMConnectionManager
import com.nendo.argosy.util.Logger
import com.nendo.argosy.util.SafeCoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "PlaySessionPullScheduler"

internal val PLAY_SESSION_PULL_INTERVAL: Duration = Duration.ofMinutes(15)

internal fun isPlaySessionPullDue(lastPull: Instant?, now: Instant): Boolean =
    lastPull == null || !lastPull.plus(PLAY_SESSION_PULL_INTERVAL).isAfter(now)

@Singleton
class PlaySessionPullScheduler internal constructor(
    private val pull: PlaySessionPull,
    private val syncPreferencesRepository: SyncPreferencesRepository,
    connectionManager: RomMConnectionManager,
    private val scope: CoroutineScope,
    private val clock: () -> Instant
) {
    @Inject constructor(
        pull: PlaySessionPull,
        syncPreferencesRepository: SyncPreferencesRepository,
        connectionManager: RomMConnectionManager
    ) : this(
        pull,
        syncPreferencesRepository,
        connectionManager,
        SafeCoroutineScope(Dispatchers.IO, TAG),
        { Instant.now() }
    )

    private var inFlight: Deferred<PlaySessionPull.Summary>? = null

    init {
        scope.launch {
            connectionManager.connectionState.collect { state ->
                if (state is ConnectionState.Connected) pullIfDue()
            }
        }
    }

    fun requestIfDue() {
        scope.launch { pullIfDue() }
    }

    /**
     * Waits up to [timeoutMs] for a pull to finish and answers its summary, or null when RomM
     * cannot be pulled from, the pull failed, or the wait ran out. A pull still running at the
     * timeout carries on in the background.
     */
    suspend fun pullWithin(timeoutMs: Long): PlaySessionPull.Summary? {
        if (!pull.canPull) return null
        return withTimeoutOrNull(timeoutMs) { awaitSharedPull() }
    }

    internal suspend fun pullIfDue(): PlaySessionPull.Summary? {
        if (!pull.canPull) return null
        val lastPull = syncPreferencesRepository.getRommPlaySessionLastPull()
        if (!isPlaySessionPullDue(lastPull, clock())) return null
        return awaitSharedPull()
    }

    private suspend fun awaitSharedPull(): PlaySessionPull.Summary? = try {
        sharedPull().await()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Logger.warn(TAG, "pull failed | ${e.message}")
        null
    }

    @Synchronized
    private fun sharedPull(): Deferred<PlaySessionPull.Summary> =
        inFlight?.takeIf { it.isActive } ?: scope.async { pull.run() }.also { inFlight = it }
}
