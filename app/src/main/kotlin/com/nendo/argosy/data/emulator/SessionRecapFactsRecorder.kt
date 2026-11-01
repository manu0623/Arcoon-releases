package com.nendo.argosy.data.emulator

import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.PlaySessionDao
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.preferences.SessionPreferencesRepository
import com.nendo.argosy.data.remote.ra.RAWebUserSummary
import com.nendo.argosy.data.repository.RaMetadataSource
import com.nendo.argosy.data.repository.RetroAchievementsRepository.GameUnlocks
import com.nendo.argosy.util.Logger
import com.nendo.argosy.util.parseTimestamp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

data class SessionRecapFacts(
    val raGameId: Long?,
    val richPresence: String?,
    val richPresenceAt: Instant?
)

/**
 * The RetroAchievements facts a play session row carries for its recap: the RA game id, the last
 * rich-presence line seen while it ran, and for external emulators with no dated unlock source,
 * the unlocks between snapshots taken at launch, stored across process death, and at session end.
 */
@Singleton
class SessionRecapFactsRecorder @Inject constructor(
    private val richPresenceCapture: RichPresenceCapture,
    private val metadataSource: dagger.Lazy<RaMetadataSource>,
    private val playSessionDao: PlaySessionDao,
    private val gameDao: GameDao,
    private val sessionPreferences: SessionPreferencesRepository
) {
    private data class LaunchSnapshot(
        val gameId: Long,
        val startTime: Instant,
        val raGameId: Long,
        val unlocks: Deferred<GameUnlocks?>
    )

    private data class LaunchUnlocks(val raGameId: Long, val unlocks: GameUnlocks?)

    @Volatile private var launchSnapshot: LaunchSnapshot? = null

    /**
     * Resets the per-session capture and, for an external emulator whose game no dated source
     * covers, starts the launch-side unlock snapshot on [scope].
     */
    fun onSessionStarted(scope: CoroutineScope, gameId: Long, emulatorPackage: String, startTime: Instant) {
        richPresenceCapture.clear()
        launchSnapshot = null
        val builtin = isBuiltin(emulatorPackage)
        scope.launch {
            try {
                sessionPreferences.setRaLaunchSnapshot(null)
                if (builtin) return@launch
                val raGameId = gameDao.getById(gameId)?.let { it.effectiveRaId ?: it.raId } ?: return@launch
                val source = metadataSource.get()
                if (!source.needsUnlockSnapshots(raGameId)) return@launch
                val unlocks = scope.async {
                    guardedSnapshot(source, raGameId)?.also { taken ->
                        storeLaunchSnapshot(LaunchUnlockSnapshot(gameId, startTime, raGameId, taken))
                    }
                }
                launchSnapshot = LaunchSnapshot(gameId, startTime, raGameId, unlocks)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.debug(TAG, "Launch unlock snapshot skipped for game $gameId: ${e.message}")
            }
        }
    }

    fun factsFor(
        game: GameEntity?,
        emulatorPackage: String,
        start: Instant,
        end: Instant
    ): SessionRecapFacts {
        val raGameId = game?.let { it.effectiveRaId ?: it.raId }
        if (!isBuiltin(emulatorPackage)) return SessionRecapFacts(raGameId, null, null)
        val captured = richPresenceCapture.take(start, end)
            ?.takeIf { raGameId == null || it.raGameId == raGameId }
        return SessionRecapFacts(
            raGameId = captured?.raGameId ?: raGameId,
            richPresence = captured?.message,
            richPresenceAt = captured?.at
        )
    }

    /**
     * The network half of a recorded session, on [scope]: warms the web progress cache, and for an
     * external emulator fills in the rich-presence line and the snapshot unlocks. Every failure
     * leaves the row as it was.
     */
    fun onSessionRecorded(
        scope: CoroutineScope,
        sessionId: Long,
        gameId: Long,
        emulatorPackage: String,
        facts: SessionRecapFacts,
        start: Instant,
        end: Instant
    ) {
        val snapshot = launchSnapshot?.takeIf { it.gameId == gameId && it.startTime == start }
        if (snapshot != null) launchSnapshot = null
        val raGameId = facts.raGameId ?: return
        scope.launch {
            val source = metadataSource.get()
            guarded(sessionId, "web progress warm") { source.warmGameProgress(raGameId, end) }
            if (isBuiltin(emulatorPackage)) return@launch
            guarded(sessionId, "rich presence lookup") { recordRichPresence(source, sessionId, raGameId, start, end) }
            val atLaunch = snapshot?.let { LaunchUnlocks(it.raGameId, it.unlocks.await()) }
                ?: storedLaunchUnlocks(gameId, start)
                ?: return@launch
            guarded(sessionId, "snapshot unlocks") { recordSnapshotUnlocks(source, sessionId, atLaunch) }
            guarded(sessionId, "launch snapshot clear") { sessionPreferences.setRaLaunchSnapshot(null) }
        }
    }

    private suspend fun storeLaunchSnapshot(snapshot: LaunchUnlockSnapshot) {
        try {
            sessionPreferences.setRaLaunchSnapshot(RaUnlockSnapshots.encodeLaunch(snapshot))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.debug(TAG, "Launch unlock snapshot for game ${snapshot.gameId} not stored: ${e.message}")
        }
    }

    private suspend fun storedLaunchUnlocks(gameId: Long, start: Instant): LaunchUnlocks? = try {
        RaUnlockSnapshots.decodeLaunch(sessionPreferences.getRaLaunchSnapshot())
            ?.takeIf { it.gameId == gameId && it.startTime == start }
            ?.let { LaunchUnlocks(it.raGameId, it.unlocks) }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Logger.debug(TAG, "Stored launch unlock snapshot for game $gameId unreadable: ${e.message}")
        null
    }

    private suspend fun recordRichPresence(
        source: RaMetadataSource,
        sessionId: Long,
        raGameId: Long,
        start: Instant,
        end: Instant
    ) {
        val summary = source.userSummary() ?: return
        val accepted = acceptSummary(summary, raGameId, start, end) ?: return
        playSessionDao.setRichPresence(sessionId, accepted.message, accepted.at)
        Logger.debug(TAG, "Recorded rich presence for session $sessionId raGameId=$raGameId")
    }

    private suspend fun recordSnapshotUnlocks(source: RaMetadataSource, sessionId: Long, atLaunch: LaunchUnlocks) {
        val atEnd = guardedSnapshot(source, atLaunch.raGameId) ?: return
        val unlocks = RaUnlockSnapshots.diff(atLaunch.unlocks, atEnd) ?: return
        playSessionDao.setRaSnapshotUnlocks(sessionId, RaUnlockSnapshots.encode(unlocks))
        Logger.debug(TAG, "Recorded ${unlocks.size} snapshot unlocks for session $sessionId")
    }

    private suspend fun guardedSnapshot(source: RaMetadataSource, raGameId: Long): GameUnlocks? = try {
        source.unlockSnapshot(raGameId)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Logger.debug(TAG, "Unlock snapshot for $raGameId failed: ${e.message}")
        null
    }

    private suspend fun guarded(sessionId: Long, what: String, block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.debug(TAG, "$what skipped for session $sessionId: ${e.message}")
        }
    }

    private fun isBuiltin(emulatorPackage: String): Boolean =
        emulatorPackage == EmulatorRegistry.BUILTIN_PACKAGE

    data class AcceptedRichPresence(val message: String, val at: Instant)

    companion object {
        private const val TAG = "SessionRecapFacts"

        /**
         * The summary's line when it describes this session: the same RA game, a non-blank
         * message, and a timestamp inside [start, end]. Anything else is another session's line.
         */
        fun acceptSummary(
            summary: RAWebUserSummary,
            raGameId: Long,
            start: Instant,
            end: Instant
        ): AcceptedRichPresence? {
            if (summary.lastGameId != raGameId) return null
            val message = summary.richPresenceMsg?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            val at = summary.richPresenceMsgDate
                ?.let { parseTimestamp(it) }
                ?.let { Instant.ofEpochMilli(it) }
                ?: return null
            if (at.isBefore(start) || at.isAfter(end)) return null
            return AcceptedRichPresence(message, at)
        }
    }
}
