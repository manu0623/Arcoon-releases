package com.nendo.argosy.ui.components.recap

import com.nendo.argosy.core.input.SoundType
import com.nendo.argosy.data.emulator.LaunchProgressTracker
import com.nendo.argosy.domain.model.LaunchProgress
import com.nendo.argosy.domain.model.LaunchRecap
import com.nendo.argosy.domain.model.LaunchStep
import com.nendo.argosy.domain.model.MonthlyDigest
import com.nendo.argosy.domain.model.SessionUnlock
import com.nendo.argosy.domain.usecase.recap.GetMonthlyRecapUseCase
import com.nendo.argosy.ui.components.LaunchOverlayViewModel
import com.nendo.argosy.ui.input.InputResult
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.YearMonth

@OptIn(ExperimentalCoroutinesApi::class)
class RecapScrollInputTest {

    private fun unlock(id: String) = SessionUnlock(
        badgeId = id,
        title = null,
        points = null,
        badgePath = "/badge/$id.png",
        isHardcore = false,
        unlockedAt = Instant.parse("2026-10-01T10:00:00Z"),
        description = null,
        type = null,
        gameTitle = null
    )

    private val recapStep = LaunchStep.Recap(
        LaunchRecap(gameTitle = "Game", lastSession = null, unlocks = listOf(unlock("a"), unlock("b")))
    )

