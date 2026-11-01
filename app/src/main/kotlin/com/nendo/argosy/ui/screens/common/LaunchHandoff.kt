package com.nendo.argosy.ui.screens.common

import android.content.Intent
import com.nendo.argosy.core.input.SoundType
import com.nendo.argosy.data.emulator.EXTRA_ALREADY_LAUNCHED
import com.nendo.argosy.data.emulator.ExternalSessionStart
import com.nendo.argosy.data.emulator.LaunchProgressTracker
import com.nendo.argosy.data.emulator.PlaySessionTracker
import com.nendo.argosy.data.emulator.startExternal
import com.nendo.argosy.ui.input.SoundFeedbackManager
import com.nendo.argosy.ui.theme.generated.ComponentDefaults
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import javax.inject.Inject

/**
 * The last step of a successful launch. Publishes Launching, which starts the veil or the
 * single-screen fade, plays the launch sound, and holds for [holdMs] so the fade has landed
 * before the game's window is requested.
 */
class LaunchHandoff internal constructor(
    private val tracker: LaunchProgressTracker,
    private val soundManager: SoundFeedbackManager,
    private val startRouter: GameStartRouter,
    private val playSessionTracker: PlaySessionTracker,
    private val holdMs: Long
) {

    @Inject constructor(
        tracker: LaunchProgressTracker,
        soundManager: SoundFeedbackManager,
        startRouter: GameStartRouter,
        playSessionTracker: PlaySessionTracker
    ) : this(
        tracker,
        soundManager,
        startRouter,
        playSessionTracker,
        ComponentDefaults.LaunchOverlay.veilMs.toLong()
    )

    /**
     * Places [start]'s game, finishes [ticket] as launched and requests [intent]'s window with that
     * placement after the hold, which a game the launch already opened skips; [session] starts once
     * the window is requested. A cancelled ticket opens nothing. A failed placement or start, or a cut-short
     * hold, clears Launching at once; a failure also cancels the game's session and reaches [GameStart.onFailure].
     */
    suspend fun handOff(
        ticket: LaunchProgressTracker.Ticket,
        intent: Intent,
        start: GameStart,
        session: ExternalSessionStart?
    ) {
        val request = startRouter.place(start.gameId, intent, start.overrideDisplayId)
        if (ticket.isCancelled) return
        tracker.holdForWindow(ticket)
        val placed = request.getOrElse { return fail(ticket, it, start) }
        tracker.finish(ticket, launched = true)
        soundManager.play(SoundType.LAUNCH_GAME)
        if (!intent.getBooleanExtra(EXTRA_ALREADY_LAUNCHED, false)) {
            try {
                delay(holdMs)
            } catch (cancelled: CancellationException) {
                tracker.openFailed(ticket)
                throw cancelled
            }
        }
        val failure = startRouter.startPlaced(placed).exceptionOrNull()
        if (failure == null) {
            session?.let(playSessionTracker::startExternal)
            tracker.windowRequested(ticket)
            return
        }
        fail(ticket, failure, start)
    }

    private fun fail(ticket: LaunchProgressTracker.Ticket, failure: Throwable, start: GameStart) {
        tracker.openFailed(ticket)
        startRouter.cancelSessionOf(start.gameId)
        start.onFailure?.invoke(failure)
    }
}
