package com.nendo.argosy.ui.screens.settings

import com.nendo.argosy.core.input.SoundType
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.screens.settings.sections.input.BoxArtSectionInput
import com.nendo.argosy.ui.screens.settings.sections.input.BuiltinControlsSectionInput
import com.nendo.argosy.ui.screens.settings.sections.input.BuiltinSavesSectionInput
import com.nendo.argosy.ui.screens.settings.sections.input.BuiltinVideoSectionInput
import com.nendo.argosy.ui.screens.settings.sections.input.EmulatorsSectionInput
import com.nendo.argosy.ui.screens.settings.sections.input.FrameSectionInput
import com.nendo.argosy.ui.screens.settings.sections.input.PlatformDetailSectionInput
import com.nendo.argosy.ui.screens.settings.sections.input.AmbientLedSectionInput
import com.nendo.argosy.ui.screens.settings.sections.input.CoreOptionsSectionInput
import com.nendo.argosy.ui.screens.settings.sections.input.DisplaysSectionInput
import com.nendo.argosy.ui.screens.settings.sections.input.ScreensSectionInput
import com.nendo.argosy.ui.screens.settings.sections.input.DriversSectionInput
import com.nendo.argosy.ui.screens.settings.sections.input.InterfaceSectionInput
import com.nendo.argosy.ui.screens.settings.sections.input.LaunchTargetsSectionInput
import com.nendo.argosy.ui.screens.settings.sections.input.LightSectionsInput
import com.nendo.argosy.ui.screens.settings.sections.input.PlayTimeListSectionInput
import com.nendo.argosy.ui.screens.settings.sections.input.PlayTimeSectionInput
import com.nendo.argosy.ui.screens.settings.sections.input.ShaderStackSectionInput
import com.nendo.argosy.ui.screens.settings.sections.input.StorageCachesSectionInput
import com.nendo.argosy.ui.screens.settings.sections.input.StorageGamesSectionInput
import com.nendo.argosy.ui.screens.settings.sections.input.StorageMediaSectionInput
import com.nendo.argosy.ui.screens.settings.sections.input.StoragePlatformGamesSectionInput
import com.nendo.argosy.ui.screens.settings.sections.input.StorageSectionInput
import com.nendo.argosy.ui.screens.settings.sections.input.ThemeBackdropSectionInput
import com.nendo.argosy.ui.screens.settings.sections.input.ThemeFontsSectionInput
import com.nendo.argosy.ui.screens.settings.sections.input.ThemeMusicSectionInput
import com.nendo.argosy.ui.screens.settings.sections.input.ThemeSectionInput
import com.nendo.argosy.ui.screens.settings.sections.input.ThemeSoundsSectionInput

