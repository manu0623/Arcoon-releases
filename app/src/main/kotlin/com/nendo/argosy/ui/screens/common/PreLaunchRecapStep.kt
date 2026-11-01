package com.nendo.argosy.ui.screens.common

import com.nendo.argosy.data.emulator.LaunchProgressTracker
import com.nendo.argosy.domain.model.LaunchRecap
import com.nendo.argosy.domain.model.LaunchStep
import com.nendo.argosy.domain.model.PreLaunchRecap
import com.nendo.argosy.domain.usecase.recap.GetSessionUnlocksUseCase
import com.nendo.argosy.domain.usecase.recap.PreLaunchRecapOverride
import com.nendo.argosy.domain.usecase.recap.RecapEligibility
import com.nendo.argosy.domain.usecase.recap.RecapGate
import com.nendo.argosy.util.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

/**
 * The recap step of a fresh launch. Shows the game's pre-launch recap when one is due and holds
 * the launch until the player starts the game or cancels it.
 */
class PreLaunchRecapStep @Inject constructor(
    private val recapGate: RecapGate,
    private val getSessionUnlocks: GetSessionUnlocksUseCase,
    private val overrides: Set<@JvmSuppressWildcards PreLaunchRecapOverride>
) {
    /**
     * False when the player cancelled the launch from the recap; true when the launch goes on,
     * including when no recap was due or it had nothing to show.
     */
    suspend fun run(ticket: LaunchProgressTracker.Ticket, gameId: Long, gameTitle: String): Boolean {
        val override = guarded(gameId, "override check") {
            overrides.firstOrNull { it.consume(gameId) }
        }
        if (override != null) {
            ticket.step(LaunchStep.RecapLoading)
            val forced = guarded(gameId, "forced recap") {
                withTimeoutOrNull(LOAD_TIMEOUT_MS) { override.recapFor(gameId, gameTitle) }
            }
            if (forced == null) Logger.warn(TAG, "Forced recap for game $gameId had nothing to show")
            return offer(ticket, forced)
        }
        val due = guarded(gameId, "recap check") { recapGate.preLaunchRecap(gameId) } ?: return true
        ticket.step(LaunchStep.RecapLoading)
        return offer(ticket, loadRecap(due, gameTitle))
    }

    private suspend fun offer(ticket: LaunchProgressTracker.Ticket, recap: LaunchRecap?): Boolean {
        if (ticket.isCancelled) return false
        val goesOn = recap == null || ticket.offerRecap(recap)
        if (goesOn) ticket.step(LaunchStep.Starting)
        return goesOn
    }

    private suspend fun loadRecap(due: PreLaunchRecap, gameTitle: String): LaunchRecap? {
        val unlocks = guarded(due.gameId, "unlock load") {
            withTimeoutOrNull(LOAD_TIMEOUT_MS) {
                getSessionUnlocks.inWindow(
                    gameId = due.gameId,
                    raGameId = due.raGameId,
                    ownerUserId = due.ownerUserId,
                    start = due.unlockWindowStart,
                    end = due.unlockWindowEnd,
                    snapshotUnlocks = due.snapshotUnlocks
                )
            }
        }
        if (unlocks == null) {
            Logger.warn(TAG, "Unlocks for game ${due.gameId} unavailable within ${LOAD_TIMEOUT_MS}ms, recap shows without them")
        }
        return RecapEligibility.launchRecapFor(due.lastSession, gameTitle, unlocks.orEmpty())
    }

    private suspend fun <T> guarded(gameId: Long, what: String, block: suspend () -> T?): T? = try {
        withContext(Dispatchers.IO) { block() }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Logger.warn(TAG, "Recap $what for game $gameId failed, launching without it", e)
        null
    }

    private companion object {
        const val TAG = "PreLaunchRecapStep"
        const val LOAD_TIMEOUT_MS = 4_000L
    }
}
