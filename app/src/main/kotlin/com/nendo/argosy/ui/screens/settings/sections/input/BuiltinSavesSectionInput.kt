package com.nendo.argosy.ui.screens.settings.sections.input

import com.nendo.argosy.core.emulator.LibretroSettingDef
import com.nendo.argosy.core.input.SoundType
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.screens.settings.BuiltinVideoState
import com.nendo.argosy.ui.screens.settings.SettingsUiState
import com.nendo.argosy.ui.screens.settings.SettingsViewModel
import com.nendo.argosy.ui.screens.settings.libretro.PlatformLibretroSettingsAccessor
import com.nendo.argosy.ui.screens.settings.sections.builtinSavesHasPathRows
import com.nendo.argosy.ui.screens.settings.sections.builtinSavesItemAtFocusIndex
import com.nendo.argosy.ui.screens.settings.sections.builtinSavesSettingsMaxFocusIndex

internal enum class BuiltinFolder { SAVE, STATE }

internal data class BuiltinFolderRow(
    val folder: BuiltinFolder,
    val platformId: Long?,
    val resettable: Boolean
)

internal fun focusedBuiltinFolderRow(state: SettingsUiState): BuiltinFolderRow? {
    val videoState = state.builtinVideo
    if (!builtinSavesHasPathRows(videoState, state.platformLibretro.platformSettings)) return null
    val isGlobal = videoState.isGlobalContext
    val platformContext = videoState.currentPlatformContext
    val platformSettings = platformContext?.let { state.platformLibretro.platformSettings[it.platformId] }
    val maxSettingsIndex = builtinSavesSettingsMaxFocusIndex(videoState)
    val platformId = if (isGlobal) null else platformContext?.platformId
    return when (state.focusedIndex) {
        maxSettingsIndex + 1 -> BuiltinFolderRow(
            folder = BuiltinFolder.SAVE,
            platformId = platformId,
            resettable = if (isGlobal) videoState.isCustomSavePath else platformSettings?.savePath != null
        )
        maxSettingsIndex + 2 -> BuiltinFolderRow(
            folder = BuiltinFolder.STATE,
            platformId = platformId,
            resettable = if (isGlobal) videoState.isCustomStatePath else platformSettings?.statePath != null
        )
        else -> null
    }
}

internal fun builtinSavesFocusedOverrideResettable(state: SettingsUiState): Boolean {
    val videoState = state.builtinVideo
    if (videoState.isGlobalContext) return false
    val setting = builtinSavesItemAtFocusIndex(state.focusedIndex, videoState) ?: return false
    return platformAccessor(state, onUpdate = { _, _ -> }).hasOverride(setting)
}

private fun platformAccessor(
    state: SettingsUiState,
    onUpdate: (LibretroSettingDef, String?) -> Unit
): PlatformLibretroSettingsAccessor {
    val videoState = state.builtinVideo
    val platformSettings = videoState.currentPlatformContext
        ?.let { state.platformLibretro.platformSettings[it.platformId] }
    return PlatformLibretroSettingsAccessor(
        platformSettings = platformSettings,
        globalState = videoState,
        onUpdate = onUpdate
    )
}

internal class BuiltinSavesSectionInput(
    private val viewModel: SettingsViewModel
) : InputHandler {

    override fun onSecondaryAction(): InputResult {
        val state = viewModel.uiState.value
        val row = focusedBuiltinFolderRow(state) ?: return resetFocusedSettingOverride(state)
        if (!row.resettable) return InputResult.HANDLED
        val platformId = row.platformId
        when (row.folder) {
            BuiltinFolder.SAVE -> if (platformId == null) {
                viewModel.resetBuiltinSavePath()
            } else {
                viewModel.resetPlatformBuiltinSavePath(platformId)
            }
            BuiltinFolder.STATE -> if (platformId == null) {
                viewModel.resetBuiltinStatePath()
            } else {
                viewModel.resetPlatformBuiltinStatePath(platformId)
            }
        }
        return InputResult.HANDLED
    }

    override fun onConfirm(): InputResult {
        val state = viewModel.uiState.value
        val videoState = state.builtinVideo
        val row = focusedBuiltinFolderRow(state)
        if (row != null) {
            openFolderBrowser(row)
            return InputResult.HANDLED
        }
        val setting = builtinSavesItemAtFocusIndex(state.focusedIndex, videoState) ?: return InputResult.UNHANDLED
        if (videoState.isGlobalContext) return confirmGlobal(setting, videoState)
        platformAccessor(state, onUpdate = { s, v -> viewModel.updatePlatformLibretroSetting(s, v) }).toggle(setting)
        return InputResult.handled(SoundType.TOGGLE)
    }

    private fun openFolderBrowser(row: BuiltinFolderRow) {
        val platformId = row.platformId
        when (row.folder) {
            BuiltinFolder.SAVE -> if (platformId == null) {
                viewModel.openBuiltinSavePathBrowser()
            } else {
                viewModel.openPlatformBuiltinSavePathBrowser(platformId)
            }
            BuiltinFolder.STATE -> if (platformId == null) {
                viewModel.openBuiltinStatePathBrowser()
            } else {
                viewModel.openPlatformBuiltinStatePathBrowser(platformId)
            }
        }
    }

    private fun confirmGlobal(setting: LibretroSettingDef, videoState: BuiltinVideoState): InputResult {
        when (setting) {
            LibretroSettingDef.AutoSaveState -> viewModel.setBuiltinAutoSaveState(!videoState.autoSaveState)
            LibretroSettingDef.AutoRestoreState -> viewModel.setBuiltinAutoRestoreState(!videoState.autoRestoreState)
            LibretroSettingDef.PreferNewerServerSave ->
                viewModel.setBuiltinPreferNewerServerSave(!videoState.preferNewerServerSave)
            LibretroSettingDef.HwCoreSaveStates ->
                viewModel.setBuiltinHwCoreSaveStates(!videoState.hwCoreSaveStatesEnabled)
            else -> return InputResult.UNHANDLED
        }
        return InputResult.handled(SoundType.TOGGLE)
    }

    private fun resetFocusedSettingOverride(state: SettingsUiState): InputResult {
        if (!builtinSavesFocusedOverrideResettable(state)) return InputResult.UNHANDLED
        val setting = builtinSavesItemAtFocusIndex(state.focusedIndex, state.builtinVideo)
            ?: return InputResult.UNHANDLED
        platformAccessor(state, onUpdate = { s, v -> viewModel.updatePlatformLibretroSetting(s, v) }).reset(setting)
        return InputResult.HANDLED
    }

    override fun onPrevSection(): InputResult = cyclePlatformContext(-1)

    override fun onNextSection(): InputResult = cyclePlatformContext(1)

    private fun cyclePlatformContext(direction: Int): InputResult {
        if (viewModel.uiState.value.builtinVideo.availablePlatforms.isEmpty()) return InputResult.UNHANDLED
        viewModel.cyclePlatformContext(direction)
        return InputResult.HANDLED
    }
}
