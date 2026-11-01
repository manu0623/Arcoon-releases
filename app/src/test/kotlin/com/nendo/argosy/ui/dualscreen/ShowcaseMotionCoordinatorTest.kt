package com.nendo.argosy.ui.dualscreen

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ShowcaseMotionCoordinatorTest {
    private val settleMs = 200L

    private fun TestScope.coordinator() = ShowcaseMotionCoordinator(this, settleMs)

    @Test
    fun `a new game advances the background key at once`() = runTest {
        val coordinator = coordinator()

        coordinator.focus(1L)

        assertEquals(
            ShowcaseMotionState(backgroundKey = 1L, contentKey = null, phase = ShowcaseMotionPhase.Settling),
            coordinator.state.value
        )
    }

    @Test
    fun `the content key waits for the settle period`() = runTest {
        val coordinator = coordinator()
        coordinator.focus(1L)

        advanceTimeBy(settleMs - 1)
        runCurrent()
        assertNull(coordinator.state.value.contentKey)
        assertEquals(ShowcaseMotionPhase.Settling, coordinator.state.value.phase)

        advanceTimeBy(1)
        runCurrent()
        assertEquals(
            ShowcaseMotionState(backgroundKey = 1L, contentKey = 1L, phase = ShowcaseMotionPhase.Entering),
            coordinator.state.value
        )
    }

    @Test
    fun `a change during the settle period restarts it`() = runTest {
        val coordinator = coordinator()
        coordinator.focus(1L)
        advanceTimeBy(settleMs - 50)
        runCurrent()

        coordinator.focus(2L)
        advanceTimeBy(settleMs - 50)
        runCurrent()
        assertEquals(
            ShowcaseMotionState(backgroundKey = 2L, contentKey = null, phase = ShowcaseMotionPhase.Settling),
            coordinator.state.value
        )

        advanceTimeBy(50)
        runCurrent()
        assertEquals(
            ShowcaseMotionState(backgroundKey = 2L, contentKey = 2L, phase = ShowcaseMotionPhase.Entering),
            coordinator.state.value
        )
    }

    @Test
    fun `rapid paging plays one entrance for the last game only`() = runTest {
        val coordinator = coordinator()
        val contentKeys = mutableListOf<Long?>()

        for (gameId in 1L..5L) {
            coordinator.focus(gameId)
            advanceTimeBy(settleMs / 2)
            runCurrent()
            contentKeys += coordinator.state.value.contentKey
        }
        advanceTimeBy(settleMs)
        runCurrent()

        assertEquals(listOf<Long?>(null, null, null, null, null), contentKeys)
        assertEquals(
            ShowcaseMotionState(backgroundKey = 5L, contentKey = 5L, phase = ShowcaseMotionPhase.Entering),
            coordinator.state.value
        )
    }

    @Test
    fun `republishing the same game changes nothing`() = runTest {
        val coordinator = coordinator()
        coordinator.focus(1L)
        advanceTimeBy(settleMs)
        runCurrent()
        coordinator.entranceFinished(1L)
        val atRest = coordinator.state.value

        coordinator.focus(1L)
        advanceTimeBy(settleMs)
        runCurrent()

        assertEquals(atRest, coordinator.state.value)
        assertEquals(ShowcaseMotionPhase.AtRest, coordinator.state.value.phase)
    }

    @Test
    fun `entrance finished moves the phase to rest`() = runTest {
        val coordinator = coordinator()
        coordinator.focus(1L)
        advanceTimeBy(settleMs)
        runCurrent()

        coordinator.entranceFinished(1L)

        assertEquals(
            ShowcaseMotionState(backgroundKey = 1L, contentKey = 1L, phase = ShowcaseMotionPhase.AtRest),
            coordinator.state.value
        )
    }

    @Test
    fun `entrance finished for a stale key is ignored`() = runTest {
        val coordinator = coordinator()
        coordinator.focus(1L)
        advanceTimeBy(settleMs)
        runCurrent()
        coordinator.focus(2L)

        coordinator.entranceFinished(1L)
        assertEquals(ShowcaseMotionPhase.Settling, coordinator.state.value.phase)

        advanceTimeBy(settleMs)
        runCurrent()
        coordinator.entranceFinished(1L)
        assertEquals(
            ShowcaseMotionState(backgroundKey = 2L, contentKey = 2L, phase = ShowcaseMotionPhase.Entering),
            coordinator.state.value
        )
    }

    @Test
    fun `a null game clears both keys without a settle`() = runTest {
        val coordinator = coordinator()
        coordinator.focus(1L)
        advanceTimeBy(settleMs)
        runCurrent()
        coordinator.entranceFinished(1L)

        coordinator.focus(null)

        assertEquals(ShowcaseMotionState(), coordinator.state.value)
        advanceTimeBy(settleMs)
        runCurrent()
        assertEquals(ShowcaseMotionState(), coordinator.state.value)
    }

    @Test
    fun `a zero settle enters on the next tick`() = runTest {
        val coordinator = ShowcaseMotionCoordinator(this, 0L)

        coordinator.focus(1L)
        runCurrent()

        assertEquals(
            ShowcaseMotionState(backgroundKey = 1L, contentKey = 1L, phase = ShowcaseMotionPhase.Entering),
            coordinator.state.value
        )
    }

    @Test
    fun `a new game during the entrance keeps the old content key until the settle ends`() = runTest {
        val coordinator = coordinator()
        coordinator.focus(1L)
        advanceTimeBy(settleMs)
        runCurrent()

        coordinator.focus(2L)

        assertEquals(
            ShowcaseMotionState(backgroundKey = 2L, contentKey = 1L, phase = ShowcaseMotionPhase.Settling),
            coordinator.state.value
        )
        advanceTimeBy(settleMs)
        runCurrent()
        assertEquals(
            ShowcaseMotionState(backgroundKey = 2L, contentKey = 2L, phase = ShowcaseMotionPhase.Entering),
            coordinator.state.value
        )
    }

    @Test
    fun `returning to the shown game during the settle period re-enters it`() = runTest {
        val coordinator = coordinator()
        coordinator.focus(1L)
        advanceTimeBy(settleMs)
        runCurrent()
        coordinator.entranceFinished(1L)

        coordinator.focus(2L)
        advanceTimeBy(settleMs / 2)
        runCurrent()
        coordinator.focus(1L)

        assertEquals(
            ShowcaseMotionState(backgroundKey = 1L, contentKey = 1L, phase = ShowcaseMotionPhase.Settling),
            coordinator.state.value
        )
        advanceTimeBy(settleMs)
        runCurrent()
        assertEquals(
            ShowcaseMotionState(backgroundKey = 1L, contentKey = 1L, phase = ShowcaseMotionPhase.Entering),
            coordinator.state.value
        )
    }

    @Test
    fun `the unidentified key settles and enters like any other`() = runTest {
        val coordinator = coordinator()
        coordinator.focus(UNIDENTIFIED_GAME_KEY)
        advanceTimeBy(settleMs)
        runCurrent()
        val entering = coordinator.state.value

        coordinator.focus(UNIDENTIFIED_GAME_KEY)
        advanceTimeBy(settleMs)
        runCurrent()

        assertEquals(
            ShowcaseMotionState(
                backgroundKey = UNIDENTIFIED_GAME_KEY,
                contentKey = UNIDENTIFIED_GAME_KEY,
                phase = ShowcaseMotionPhase.Entering
            ),
            entering
        )
        assertEquals(entering, coordinator.state.value)
    }
}
