package com.nendo.argosy.ui.screens.common

import com.nendo.argosy.data.emulator.LaunchProgressTracker
import com.nendo.argosy.domain.model.LaunchPhase
import com.nendo.argosy.domain.model.LaunchPromptOption
import com.nendo.argosy.domain.model.LaunchRecap
import com.nendo.argosy.domain.model.LaunchStep
import com.nendo.argosy.domain.model.PreLaunchRecap
import com.nendo.argosy.domain.model.RecapLastSession
import com.nendo.argosy.domain.model.SessionUnlock
import com.nendo.argosy.domain.model.phase
import com.nendo.argosy.domain.usecase.recap.GetSessionUnlocksUseCase
import com.nendo.argosy.domain.usecase.recap.PreLaunchRecapOverride
import com.nendo.argosy.domain.usecase.recap.RecapGate
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class PreLaunchRecapStepTest {

    private val start = Instant.parse("2026-10-01T10:00:00Z")
    private val end = Instant.parse("2026-10-01T12:00:00Z")
    private val recapGate: RecapGate = mockk()
    private val getSessionUnlocks: GetSessionUnlocksUseCase = mockk()

    private val unlock = SessionUnlock(
        "1",
        "First",
        5,
        "/badge.png",
        isHardcore = true,
        unlockedAt = start,
        description = "Finish the first level",
        type = null,
        gameTitle = null
    )

    private fun due(richPresence: String? = null) = PreLaunchRecap(
        gameId = 1L,
        raGameId = 50L,
        ownerUserId = 7L,
        unlockWindowStart = start,
        unlockWindowEnd = end,
        lastSession = RecapLastSession(start, end, activePlayMs = 7_200_000L, richPresence = richPresence, daysSince = 12L)
    )

    private class Requested(private val recap: LaunchRecap?, vararg gameIds: Long) : PreLaunchRecapOverride {
        val pending = gameIds.toMutableSet()
        override suspend fun consume(gameId: Long): Boolean = pending.remove(gameId)
        override suspend fun recapFor(gameId: Long, gameTitle: String): LaunchRecap? = recap
    }

    private fun step(overrides: Set<PreLaunchRecapOverride> = emptySet()) =
        PreLaunchRecapStep(recapGate, getSessionUnlocks, overrides)

    private suspend fun ticket(tracker: LaunchProgressTracker) = tracker.begin(1L, "Game")!!

    private suspend fun LaunchProgressTracker.awaitRecap(): LaunchRecap =
        (progress.first { it?.step is LaunchStep.Recap }!!.step as LaunchStep.Recap).recap

    @Test
    fun `no recap due lets the launch go on untouched`() = runTest {
        val tracker = LaunchProgressTracker(backgroundScope)
        coEvery { recapGate.preLaunchRecap(1L, any()) } returns null

        assertTrue(step().run(ticket(tracker), 1L, "Game"))
        assertNull(tracker.progress.value)
    }

    @Test
    fun `a due recap with unlocks waits for start`() = runTest {
        val tracker = LaunchProgressTracker(backgroundScope)
        coEvery { recapGate.preLaunchRecap(1L, any()) } returns due()
        coEvery { getSessionUnlocks.inWindow(1L, 50L, 7L, start, end) } returns listOf(unlock)

        val result = async { step().run(ticket(tracker), 1L, "Game") }
        assertEquals(listOf(unlock), tracker.awaitRecap().unlocks)
        tracker.answer(LaunchPromptOption.START_GAME)

        assertTrue(result.await())
    }

    @Test
    fun `cancelling from the recap stops the launch`() = runTest {
        val tracker = LaunchProgressTracker(backgroundScope)
        coEvery { recapGate.preLaunchRecap(1L, any()) } returns due(richPresence = "World 2-1")
        coEvery { getSessionUnlocks.inWindow(any(), any(), any(), any(), any()) } returns emptyList()

        val result = async { step().run(ticket(tracker), 1L, "Game") }
        tracker.awaitRecap()
        tracker.cancel()

        assertFalse(result.await())
    }

    @Test
    fun `a due recap with nothing to show is skipped`() = runTest {
        val tracker = LaunchProgressTracker(backgroundScope)
        coEvery { recapGate.preLaunchRecap(1L, any()) } returns due()
        coEvery { getSessionUnlocks.inWindow(any(), any(), any(), any(), any()) } returns emptyList()

        assertTrue(step().run(ticket(tracker), 1L, "Game"))
        assertFalse(tracker.progress.value?.step is LaunchStep.Recap)
    }

    @Test
    fun `unlocks that fail to load leave a rich presence recap standing`() = runTest {
        val tracker = LaunchProgressTracker(backgroundScope)
        coEvery { recapGate.preLaunchRecap(1L, any()) } returns due(richPresence = "World 2-1")
        coEvery { getSessionUnlocks.inWindow(any(), any(), any(), any(), any()) } throws IllegalStateException("offline")

        val result = async { step().run(ticket(tracker), 1L, "Game") }
        assertEquals("World 2-1", tracker.awaitRecap().lastSession?.richPresence)
        tracker.answer(LaunchPromptOption.START_GAME)

        assertTrue(result.await())
    }

    @Test
    fun `a failing recap check never blocks the launch`() = runTest {
        val tracker = LaunchProgressTracker(backgroundScope)
        coEvery { recapGate.preLaunchRecap(1L, any()) } throws IllegalStateException("db")

        assertTrue(step().run(ticket(tracker), 1L, "Game"))
    }

    @Test
    fun `a requested recap skips the gate and is spent by that launch`() = runTest {
        val tracker = LaunchProgressTracker(backgroundScope)
        val forced = LaunchRecap("Game", lastSession = null, unlocks = listOf(unlock))
        val requested = Requested(forced, 1L)
        coEvery { recapGate.preLaunchRecap(1L, any()) } returns null

        val result = async { step(setOf(requested)).run(ticket(tracker), 1L, "Game") }
        assertEquals(forced, tracker.awaitRecap())
        tracker.answer(LaunchPromptOption.START_GAME)

        assertTrue(result.await())
        coVerify(exactly = 0) { recapGate.preLaunchRecap(any(), any()) }
        assertTrue(requested.pending.isEmpty())
    }

    @Test
    fun `a request for another game leaves the gate in charge`() = runTest {
        val tracker = LaunchProgressTracker(backgroundScope)
        val requested = Requested(LaunchRecap("Other", null, listOf(unlock)), 2L)
        coEvery { recapGate.preLaunchRecap(1L, any()) } returns null

        assertTrue(step(setOf(requested)).run(ticket(tracker), 1L, "Game"))
        coVerify(exactly = 1) { recapGate.preLaunchRecap(1L, any()) }
        assertEquals(setOf(2L), requested.pending)
    }

    @Test
    fun `a requested recap with nothing to show lets the launch go on`() = runTest {
        val tracker = LaunchProgressTracker(backgroundScope)

        assertTrue(step(setOf(Requested(null, 1L))).run(ticket(tracker), 1L, "Game"))
        assertEquals(LaunchStep.Starting, tracker.progress.value?.step)
        coVerify(exactly = 0) { recapGate.preLaunchRecap(any(), any()) }
    }

    @Test
    fun `starting from the recap takes the card down and hands the launch to a cancellable step`() = runTest {
        val tracker = LaunchProgressTracker(backgroundScope)
        coEvery { recapGate.preLaunchRecap(1L, any()) } returns due()
        coEvery { getSessionUnlocks.inWindow(1L, 50L, 7L, start, end) } returns listOf(unlock)

        val result = async { step().run(ticket(tracker), 1L, "Game") }
        tracker.awaitRecap()
        tracker.answer(LaunchPromptOption.START_GAME)

        assertTrue(result.await())
        val step = tracker.progress.value?.step
        assertEquals(LaunchStep.Starting, step)
        assertNotEquals(LaunchPhase.QUESTION, step?.phase)
        assertNotEquals(LaunchPhase.LAUNCHING, step?.phase)
    }

    @Test
    fun `a due recap with nothing to show ends the recap loading line`() = runTest {
        val tracker = LaunchProgressTracker(backgroundScope)
        coEvery { recapGate.preLaunchRecap(1L, any()) } returns due()
        coEvery { getSessionUnlocks.inWindow(any(), any(), any(), any(), any()) } returns emptyList()

        assertTrue(step().run(ticket(tracker), 1L, "Game"))
        assertEquals(LaunchStep.Starting, tracker.progress.value?.step)
    }
}
