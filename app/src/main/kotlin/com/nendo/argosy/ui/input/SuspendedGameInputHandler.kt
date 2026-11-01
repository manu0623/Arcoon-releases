package com.nendo.argosy.ui.input

import com.nendo.argosy.core.input.SoundType
import com.nendo.argosy.data.emulator.GameSessionManager
import com.nendo.argosy.domain.model.multitasking.TopScreenUiState
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SuspendedGameInputHandler @Inject constructor(
    private val gameSessionManager: GameSessionManager
) : InputHandler {

    private var currentUiState: TopScreenUiState = TopScreenUiState()

    fun updateState(uiState: TopScreenUiState) {
        this.currentUiState = uiState
    }

    /**
     * Maps Hardware X Button (SecondaryAction in Argosy taxonomy) to Thaw/Resume.
     */
    override fun onSecondaryAction(): InputResult {
        if (currentUiState.isCardCentered && currentUiState.suspendedSession != null) {
            gameSessionManager.resumeSession()
            return InputResult.handled(soundOverride = SoundType.SELECT)
        }
        return InputResult.UNHANDLED
    }

    /**
     * Maps Hardware Y Button (ContextMenu in Argosy taxonomy) to Force Close/Kill.
     */
    override fun onContextMenu(): InputResult {
        if (currentUiState.isCardCentered && currentUiState.suspendedSession != null) {
            gameSessionManager.closeSession()
            return InputResult.handled(soundOverride = SoundType.BACK)
        }
        return InputResult.UNHANDLED
    }

    /**
     * Confirms resume via A Button when card is focused.
     */
    override fun onConfirm(): InputResult {
        if (currentUiState.isCardCentered && currentUiState.suspendedSession != null) {
            gameSessionManager.resumeSession()
            return InputResult.handled(soundOverride = SoundType.SELECT)
        }
        return InputResult.UNHANDLED
    }

    /**
     * Maps B Button (Back) to resume the suspended game (reverse transition).
     */
    override fun onBack(): InputResult {
        if (currentUiState.isCardCentered && currentUiState.suspendedSession != null) {
            gameSessionManager.resumeSession()
            return InputResult.handled(soundOverride = SoundType.BACK)
        }
        return InputResult.UNHANDLED
    }
}
