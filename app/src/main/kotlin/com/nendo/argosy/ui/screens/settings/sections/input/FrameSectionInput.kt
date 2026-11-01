package com.nendo.argosy.ui.screens.settings.sections.input

import com.nendo.argosy.libretro.frame.FrameRegistry
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.screens.settings.SettingsViewModel

internal class FrameSectionInput(
    private val viewModel: SettingsViewModel
) : InputHandler {

    override fun onSecondaryAction(): InputResult {
        val frames = viewModel.getFrameRegistry().getAllFrames()
        val focused = frames.getOrNull(viewModel.uiState.value.focusedIndex - 2)
            ?: return InputResult.UNHANDLED
        if (focused.source != FrameRegistry.Source.CUSTOM) return InputResult.UNHANDLED
        viewModel.requestCustomFrameRemoval(focused.id)
        return InputResult.HANDLED
    }
}