    private val dispatcher = UnconfinedTestDispatcher()
    private val controllerScope = CoroutineScope(SupervisorJob() + dispatcher)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        controllerScope.cancel()
        Dispatchers.resetMain()
    }

    private fun recapController(useCase: GetMonthlyRecapUseCase) =
        MonthlyRecapController(mockk(relaxed = true), useCase, emptySet(), controllerScope, dispatcher)

    private fun launchOverlay(step: LaunchStep): Pair<LaunchOverlayViewModel, LaunchRecapCursor> {
        val tracker = mockk<LaunchProgressTracker>()
        every { tracker.progress } returns MutableStateFlow(LaunchProgress("Game", step))
        val cursor = LaunchRecapCursor()
        return LaunchOverlayViewModel(tracker, cursor) to cursor
    }

    private fun openMonthlyRecap(): MonthlyRecapController {
        val useCase = mockk<GetMonthlyRecapUseCase>()
        coEvery { useCase(any(), any()) } coAnswers { awaitCancellation() }
        val controller = recapController(useCase)
        controller.openLastMonth()
        return controller
    }

    @Test
    fun `dpad and right stick vertical scroll the pre-launch recap body`() {
        val (viewModel, cursor) = launchOverlay(recapStep)
        val input = viewModel.inputHandler

        input.onDown()
        assertEquals(1, cursor.state.value.bodyScrolls)
        assertEquals(1, cursor.state.value.bodyScrollDirection)
        input.onUp()
        assertEquals(-1, cursor.state.value.bodyScrollDirection)
        input.onRightStickDown()
        assertEquals(1, cursor.state.value.bodyScrollDirection)
        input.onRightStickUp()
        assertEquals(4, cursor.state.value.bodyScrolls)
        assertEquals(-1, cursor.state.value.bodyScrollDirection)
    }

    @Test
    fun `horizontal input never scrolls the pre-launch recap body`() {
        val (viewModel, cursor) = launchOverlay(recapStep)
        val input = viewModel.inputHandler

        input.onLeft()
        input.onRight()
        input.onRightStickRight()
        assertEquals(0, cursor.state.value.bodyScrolls)
        assertEquals(1, cursor.state.value.currentIndex)
    }

    @Test
    fun `vertical input outside a recap step leaves the recap cursor alone`() {
        val (viewModel, cursor) = launchOverlay(LaunchStep.PreparingUi)
        val input = viewModel.inputHandler

        input.onDown()
        input.onRightStickDown()
        assertEquals(0, cursor.state.value.bodyScrolls)
    }

    @Test
    fun `dpad and right stick vertical scroll the monthly recap body`() {
        val controller = openMonthlyRecap()
        val input = controller.inputHandler

        input.onDown()
        input.onRightStickDown()
        assertEquals(2, controller.cursor.value.bodyScrolls)
        assertEquals(1, controller.cursor.value.bodyScrollDirection)
        input.onUp()
        input.onRightStickUp()
        assertEquals(4, controller.cursor.value.bodyScrolls)
        assertEquals(-1, controller.cursor.value.bodyScrollDirection)
        controller.close()
    }

    @Test
    fun `horizontal input never scrolls the monthly recap body`() {
        val controller = openMonthlyRecap()
        val input = controller.inputHandler

        input.onLeft()
        input.onRight()
        input.onRightStickLeft()
        input.onRightStickRight()
        assertEquals(0, controller.cursor.value.bodyScrolls)
        controller.close()
    }

    private fun readyMonthlyRecap(): MonthlyRecapController {
        val month = YearMonth.of(2026, 9)
        val digest = MonthlyDigest(
            month = month,
            totalActiveMs = 0L,
            sessionCount = 0,
            longestSessionMs = 0L,
            days = emptyList(),
            topGames = emptyList(),
            unlocks = listOf(unlock("a"), unlock("b")),
            awards = emptyList()
        )
        val useCase = mockk<GetMonthlyRecapUseCase>()
        coEvery { useCase(any(), any()) } returns digest
        val controller = recapController(useCase)
        controller.openLastMonth()
        assertTrue(controller.state.value is MonthlyRecapCardState.Ready)
        return controller
    }

    @Test
    fun `dpad left and right scrub the pre-launch recap unlocks like the right stick`() {
        val (viewModel, cursor) = launchOverlay(recapStep)
        val input = viewModel.inputHandler

        assertEquals(SoundType.NAVIGATE, input.onRight().soundOverride)
        assertEquals(1, cursor.state.value.currentIndex)
        assertEquals(1, cursor.state.value.scrubs)
        assertEquals(SoundType.BOUNDARY, input.onRight().soundOverride)
        assertEquals(1, cursor.state.value.currentIndex)
        assertEquals(SoundType.NAVIGATE, input.onLeft().soundOverride)
        assertEquals(0, cursor.state.value.currentIndex)
        assertEquals(SoundType.BOUNDARY, input.onLeft().soundOverride)
        assertEquals(0, cursor.state.value.currentIndex)
        assertEquals(4, cursor.state.value.scrubs)
        assertEquals(SoundType.NAVIGATE, input.onRightStickRight().soundOverride)
        assertEquals(1, cursor.state.value.currentIndex)
    }

    @Test
    fun `dpad left and right outside a recap step leave the recap cursor alone`() {
        val (viewModel, cursor) = launchOverlay(LaunchStep.PreparingUi)
        val input = viewModel.inputHandler

        assertEquals(InputResult.HANDLED, input.onLeft())
        assertEquals(InputResult.HANDLED, input.onRight())
        assertEquals(RecapCursor(), cursor.state.value)
    }

    @Test
    fun `dpad left and right scrub the monthly recap unlocks like the right stick`() {
        val controller = readyMonthlyRecap()
        val input = controller.inputHandler

        assertEquals(SoundType.NAVIGATE, input.onRight().soundOverride)
        assertEquals(1, controller.cursor.value.currentIndex)
        assertEquals(1, controller.cursor.value.scrubs)
        assertEquals(SoundType.BOUNDARY, input.onRight().soundOverride)
        assertEquals(1, controller.cursor.value.currentIndex)
        assertEquals(SoundType.NAVIGATE, input.onLeft().soundOverride)
        assertEquals(0, controller.cursor.value.currentIndex)
        assertEquals(SoundType.BOUNDARY, input.onLeft().soundOverride)
        assertEquals(0, controller.cursor.value.currentIndex)
        assertEquals(4, controller.cursor.value.scrubs)
        assertEquals(SoundType.NAVIGATE, input.onRightStickRight().soundOverride)
        assertEquals(1, controller.cursor.value.currentIndex)
        controller.close()
    }

    @Test
    fun `dpad left and right on a loading monthly recap move nothing`() {
        val controller = openMonthlyRecap()
        val input = controller.inputHandler

        assertEquals(SoundType.SILENT, input.onLeft().soundOverride)
        assertEquals(SoundType.SILENT, input.onRight().soundOverride)
        assertEquals(RecapCursor(), controller.cursor.value)
        controller.close()
    }

    @Test
    fun `a closed monthly recap takes no body scroll`() {
        val controller = recapController(mockk(relaxed = true))

        controller.inputHandler.onDown()
        assertEquals(0, controller.cursor.value.bodyScrolls)
    }
}
