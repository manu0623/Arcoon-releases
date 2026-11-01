package com.nendo.argosy.ui.screens.common

import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.nendo.argosy.DualScreenManager
import com.nendo.argosy.DualScreenManagerHolder
import com.nendo.argosy.core.input.SoundType
import com.nendo.argosy.data.emulator.ActiveSession
import com.nendo.argosy.data.emulator.EXTRA_ALREADY_LAUNCHED
import com.nendo.argosy.data.emulator.ExternalSessionStart
import com.nendo.argosy.data.emulator.LaunchOrigin
import com.nendo.argosy.data.emulator.LaunchProgressTracker
import com.nendo.argosy.data.emulator.PlaySessionTracker
import com.nendo.argosy.domain.model.LaunchStep
import com.nendo.argosy.ui.input.SoundFeedbackManager
import com.nendo.argosy.ui.theme.generated.ComponentDefaults
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import io.mockk.verifyOrder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

class LaunchHandoffTest {

    private val holdMs = ComponentDefaults.LaunchOverlay.veilMs.toLong()
    private val gameId = 7L
    private val intent: Intent = mockk(relaxed = true)
    private val options: Bundle = mockk()
    private val soundManager: SoundFeedbackManager = mockk(relaxed = true)
    private val resolver: EmulatorLaunchTargetResolver = mockk()
    private val manager: DualScreenManager = mockk()
    private val activeSession = MutableStateFlow<ActiveSession?>(null)
    private val playSessionTracker: PlaySessionTracker = mockk(relaxed = true) {
        every { activeSession } returns this@LaunchHandoffTest.activeSession
    }
    private val session = ExternalSessionStart(
        gameId = gameId,
        emulatorPackage = "org.citra.emu",
        coreName = "citra",
        variantFileId = 50L,
        origin = LaunchOrigin.EXTERNAL
    )

    @Before
    fun setUp() {
        coEvery { resolver.launchOptionsFor(gameId, any(), any()) } returns options
        every { manager.startGame(any()) } returns Result.success(Unit)
        DualScreenManagerHolder.instance = manager
    }

    @After
    fun tearDown() {
        DualScreenManagerHolder.instance = null
    }

    private fun router(scope: CoroutineScope) = GameStartRouter(mockk<Context>(relaxed = true), resolver, playSessionTracker, scope)

    private fun handoff(tracker: LaunchProgressTracker, router: GameStartRouter) =
        LaunchHandoff(tracker, soundManager, router, playSessionTracker, holdMs)