class SettingsInputHandler(
    private val viewModel: SettingsViewModel,
    private val onBackNavigation: () -> Unit,
    /** The page this screen was opened straight into from outside Settings, if any. */
    private val leaveAt: SettingsSection? = null
) : InputHandler {

    companion object {
        internal const val SLIDER_STEP = 10
        internal const val FONT_SCALE_STEP = 5
        internal const val HUE_STEP = com.nendo.argosy.ui.theme.AccentHue.STEP
    }

    private val modalRouter = ModalInputRouter(viewModel)
    private val lightHandler = LightSectionsInput(viewModel)

    private val handlers: Map<SettingsSection, InputHandler> = buildMap {
        put(SettingsSection.BUILTIN_VIDEO, BuiltinVideoSectionInput(viewModel))
        put(SettingsSection.PLATFORMS, EmulatorsSectionInput(viewModel))
        put(SettingsSection.PLATFORM_DETAIL, PlatformDetailSectionInput(viewModel))
        put(SettingsSection.BUILTIN_CONTROLS, BuiltinControlsSectionInput(viewModel))
        put(SettingsSection.BUILTIN_SAVES, BuiltinSavesSectionInput(viewModel))
        put(SettingsSection.BOX_ART, BoxArtSectionInput(viewModel))
        put(SettingsSection.INTERFACE, InterfaceSectionInput(viewModel))
        put(SettingsSection.DISPLAYS, DisplaysSectionInput(viewModel))
        put(SettingsSection.THEME, ThemeSectionInput(viewModel))
        put(SettingsSection.THEME_SOUNDS, ThemeSoundsSectionInput(viewModel))
        put(SettingsSection.THEME_MUSIC, ThemeMusicSectionInput(viewModel))
        put(SettingsSection.THEME_FONTS, ThemeFontsSectionInput(viewModel))
        put(SettingsSection.THEME_BACKDROP, ThemeBackdropSectionInput(viewModel))
        put(SettingsSection.AMBIENT_LED, AmbientLedSectionInput(viewModel))
        put(SettingsSection.SCREENS, ScreensSectionInput(viewModel))
        put(SettingsSection.LAUNCH_TARGETS, LaunchTargetsSectionInput(viewModel))
        put(SettingsSection.SHADER_STACK, ShaderStackSectionInput(viewModel))
        put(SettingsSection.CORE_OPTIONS, CoreOptionsSectionInput(viewModel))
        put(SettingsSection.STORAGE, StorageSectionInput(viewModel))
        put(SettingsSection.STORAGE_GAMES, StorageGamesSectionInput(viewModel))
        put(SettingsSection.STORAGE_MEDIA, StorageMediaSectionInput(viewModel))
        put(SettingsSection.STORAGE_PLATFORM_GAMES, StoragePlatformGamesSectionInput(viewModel))
        put(SettingsSection.STORAGE_CACHES, StorageCachesSectionInput(viewModel))
        put(SettingsSection.PLAY_TIME, PlayTimeSectionInput(viewModel))
        val playTimeListInput = PlayTimeListSectionInput(viewModel)
        put(SettingsSection.PLAY_TIME_PLATFORMS, playTimeListInput)
        put(SettingsSection.PLAY_TIME_DEVICES, playTimeListInput)
        put(SettingsSection.PLAY_TIME_GAMES, playTimeListInput)
        put(SettingsSection.DRIVERS, DriversSectionInput(viewModel))
        put(SettingsSection.FRAME_PICKER, FrameSectionInput(viewModel))
        for (s in listOf(
            SettingsSection.MAIN,
            SettingsSection.ACCOUNTS,
            SettingsSection.BIOS, SettingsSection.ROMM, SettingsSection.SAVES,
            SettingsSection.CONTROLLER_GRIP,
            SettingsSection.HOME_SCREEN, SettingsSection.PRESENTATION, SettingsSection.LIBRARY_VIEW,
            SettingsSection.NAVIGATION, SettingsSection.AUDIO, SettingsSection.SYNC_SETTINGS,
            SettingsSection.ABOUT, SettingsSection.STEAM_SETTINGS, SettingsSection.JELLYFIN,
            SettingsSection.CORE_MANAGEMENT,
            SettingsSection.SOCIAL, SettingsSection.BUILTIN_EMULATOR
        )) {
            put(s, lightHandler)
        }
    }

    /**
     * Cocoon's two-pane settings root. On the left, up/down change category and right or A step into
     * it; on the right, up/down stay inside the category and left or B step back out. Anything this
     * does not claim (A on a row, B on the left, modals) falls through to Argosy's own routing.
     */
    private fun cocoonRoot(method: InputMethod): InputResult? {
        val state = viewModel.uiState.value
        if (state.currentSection != SettingsSection.MAIN) return null
        modalRouter.intercept(state, method)?.let { return it }
        val ranges = com.nendo.argosy.ui.screens.settings.sections.mainSettingsCategoryRanges(state.controls)
        if (ranges.isEmpty()) return null
        val pane = com.nendo.argosy.ui.cocoon.CocoonSettingsFocus.paneFocused
        val category = ranges.indexOfFirst { state.focusedIndex in it.second }.coerceAtLeast(0)
        val range = ranges[category].second
        return if (!pane.value) {
            when (method) {
                InputMethod.UP, InputMethod.DOWN -> {
                    val next = category + if (method == InputMethod.UP) -1 else 1
                    if (next !in ranges.indices) {
                        InputResult.handled(SoundType.BOUNDARY)
                    } else {
                        viewModel.setFocusIndex(ranges[next].second.first)
                        InputResult.HANDLED
                    }
                }
                InputMethod.RIGHT, InputMethod.CONFIRM -> {
                    pane.value = true
                    if (state.focusedIndex !in range) viewModel.setFocusIndex(range.first)
                    InputResult.HANDLED
                }
                InputMethod.LEFT -> InputResult.handled(SoundType.BOUNDARY)
                else -> null
            }
        } else {
            when (method) {
                InputMethod.UP, InputMethod.DOWN -> {
                    val next = state.focusedIndex + if (method == InputMethod.UP) -1 else 1
                    if (next !in range) {
                        InputResult.handled(SoundType.BOUNDARY)
                    } else {
                        viewModel.setFocusIndex(next)
                        InputResult.HANDLED
                    }
                }
                InputMethod.LEFT, InputMethod.BACK -> {
                    pane.value = false
                    InputResult.HANDLED
                }
                else -> null
            }
        }
    }

    private fun dispatch(method: InputMethod, fallback: () -> InputResult): InputResult {
        cocoonRoot(method)?.let { return it }
        viewModel.exitTouchMode()
        val state = viewModel.uiState.value
        modalRouter.intercept(state, method)?.let { return it }
        val handler = handlers[state.currentSection]
        return handler?.let {
            val result = when (method) {
                InputMethod.UP -> it.onUp()
                InputMethod.DOWN -> it.onDown()
                InputMethod.LEFT -> it.onLeft()
                InputMethod.RIGHT -> it.onRight()
                InputMethod.CONFIRM -> it.onConfirm()
                InputMethod.BACK -> it.onBack()
                InputMethod.CONTEXT_MENU -> it.onContextMenu()
                InputMethod.SECONDARY_ACTION -> it.onSecondaryAction()
                InputMethod.PREV_SECTION -> it.onPrevSection()
                InputMethod.NEXT_SECTION -> it.onNextSection()
                InputMethod.PREV_TRIGGER -> it.onPrevTrigger()
                InputMethod.NEXT_TRIGGER -> it.onNextTrigger()
                InputMethod.MENU -> it.onMenu()
                InputMethod.SELECT -> it.onSelect()
                InputMethod.LEFT_STICK_CLICK -> it.onLeftStickClick()
                InputMethod.RIGHT_STICK_CLICK -> it.onRightStickClick()
                InputMethod.LONG_CONFIRM -> it.onLongConfirm()
            }
            if (result != InputResult.UNHANDLED) result else fallback()
        } ?: fallback()
    }

    override fun onUp(): InputResult = dispatch(InputMethod.UP) {
        if (viewModel.moveFocus(-1)) InputResult.HANDLED else InputResult.handled(SoundType.BOUNDARY)
    }

    override fun onDown(): InputResult = dispatch(InputMethod.DOWN) {
        if (viewModel.moveFocus(1)) InputResult.HANDLED else InputResult.handled(SoundType.BOUNDARY)
    }

    override fun onLeft(): InputResult = dispatch(InputMethod.LEFT) {
        InputResult.UNHANDLED
    }

    override fun onRight(): InputResult = dispatch(InputMethod.RIGHT) {
        InputResult.UNHANDLED
    }

    override fun onConfirm(): InputResult = dispatch(InputMethod.CONFIRM) {
        viewModel.handleConfirm()
    }

    override fun onLongConfirm(): InputResult = dispatch(InputMethod.LONG_CONFIRM) {
        InputResult.UNHANDLED
    }

    override fun onBack(): InputResult {
        cocoonRoot(InputMethod.BACK)?.let { return it }
        viewModel.exitTouchMode()
        val state = viewModel.uiState.value
        modalRouter.intercept(state, InputMethod.BACK)?.let { return it }
        // A modal or in-place prompt still owns Back first.
        if (viewModel.dismissOverlaysForBack()) return InputResult.HANDLED
        // Opened straight into this page from a folder or a game (Y ▸ platform settings): Back
        // leaves Settings for whatever sent it here. Stepping up to Settings' own list instead
        // read as being stuck on the page, because the list was not where the user came from.
        if (viewModel.atDirectEntry(leaveAt)) {
            onBackNavigation()
            return InputResult.HANDLED
        }
        return if (!viewModel.navigateBack()) {
            onBackNavigation()
            InputResult.HANDLED
        } else {
            InputResult.HANDLED
        }
    }

    override fun onContextMenu(): InputResult = dispatch(InputMethod.CONTEXT_MENU) {
        InputResult.UNHANDLED
    }

    override fun onSecondaryAction(): InputResult = dispatch(InputMethod.SECONDARY_ACTION) {
        InputResult.UNHANDLED
    }

    override fun onPrevSection(): InputResult = dispatch(InputMethod.PREV_SECTION) {
        bumperFallback()
    }

    override fun onNextSection(): InputResult = dispatch(InputMethod.NEXT_SECTION) {
        bumperFallback()
    }

    private fun bumperFallback(): InputResult =
        if (viewModel.uiState.value.currentSection == SettingsSection.MAIN) {
            InputResult.UNHANDLED
        } else {
            InputResult.HANDLED
        }

    override fun onPrevTrigger(): InputResult = dispatch(InputMethod.PREV_TRIGGER) {
        InputResult.UNHANDLED
    }

    override fun onNextTrigger(): InputResult = dispatch(InputMethod.NEXT_TRIGGER) {
        InputResult.UNHANDLED
    }

    override fun onSelect(): InputResult = dispatch(InputMethod.SELECT) {
        InputResult.UNHANDLED
    }

    override fun onMenu(): InputResult = InputResult.UNHANDLED

    override fun onLeftStickClick(): InputResult = InputResult.UNHANDLED

    override fun onRightStickClick(): InputResult = InputResult.UNHANDLED
}
