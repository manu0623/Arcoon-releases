package com.nendo.argosy.ui.screens.common

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.nendo.argosy.DualScreenManager
import com.nendo.argosy.DualScreenManagerHolder
import com.nendo.argosy.data.emulator.ActiveSession
import com.nendo.argosy.data.emulator.PlaySessionTracker
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class GameStartRouterTest {

    private val appContext = mockk<Context>(relaxed = true)
    private val resolver = mockk<EmulatorLaunchTargetResolver>()
    private val options = mockk<Bundle>()
    private val activeSession = MutableStateFlow<ActiveSession?>(null)
    private val tracker = mockk<PlaySessionTracker>(relaxed = true) {
        every { activeSession } returns this@GameStartRouterTest.activeSession
    }
    private val intent = mockk<Intent>(relaxed = true) {
        every { component } returns mockk<ComponentName> { every { packageName } returns EMULATOR }
    }

    @After
    fun tearDown() {
        DualScreenManagerHolder.instance = null
    }

    @Test
    fun `the manager starts the resolved request with no screen listening`() = runTest {
        val manager = mockk<DualScreenManager> {
            every { startGame(any()) } returns Result.success(Unit)
        }
        DualScreenManagerHolder.instance = manager
        coEvery { resolver.launchOptionsFor(7L, 2, EMULATOR) } returns options
        var failure: Throwable? = null

        activeSession.value = sessionFor(7L)

        GameStartRouter(appContext, resolver, tracker, this)
            .start(gameId = 7L, intent = intent, overrideDisplayId = 2, onFailure = { failure = it })
        advanceUntilIdle()

        coVerifyOrder {
            resolver.launchOptionsFor(7L, 2, EMULATOR)
            manager.startGame(GameLaunchRequest(intent, options))
        }
        verify(exactly = 0) { appContext.startActivity(any(), any()) }
        verify(exactly = 0) { tracker.cancelSession() }
        assertNull(failure)
    }

    @Test
    fun `without a manager the application starts the game as a new task`() = runTest {
        coEvery { resolver.launchOptionsFor(7L, null, EMULATOR) } returns options

        GameStartRouter(appContext, resolver, tracker, this).start(gameId = 7L, intent = intent)
        advanceUntilIdle()

        verify(exactly = 1) { intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        verify(exactly = 1) { appContext.startActivity(intent, options) }
    }

    @Test
    fun `a refused start reaches the failure callback`() = runTest {
        val refusal = SecurityException("denied")
        DualScreenManagerHolder.instance = mockk<DualScreenManager> {
            every { startGame(any()) } returns Result.failure(refusal)
        }
        coEvery { resolver.launchOptionsFor(7L, null, EMULATOR) } returns options
        var failure: Throwable? = null
        activeSession.value = sessionFor(7L)

        GameStartRouter(appContext, resolver, tracker, this)
            .start(gameId = 7L, intent = intent, onFailure = { failure = it })
        advanceUntilIdle()

        assertSame(refusal, failure)
        verify(exactly = 1) { tracker.cancelSession() }
    }

    @Test
    fun `a refused start leaves another game's session running`() = runTest {
        DualScreenManagerHolder.instance = mockk<DualScreenManager> {
            every { startGame(any()) } returns Result.failure(SecurityException("denied"))
        }
        coEvery { resolver.launchOptionsFor(7L, null, EMULATOR) } returns options
        activeSession.value = sessionFor(8L)

        GameStartRouter(appContext, resolver, tracker, this).start(gameId = 7L, intent = intent)
        advanceUntilIdle()

        verify(exactly = 0) { tracker.cancelSession() }
    }

    @Test
    fun `placing resolves the display without starting the game`() = runTest {
        val manager = mockk<DualScreenManager>(relaxed = true)
        DualScreenManagerHolder.instance = manager
        coEvery { resolver.launchOptionsFor(7L, 2, EMULATOR) } returns options
        val router = GameStartRouter(appContext, resolver, tracker, this)

        val placed = router.place(gameId = 7L, intent = intent, overrideDisplayId = 2)

        assertEquals(GameLaunchRequest(intent, options), placed.getOrNull())
        coVerify(exactly = 1) { resolver.launchOptionsFor(7L, 2, EMULATOR) }
        verify(exactly = 0) { manager.startGame(any()) }
    }

    @Test
    fun `a placed request starts through the manager`() = runTest {
        val manager = mockk<DualScreenManager> {
            every { startGame(any()) } returns Result.success(Unit)
        }
        DualScreenManagerHolder.instance = manager
        val router = GameStartRouter(appContext, resolver, tracker, this)

        val result = router.startPlaced(GameLaunchRequest(intent, options))

        assertTrue(result.isSuccess)
        verify(exactly = 1) { manager.startGame(GameLaunchRequest(intent, options)) }
        coVerify(exactly = 0) { resolver.launchOptionsFor(any(), any(), any()) }
    }

    @Test
    fun `an unresolvable placement reaches the failure callback without a start`() = runTest {
        val manager = mockk<DualScreenManager>(relaxed = true)
        DualScreenManagerHolder.instance = manager
        val broken = IllegalStateException("db closed")
        coEvery { resolver.launchOptionsFor(7L, null, EMULATOR) } throws broken
        var failure: Throwable? = null
        activeSession.value = sessionFor(7L)

        GameStartRouter(appContext, resolver, tracker, this)
            .start(gameId = 7L, intent = intent, onFailure = { failure = it })
        advanceUntilIdle()

        assertSame(broken, failure)
        verify(exactly = 0) { manager.startGame(any()) }
        verify(exactly = 1) { tracker.cancelSession() }
    }

    @Test
    fun `the started game window opens without the system transition`() = runTest {
        val flagged = flagTrackingIntent(initialFlags = 0)
        coEvery { resolver.launchOptionsFor(7L, null, any()) } returns options
        val started = slot<Intent>()
        every { appContext.startActivity(capture(started), options) } returns Unit

        GameStartRouter(appContext, resolver, tracker, this).start(gameId = 7L, intent = flagged.intent)
        advanceUntilIdle()

        assertSame(flagged.intent, started.captured)
        assertNotEquals(0, flagged.flags and Intent.FLAG_ACTIVITY_NO_ANIMATION)
    }

    @Test
    fun `the caller flags survive the transition flag`() = runTest {
        val callerFlags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        val flagged = flagTrackingIntent(initialFlags = callerFlags)
        val manager = mockk<DualScreenManager> {
            every { startGame(any()) } returns Result.success(Unit)
        }
        DualScreenManagerHolder.instance = manager

        GameStartRouter(appContext, resolver, tracker, this).startPlaced(GameLaunchRequest(flagged.intent, options))

        assertEquals(callerFlags or Intent.FLAG_ACTIVITY_NO_ANIMATION, flagged.flags)
        verify(exactly = 1) { manager.startGame(GameLaunchRequest(flagged.intent, options)) }
    }

    @Test
    fun `cancelling a failed start leaves another game's session running`() = runTest {
        activeSession.value = sessionFor(8L)

        GameStartRouter(appContext, resolver, tracker, this).cancelSessionOf(7L)

        verify(exactly = 0) { tracker.cancelSession() }
    }

    @Test
    fun `cancelling a failed start ends that game's session`() = runTest {
        activeSession.value = sessionFor(7L)

        GameStartRouter(appContext, resolver, tracker, this).cancelSessionOf(7L)

        verify(exactly = 1) { tracker.cancelSession() }
    }

    private class FlagTrackingIntent(val intent: Intent, initialFlags: Int) {
        var flags: Int = initialFlags
    }

    private fun flagTrackingIntent(initialFlags: Int): FlagTrackingIntent {
        val mock = mockk<Intent>(relaxed = true)
        val tracked = FlagTrackingIntent(mock, initialFlags)
        every { mock.flags } answers { tracked.flags }
        every { mock.addFlags(any()) } answers {
            tracked.flags = tracked.flags or firstArg<Int>()
            mock
        }
        every { mock.setFlags(any()) } answers {
            tracked.flags = firstArg()
            mock
        }
        return tracked
    }

    private fun sessionFor(gameId: Long) =
        ActiveSession(gameId = gameId, startTime = Instant.EPOCH, emulatorPackage = EMULATOR)

    private companion object {
        const val EMULATOR = "com.pokedaisy.app"
    }
}
