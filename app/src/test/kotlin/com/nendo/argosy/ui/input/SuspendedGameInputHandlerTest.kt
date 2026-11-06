package com.nendo.argosy.ui.input

import android.content.ContextWrapper
import com.nendo.argosy.core.input.SoundType
import com.nendo.argosy.data.emulator.GameSessionManager
import com.nendo.argosy.domain.model.multitasking.ProcessState
import com.nendo.argosy.domain.model.multitasking.SuspendedGameSession
import com.nendo.argosy.domain.model.multitasking.TopScreenUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private class TestGameSessionManager : GameSessionManager(
    context = ContextWrapper(null),
    playSessionTracker = dagger.Lazy {
        io.mockk.mockk<com.nendo.argosy.data.emulator.PlaySessionTracker>(relaxed = true)
    }
) {
    var resumeCalled = false
    var closeCalled = false

    override fun resumeSession() {
        resumeCalled = true
    }

    override fun closeSession() {
        closeCalled = true
    }
}

class SuspendedGameInputHandlerTest {

    private val sampleSession = SuspendedGameSession(
        gameId = 42L,
        title = "Chrono Trigger",
        platformName = "SNES",
        packageName = "com.retroarch",
        processPid = 1234,
        startTimeMillis = 1000000L,
        elapsedTimeSeconds = 30L
    )

    @Test
    fun `when card is not focused input returns UNHANDLED`() {
        val sessionManager = TestGameSessionManager()
        val handler = SuspendedGameInputHandler(sessionManager)

        val unfocusedState = TopScreenUiState(
            suspendedSession = sampleSession,
            processState = ProcessState.SUSPENDED,
            highlightedGameId = 99L,
            isSuspendedGameFocused = false
        )
        handler.updateState(unfocusedState)

        val xResult = handler.onSecondaryAction()
        val yResult = handler.onContextMenu()
        val aResult = handler.onConfirm()

        assertFalse(xResult.handled)
        assertFalse(yResult.handled)
        assertFalse(aResult.handled)
        assertFalse(sessionManager.resumeCalled)
        assertFalse(sessionManager.closeCalled)
    }

    @Test
    fun `when card is focused X button resumes session`() {
        val sessionManager = TestGameSessionManager()
        val handler = SuspendedGameInputHandler(sessionManager)

        val focusedState = TopScreenUiState(
            suspendedSession = sampleSession,
            processState = ProcessState.SUSPENDED,
            highlightedGameId = 42L,
            isSuspendedGameFocused = true
        )
        handler.updateState(focusedState)

        val result = handler.onSecondaryAction()

        assertTrue(result.handled)
        assertEquals(SoundType.SELECT, result.soundOverride)
        assertTrue(sessionManager.resumeCalled)
    }

    @Test
    fun `when card is focused Y button closes session`() {
        val sessionManager = TestGameSessionManager()
        val handler = SuspendedGameInputHandler(sessionManager)

        val focusedState = TopScreenUiState(
            suspendedSession = sampleSession,
            processState = ProcessState.SUSPENDED,
            highlightedGameId = 42L,
            isSuspendedGameFocused = true
        )
        handler.updateState(focusedState)

        val result = handler.onContextMenu()

        assertTrue(result.handled)
        assertEquals(SoundType.BACK, result.soundOverride)
        assertTrue(sessionManager.closeCalled)
    }
}
