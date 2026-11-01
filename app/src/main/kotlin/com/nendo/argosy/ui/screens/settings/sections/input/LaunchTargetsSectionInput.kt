package com.nendo.argosy.ui.screens.settings.sections.input

import com.nendo.argosy.core.input.SoundType
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.screens.settings.LaunchTargetsLayout
import com.nendo.argosy.ui.screens.settings.SettingsViewModel
import com.nendo.argosy.ui.screens.settings.launchTargetsLayout

internal class LaunchTargetsSectionInput(
    private val viewModel: SettingsViewModel
) : InputHandler {

    private fun layout(): LaunchTargetsLayout = launchTargetsLayout(viewModel.uiState.value.launchTargets)

    override fun onPrevSection(): InputResult =
        if (viewModel.jumpToPrevSection(layout().jumpSections)) {
            InputResult.HANDLED
        } else {
            InputResult.handled(SoundType.BOUNDARY)
        }

    override fun onNextSection(): InputResult =
        if (viewModel.jumpToNextSection(layout().jumpSections)) {
            InputResult.HANDLED
        } else {
            InputResult.handled(SoundType.BOUNDARY)
        }

    override fun onSecondaryAction(): InputResult {
        val entry = layout().entryAt(viewModel.uiState.value.focusedIndex)
            ?.takeIf { it.isSet }
            ?: return InputResult.handled(SoundType.SILENT)
        viewModel.resetLaunchTarget(entry.key)
        return InputResult.HANDLED
    }
}