    private fun verifyNoSessionStarted() {
        verify(exactly = 0) {
            playSessionTracker.startSession(any(), any(), any(), any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `launching is published and the window waits out the veil before it is requested`() = runTest {
        val tracker = LaunchProgressTracker(backgroundScope)
        val router = router(backgroundScope)
        val ticket = tracker.begin(gameId, "Game")!!

        launch { handoff(tracker, router).handOff(ticket, intent, GameStart(gameId), null) }
        runCurrent()

        assertEquals(LaunchStep.Launching, tracker.progress.value?.step)
        assertTrue(ticket.isFinished)
        verify(exactly = 0) { manager.startGame(any()) }

        advanceTimeBy(holdMs - 1)
        runCurrent()
        verify(exactly = 0) { manager.startGame(any()) }

        advanceTimeBy(1)
        runCurrent()
        verify(exactly = 1) { manager.startGame(GameLaunchRequest(intent, options)) }
    }

    @Test
    fun `the launching screen stopping during the hold leaves launching up and the window still opens`() = runTest {
        val tracker = LaunchProgressTracker(backgroundScope)
        val router = router(backgroundScope)
        val ticket = tracker.begin(gameId, "Game")!!

        launch { handoff(tracker, router).handOff(ticket, intent, GameStart(gameId), null) }
        runCurrent()
        tracker.hostStopped()

        assertEquals(LaunchStep.Launching, tracker.progress.value?.step)

        advanceTimeBy(holdMs)
        runCurrent()
        verify(exactly = 1) { manager.startGame(GameLaunchRequest(intent, options)) }
        assertEquals(LaunchStep.Launching, tracker.progress.value?.step)
    }

    @Test
    fun `the launching screen stopping after the window is requested clears launching`() = runTest {
        val tracker = LaunchProgressTracker(backgroundScope)
        val router = router(backgroundScope)
        val ticket = tracker.begin(gameId, "Game")!!

        launch { handoff(tracker, router).handOff(ticket, intent, GameStart(gameId), null) }
        advanceTimeBy(holdMs)
        runCurrent()
        verify(exactly = 1) { manager.startGame(any()) }

        tracker.hostStopped()

        assertNull(tracker.progress.value)
    }

    @Test
    fun `the ticket is finished exactly once as launched`() = runTest {
        val tracker: LaunchProgressTracker = mockk(relaxed = true)
        val ticket: LaunchProgressTracker.Ticket = mockk(relaxed = true)
        val router = router(backgroundScope)

        handoff(tracker, router).handOff(ticket, intent, GameStart(gameId), null)

        verify(exactly = 1) { tracker.finish(ticket, true) }
        verify(exactly = 0) { tracker.finish(ticket, false) }
    }

    @Test
    fun `the launch sound plays at the start of the hold`() = runTest {
        val tracker = LaunchProgressTracker(backgroundScope)
        val router = router(backgroundScope)
        val ticket = tracker.begin(gameId, "Game")!!

        launch { handoff(tracker, router).handOff(ticket, intent, GameStart(gameId), null) }
        runCurrent()

        verify(exactly = 1) { soundManager.play(SoundType.LAUNCH_GAME) }
    }

    @Test
    fun `the game is placed before launching is published and the start uses that placement`() = runTest {
        val tracker = LaunchProgressTracker(backgroundScope)
        val router = router(backgroundScope)
        val ticket = tracker.begin(gameId, "Game")!!
        var stepWhenPlaced: LaunchStep? = LaunchStep.Launching
        coEvery { resolver.launchOptionsFor(gameId, 2, any()) } answers {
            stepWhenPlaced = tracker.progress.value?.step
            options
        }

        launch { handoff(tracker, router).handOff(ticket, intent, GameStart(gameId, overrideDisplayId = 2), null) }
        runCurrent()

        assertNull(stepWhenPlaced)
        coVerify(exactly = 1) { resolver.launchOptionsFor(gameId, 2, any()) }
        assertEquals(LaunchStep.Launching, tracker.progress.value?.step)
        verify(exactly = 0) { manager.startGame(any()) }

        advanceTimeBy(holdMs)
        runCurrent()

        coVerify(exactly = 1) { resolver.launchOptionsFor(any(), any(), any()) }
        verify(exactly = 1) { manager.startGame(GameLaunchRequest(intent, options)) }
    }

    @Test
    fun `a launch cancelled while its placement resolves opens nothing and starts no session`() = runTest {
        val tracker = LaunchProgressTracker(backgroundScope)
        val router = router(backgroundScope)
        val ticket = tracker.begin(gameId, "Game")!!
        coEvery { resolver.launchOptionsFor(gameId, any(), any()) } answers {
            tracker.cancel()
            options
        }

        launch { handoff(tracker, router).handOff(ticket, intent, GameStart(gameId), session) }
        advanceTimeBy(holdMs)
        runCurrent()

        assertNull(tracker.progress.value)
        verify(exactly = 0) { soundManager.play(SoundType.LAUNCH_GAME) }
        verify(exactly = 0) { manager.startGame(any()) }
        verifyNoSessionStarted()
    }

    @Test
    fun `a launch whose scope dies during the hold opens nothing, starts no session and clears at once`() = runTest {
        val tracker = LaunchProgressTracker(backgroundScope)
        val router = router(backgroundScope)
        val ticket = tracker.begin(gameId, "Game")!!

        val job = launch { handoff(tracker, router).handOff(ticket, intent, GameStart(gameId), session) }
        runCurrent()
        advanceTimeBy(holdMs - 1)
        job.cancel()
        runCurrent()

        assertNull(tracker.progress.value)
        assertFalse(tracker.launchLive.value)
        advanceTimeBy(holdMs)
        runCurrent()
        verify(exactly = 0) { manager.startGame(any()) }
        verifyNoSessionStarted()
    }

    @Test
    fun `the session starts once, after the game's start succeeds and before the window is marked requested`() = runTest {
        val tracker = spyk(LaunchProgressTracker(backgroundScope))
        val router = router(backgroundScope)
        val ticket = tracker.begin(gameId, "Game")!!

        launch { handoff(tracker, router).handOff(ticket, intent, GameStart(gameId), session) }
        runCurrent()
        verifyNoSessionStarted()

        advanceTimeBy(holdMs)
        runCurrent()

        verifyOrder {
            manager.startGame(GameLaunchRequest(intent, options))
            playSessionTracker.startSession(
                gameId = gameId,
                emulatorPackage = "org.citra.emu",
                coreName = "citra",
                isHardcore = false,
                isNewGame = true,
                isNetplayGuest = false,
                variantFileId = 50L,
                origin = LaunchOrigin.EXTERNAL
            )
            tracker.windowRequested(ticket)
        }
        verify(exactly = 1) {
            playSessionTracker.startSession(any(), any(), any(), any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `a game the launch already opened starts its session at once with no hold`() = runTest {
        val tracker = LaunchProgressTracker(backgroundScope)
        val router = router(backgroundScope)
        val ticket = tracker.begin(gameId, "Game")!!
        val opened: Intent = mockk(relaxed = true)
        every { opened.getBooleanExtra(EXTRA_ALREADY_LAUNCHED, false) } returns true

        launch { handoff(tracker, router).handOff(ticket, opened, GameStart(gameId), session) }
        runCurrent()

        verify(exactly = 1) {
            playSessionTracker.startSession(any(), any(), any(), any(), any(), any(), any(), any())
        }
        tracker.hostStopped()
        assertNull(tracker.progress.value)
    }

    @Test
    fun `a launch that owes no session starts none`() = runTest {
        val tracker = LaunchProgressTracker(backgroundScope)
        val router = router(backgroundScope)
        val ticket = tracker.begin(gameId, "Game")!!

        launch { handoff(tracker, router).handOff(ticket, intent, GameStart(gameId), null) }
        advanceTimeBy(holdMs)
        runCurrent()

        verify(exactly = 1) { manager.startGame(any()) }
        verifyNoSessionStarted()
    }

    @Test
    fun `a refused start clears launching at once, reaches the caller's failure callback and starts no session`() = runTest {
        val refusal = SecurityException("denied")
        every { manager.startGame(any()) } returns Result.failure(refusal)
        val tracker = spyk(LaunchProgressTracker(backgroundScope))
        val router = router(backgroundScope)
        val ticket = tracker.begin(gameId, "Game")!!
        var failure: Throwable? = null

        launch {
            handoff(tracker, router).handOff(ticket, intent, GameStart(gameId, onFailure = { e -> failure = e }), session)
        }
        advanceTimeBy(holdMs)
        runCurrent()

        assertSame(refusal, failure)
        assertNull(tracker.progress.value)
        assertNull(tracker.current)
        verify(exactly = 1) { tracker.openFailed(ticket) }
        verify(exactly = 0) { tracker.windowRequested(any()) }
        verifyNoSessionStarted()
        assertNotNull(tracker.begin(gameId, "Game"))
    }

    @Test
    fun `a refused start leaves a launch that finished during its hold showing`() = runTest {
        every { manager.startGame(any()) } returns Result.failure(SecurityException("denied"))
        val tracker = LaunchProgressTracker(backgroundScope)
        val router = router(backgroundScope)
        val ticket = tracker.begin(gameId, "Game")!!

        launch { handoff(tracker, router).handOff(ticket, intent, GameStart(gameId), null) }
        runCurrent()
        val newer = tracker.begin(8L, "Newer")!!
        tracker.finish(newer, launched = true)
        advanceTimeBy(holdMs)
        runCurrent()

        verify(exactly = 1) { manager.startGame(any()) }
        assertEquals(8L, tracker.progress.value?.gameId)
        assertEquals(LaunchStep.Launching, tracker.progress.value?.step)
    }

    @Test
    fun `an unplaceable game fails at once with no sound, no hold and no launching`() = runTest {
        val broken = IllegalStateException("db closed")
        coEvery { resolver.launchOptionsFor(gameId, any(), any()) } throws broken
        val tracker = LaunchProgressTracker(backgroundScope)
        val router = router(backgroundScope)
        val ticket = tracker.begin(gameId, "Game")!!
        var failure: Throwable? = null
        var progressWhenFailed: Any? = Unit

        launch {
            handoff(tracker, router).handOff(
                ticket,
                intent,
                GameStart(gameId, onFailure = { e ->
                    failure = e
                    progressWhenFailed = tracker.progress.value
                }),
                session
            )
        }
        runCurrent()

        assertSame(broken, failure)
        assertNull(progressWhenFailed)
        assertNull(tracker.progress.value)
        assertEquals(0L, testScheduler.currentTime)
        verify(exactly = 0) { soundManager.play(SoundType.LAUNCH_GAME) }
        verify(exactly = 0) { manager.startGame(any()) }

        advanceTimeBy(holdMs)
        runCurrent()
        verify(exactly = 0) { manager.startGame(any()) }
        verifyNoSessionStarted()
    }

    @Test
    fun `a refused start cancels the running session of the game it was starting`() = runTest {
        every { manager.startGame(any()) } returns Result.failure(SecurityException("denied"))
        activeSession.value = runningSession(gameId)
        val tracker = LaunchProgressTracker(backgroundScope)
        val router = router(backgroundScope)
        val ticket = tracker.begin(gameId, "Game")!!

        launch { handoff(tracker, router).handOff(ticket, intent, GameStart(gameId), null) }
        advanceTimeBy(holdMs)
        runCurrent()

        verify(exactly = 1) { playSessionTracker.cancelSession() }
    }

    @Test
    fun `an unplaceable game cancels the running session of the game it was starting`() = runTest {
        coEvery { resolver.launchOptionsFor(gameId, any(), any()) } throws IllegalStateException("db closed")
        activeSession.value = runningSession(gameId)
        val tracker = LaunchProgressTracker(backgroundScope)
        val router = router(backgroundScope)
        val ticket = tracker.begin(gameId, "Game")!!

        launch { handoff(tracker, router).handOff(ticket, intent, GameStart(gameId), null) }
        runCurrent()

        verify(exactly = 1) { playSessionTracker.cancelSession() }
    }

    @Test
    fun `a refused start leaves another game's session running`() = runTest {
        every { manager.startGame(any()) } returns Result.failure(SecurityException("denied"))
        activeSession.value = runningSession(8L)
        val tracker = LaunchProgressTracker(backgroundScope)
        val router = router(backgroundScope)
        val ticket = tracker.begin(gameId, "Game")!!

        launch { handoff(tracker, router).handOff(ticket, intent, GameStart(gameId), session) }
        advanceTimeBy(holdMs)
        runCurrent()

        verify(exactly = 0) { playSessionTracker.cancelSession() }
    }

    @Test
    fun `a launch that opens its window cancels no session`() = runTest {
        activeSession.value = runningSession(gameId)
        val tracker = LaunchProgressTracker(backgroundScope)
        val router = router(backgroundScope)
        val ticket = tracker.begin(gameId, "Game")!!

        launch { handoff(tracker, router).handOff(ticket, intent, GameStart(gameId), null) }
        advanceTimeBy(holdMs)
        runCurrent()

        verify(exactly = 1) { manager.startGame(any()) }
        verify(exactly = 0) { playSessionTracker.cancelSession() }
    }

    private fun runningSession(id: Long) =
        ActiveSession(gameId = id, startTime = Instant.EPOCH, emulatorPackage = "org.citra.emu")
}
