package com.nendo.argosy

import com.nendo.argosy.data.emulator.ActiveSession
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmulatorReleaseTest {

    private val emulator = "org.ppsspp.ppsspp"
    private val ended = ActiveSession(gameId = 1L, startTime = Instant.ofEpochSecond(100), emulatorPackage = emulator)

    private suspend fun release(
        activeSession: MutableStateFlow<ActiveSession?>,
        persisted: () -> Boolean = { false },
        onStop: (Int) -> Unit = {}
    ): Int {
        var stops = 0
        releaseStoppedEmulator(
            emulatorPackage = emulator,
            endedSession = ended,
            activeSession = activeSession,
            persistedSessionActive = persisted,
            stop = { stops++; onStop(stops) },
            attempts = 6,
            intervalMs = 5_000L
        )
        return stops
    }

    @Test
    fun `with no new session the emulator is stopped on every attempt`() = runTest {
        assertEquals(6, release(MutableStateFlow(null)))
    }

    @Test
    fun `a relaunch of the same emulator stops the loop before its record is written`() = runTest {
        val active = MutableStateFlow<ActiveSession?>(null)
        val relaunch = ActiveSession(gameId = 2L, startTime = Instant.ofEpochSecond(200), emulatorPackage = emulator)

        val stops = release(active, persisted = { false }) { active.value = relaunch }

        assertEquals(1, stops)
    }

    @Test
    fun `a session already live when the loop starts is never stopped`() = runTest {
        val relaunch = ActiveSession(gameId = 1L, startTime = Instant.ofEpochSecond(200), emulatorPackage = emulator)

        assertEquals(0, release(MutableStateFlow(relaunch)))
    }

    @Test
    fun `the ended session still lingering does not stop the release`() = runTest {
        assertEquals(6, release(MutableStateFlow(ended)))
    }

    @Test
    fun `a recorded session stops the loop`() = runTest {
        assertEquals(0, release(MutableStateFlow(null), persisted = { true }))
    }

    @Test
    fun `an emulator already gone is reported left without waiting`() = runTest {
        val left = awaitEmulatorLeft(
            stillOnScreen = { false },
            launcherStillInFront = { true },
            checks = 8,
            intervalMs = 2_000L
        )

        assertTrue(left)
        assertEquals(0L, testScheduler.currentTime)
    }

    @Test
    fun `an emulator that stops ten seconds after the launcher resumes is reported left`() = runTest {
        val left = awaitEmulatorLeft(
            stillOnScreen = { testScheduler.currentTime < 10_600L },
            launcherStillInFront = { true },
            checks = 8,
            intervalMs = 2_000L
        )

        assertTrue(left)
        assertEquals(12_000L, testScheduler.currentTime)
    }

    @Test
    fun `the launcher losing focus ends the wait without a further check`() = runTest {
        var checks = 0
        val left = awaitEmulatorLeft(
            stillOnScreen = { checks++; true },
            launcherStillInFront = { testScheduler.currentTime < 4_000L },
            checks = 8,
            intervalMs = 2_000L
        )

        assertFalse(left)
        assertEquals(2, checks)
    }

    @Test
    fun `an emulator that never leaves is checked once plus every settle interval`() = runTest {
        var checks = 0
        val left = awaitEmulatorLeft(
            stillOnScreen = { checks++; true },
            launcherStillInFront = { true },
            checks = 8,
            intervalMs = 2_000L
        )

        assertFalse(left)
        assertEquals(9, checks)
        assertEquals(16_000L, testScheduler.currentTime)
    }
}
