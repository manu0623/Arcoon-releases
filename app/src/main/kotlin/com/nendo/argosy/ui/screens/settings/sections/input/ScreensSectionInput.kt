package com.nendo.argosy.ui.screens.settings.sections.input

import com.nendo.argosy.core.input.SoundType
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.screens.settings.SettingsViewModel
import com.nendo.argosy.ui.screens.settings.sections.screensFocusMove

internal class ScreensSectionInput(
    private val viewModel: SettingsViewModel
) : InputHandler {

    override fun onUp(): InputResult = move(dx = 0, dy = -1)

    override fun onDown(): InputResult = move(dx = 0, dy = 1)

    override fun onLeft(): InputResult = move(dx = -1, dy = 0)

    override fun onRight(): InputResult = move(dx = 1, dy = 0)

    private fun move(dx: Int, dy: Int): InputResult {
        val state = viewModel.uiState.value
        val target = screensFocusMove(state.display.screens, state.focusedIndex, dx, dy)
            ?: return InputResult.handled(SoundType.BOUNDARY)
        viewModel.setFocusIndex(target)
        return InputResult.HANDLED
    }
}
