package com.nendo.argosy.ui.screens.settings.sections

import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.nendo.argosy.R
import com.nendo.argosy.ui.components.ActionPreference
import com.nendo.argosy.ui.components.CyclePreference
import com.nendo.argosy.ui.components.SwitchPreference
import com.nendo.argosy.ui.cocoon.CocoonBackup
import com.nendo.argosy.ui.cocoon.CocoonHomeStore
import com.nendo.argosy.ui.cocoon.CocoonNowPlayingStyle
import com.nendo.argosy.ui.cocoon.CocoonOptimizer
import com.nendo.argosy.ui.cocoon.theme.CocoonThemeEngine
import com.nendo.argosy.ui.screens.settings.SettingsSection
import com.nendo.argosy.ui.screens.settings.SettingsUiState
import com.nendo.argosy.ui.screens.settings.SettingsViewModel

internal enum class CocoonOption(val key: String, val labelRes: Int) {
    Cursor("cocoonCursor", R.string.cocoon_personal_cursor),
    Thickness("cocoonThickness", R.string.cocoon_personal_thickness),
    DockShape("cocoonDockShape", R.string.cocoon_personal_dock_shape),
    DockPosition("cocoonDockPosition", R.string.cocoon_personal_dock_position),
    DockBackground("cocoonDockBackground", R.string.cocoon_personal_dock_background),
    DockAutoHide("cocoonDockAutoHide", R.string.cocoon_personal_dock_autohide),
    NowPlaying("cocoonNowPlaying", R.string.cocoon_personal_now_playing),
    SingleScreen("cocoonSingleScreen", R.string.cocoon_personal_hero_size),
    HeroArt("cocoonHeroArt", R.string.cocoon_personal_hero),
    DetailsLayout("cocoonDetailsLayout", R.string.cocoon_personal_details_layout),
    Surface("cocoonSurface", R.string.cocoon_personal_surface),
    IconStyle("cocoonIconStyle", R.string.cocoon_personal_icon_style),
    Motion("cocoonMotion", R.string.cocoon_personal_motion),
    HomeMenu("cocoonHomeMenu", R.string.cocoon_personal_handle_size),
    RestoreHome("cocoonRestoreHome", R.string.cocoon_restore),
    BackupHome("cocoonBackupHome", R.string.cocoon_backup),
    Optimize("cocoonOptimize", R.string.cocoon_optimize_now),
    FreeMemory("cocoonFreeMemory", R.string.cocoon_free_memory)
}

private const val THEME_TOKEN = "THEME"

private val CURSOR_TOKENS = listOf(THEME_TOKEN, "ANIMATED", "OUTLINED", "ALTERNATIVE_ANIMATED", "ALTERNATIVE")
private val CURSOR_LABELS = listOf(
    R.string.cocoon_personal_theme,
    R.string.cocoon_cursor_animated,
    R.string.cocoon_cursor_outlined,
    R.string.cocoon_cursor_alt_animated,
    R.string.cocoon_cursor_alt
)
private val THICKNESS_TOKENS = listOf(THEME_TOKEN, "0.5", "0.6", "0.7", "0.8", "0.9", "1.0")
private val DOCK_SHAPE_TOKENS = listOf(THEME_TOKEN, "CIRCLE", "SQUIRCLE", "ROUNDED_SQUARE", "SQUARE")
private val DOCK_SHAPE_LABELS = listOf(
    R.string.cocoon_personal_theme,
    R.string.cocoon_shape_circle,
    R.string.cocoon_shape_squircle,
    R.string.cocoon_shape_rounded,
    R.string.cocoon_shape_square
)
private val DOCK_POSITION_TOKENS = listOf("TOP", "BOTTOM")
private val DOCK_POSITION_LABELS = listOf(R.string.cocoon_dock_top, R.string.cocoon_dock_bottom)
private val HERO_TOKENS = listOf("FULL", "VIGNETTE", "NONE")
private val HERO_LABELS = listOf(R.string.cocoon_hero_full, R.string.cocoon_hero_vignette, R.string.cocoon_hero_none)
private val SHARE_TOKENS = listOf("0.3", "0.45", "0.6")
private val SHARE_LABELS = listOf(R.string.cocoon_size_small, R.string.cocoon_size_medium, R.string.cocoon_size_large)
private val DETAILS_TOKENS = listOf("CLASSIC", "TABS", "SIDE", "TILES", "SPLIT")
private val DETAILS_LABELS = listOf(
    R.string.cocoon_details_layout_classic,
    R.string.cocoon_details_layout_tabs,
    R.string.cocoon_details_layout_side,
    R.string.cocoon_details_layout_tiles,
    R.string.cocoon_details_layout_split
)
private val SURFACE_TOKENS = listOf(THEME_TOKEN, "GLASS", "SOLID")
private val SURFACE_LABELS = listOf(
    R.string.cocoon_personal_theme,
    R.string.cocoon_surface_glass,
    R.string.cocoon_surface_solid
)
private val ICON_TOKENS = listOf("COLORED", "ACCENT", "FLAT")
private val ICON_LABELS = listOf(R.string.cocoon_icon_colored, R.string.cocoon_icon_accent, R.string.cocoon_icon_flat)
private val MOTION_TOKENS = listOf("COMFORTABLE", "SNAPPY", "REDUCED")
private val MOTION_LABELS = listOf(
    R.string.cocoon_motion_comfortable,
    R.string.cocoon_motion_snappy,
    R.string.cocoon_motion_reduced
)
private val HANDLE_TOKENS = listOf("SMALL", "MEDIUM", "LARGE")
private val HANDLE_LABELS = listOf(R.string.cocoon_size_small, R.string.cocoon_size_medium, R.string.cocoon_size_large)

internal val CocoonOption.isSwitch: Boolean
    get() = this == CocoonOption.DockBackground || this == CocoonOption.DockAutoHide

internal val CocoonOption.isAction: Boolean
    get() = this == CocoonOption.RestoreHome || this == CocoonOption.BackupHome ||
        this == CocoonOption.Optimize || this == CocoonOption.FreeMemory

private fun CocoonOption.tokens(): List<String> = when (this) {
    CocoonOption.Cursor -> CURSOR_TOKENS
    CocoonOption.Thickness -> THICKNESS_TOKENS
    CocoonOption.DockShape -> DOCK_SHAPE_TOKENS
    CocoonOption.DockPosition -> DOCK_POSITION_TOKENS
    CocoonOption.NowPlaying -> CocoonNowPlayingStyle.entries.map { it.name }
    CocoonOption.SingleScreen -> SHARE_TOKENS
    CocoonOption.HeroArt -> HERO_TOKENS
    CocoonOption.DetailsLayout -> DETAILS_TOKENS
    CocoonOption.Surface -> SURFACE_TOKENS
    CocoonOption.IconStyle -> ICON_TOKENS
    CocoonOption.Motion -> MOTION_TOKENS
    CocoonOption.HomeMenu -> HANDLE_TOKENS
    CocoonOption.DockBackground, CocoonOption.DockAutoHide,
    CocoonOption.RestoreHome, CocoonOption.BackupHome,
    CocoonOption.Optimize, CocoonOption.FreeMemory -> emptyList()
}

private fun CocoonOption.labels(): List<Int> = when (this) {
    CocoonOption.Cursor -> CURSOR_LABELS
    CocoonOption.Thickness -> THICKNESS_TOKENS.map { 0 }
    CocoonOption.DockShape -> DOCK_SHAPE_LABELS
    CocoonOption.DockPosition -> DOCK_POSITION_LABELS
    CocoonOption.NowPlaying -> CocoonNowPlayingStyle.entries.map { it.labelRes }
    CocoonOption.SingleScreen -> SHARE_LABELS
    CocoonOption.HeroArt -> HERO_LABELS
    CocoonOption.DetailsLayout -> DETAILS_LABELS
    CocoonOption.Surface -> SURFACE_LABELS
    CocoonOption.IconStyle -> ICON_LABELS
    CocoonOption.Motion -> MOTION_LABELS
    CocoonOption.HomeMenu -> HANDLE_LABELS
    CocoonOption.DockBackground, CocoonOption.DockAutoHide,
    CocoonOption.RestoreHome, CocoonOption.BackupHome,
    CocoonOption.Optimize, CocoonOption.FreeMemory -> emptyList()
}

private fun CocoonOption.currentToken(): String = when (this) {
    CocoonOption.Cursor -> CocoonThemeEngine.personal.value.cursorStyle ?: THEME_TOKEN
    CocoonOption.Thickness -> CocoonThemeEngine.personal.value.highlightThickness?.let { it.toString() } ?: THEME_TOKEN
    CocoonOption.DockShape -> CocoonThemeEngine.personal.value.dockIconShape ?: THEME_TOKEN
    CocoonOption.DockPosition -> if (CocoonHomeStore.dockBottom) "BOTTOM" else "TOP"
    CocoonOption.NowPlaying -> CocoonHomeStore.nowPlaying.name
    CocoonOption.SingleScreen -> SHARE_TOKENS.minByOrNull {
        kotlin.math.abs(it.toFloat() - CocoonHomeStore.heroShare)
    } ?: "0.45"
    CocoonOption.HeroArt -> CocoonHomeStore.heroStyle
    CocoonOption.DetailsLayout -> CocoonHomeStore.detailsLayout
    CocoonOption.Surface -> when (CocoonThemeEngine.personal.value.glass) {
        null -> THEME_TOKEN
        true -> "GLASS"
        false -> "SOLID"
    }
    CocoonOption.IconStyle -> CocoonHomeStore.iconStyle
    CocoonOption.Motion -> CocoonHomeStore.motion
    CocoonOption.HomeMenu -> CocoonHomeStore.handleSize
    CocoonOption.DockBackground, CocoonOption.DockAutoHide,
    CocoonOption.RestoreHome, CocoonOption.BackupHome,
    CocoonOption.Optimize, CocoonOption.FreeMemory -> ""
}

private fun CocoonOption.applyToken(vm: SettingsViewModel, token: String) {
    val personal = CocoonThemeEngine.personal.value
    when (this) {
        CocoonOption.Cursor -> CocoonThemeEngine.setPersonal(
            personal.copy(cursorStyle = token.takeIf { it != THEME_TOKEN })
        )
        CocoonOption.Thickness -> CocoonThemeEngine.setPersonal(
            personal.copy(highlightThickness = token.takeIf { it != THEME_TOKEN }?.toFloatOrNull())
        )
        CocoonOption.DockShape -> CocoonThemeEngine.setPersonal(
            personal.copy(dockIconShape = token.takeIf { it != THEME_TOKEN })
        )
        CocoonOption.DockPosition -> CocoonHomeStore.setDock(vm.context, bottom = token == "BOTTOM")
        CocoonOption.NowPlaying -> CocoonNowPlayingStyle.entries
            .firstOrNull { it.name == token }
            ?.let { CocoonHomeStore.setNowPlaying(vm.context, it) }
        CocoonOption.SingleScreen -> token.toFloatOrNull()?.let { CocoonHomeStore.setHeroShare(vm.context, it) }
        CocoonOption.HeroArt -> CocoonHomeStore.setHeroStyle(vm.context, token)
        CocoonOption.DetailsLayout -> CocoonHomeStore.setDetailsLayout(vm.context, token)
        CocoonOption.Surface -> CocoonThemeEngine.setPersonal(
            personal.copy(
                glass = when (token) {
                    "GLASS" -> true
                    "SOLID" -> false
                    else -> null
                }
            )
        )
        CocoonOption.IconStyle -> CocoonHomeStore.setIconStyle(vm.context, token)
        CocoonOption.Motion -> CocoonHomeStore.setMotion(vm.context, token)
        CocoonOption.HomeMenu -> CocoonHomeStore.setHandle(vm.context, size = token)
        CocoonOption.DockBackground, CocoonOption.DockAutoHide,
        CocoonOption.RestoreHome, CocoonOption.BackupHome,
        CocoonOption.Optimize, CocoonOption.FreeMemory -> Unit
    }
}

private fun stepToken(option: CocoonOption, direction: Int): String? {
    val tokens = option.tokens()
    if (tokens.isEmpty()) return null
    val index = tokens.indexOf(option.currentToken()).coerceAtLeast(0)
    return tokens[(index + direction).mod(tokens.size)]
}

internal fun routeCocoonOptionStep(vm: SettingsViewModel, key: String, direction: Int) {
    val option = CocoonOption.entries.firstOrNull { it.key == key } ?: return
    if (option.isAction) {
        routeCocoonOptionAction(vm, key)
        return
    }
    if (option.isSwitch) {
        routeCocoonOptionSwitch(vm, key, !cocoonSwitchValue(option))
        return
    }
    stepToken(option, direction)?.let { option.applyToken(vm, it) }
}

internal fun routeCocoonOptionSwitch(vm: SettingsViewModel, key: String, on: Boolean) {
    when (CocoonOption.entries.firstOrNull { it.key == key }) {
        CocoonOption.DockBackground -> CocoonHomeStore.setDock(vm.context, background = on)
        CocoonOption.DockAutoHide -> CocoonHomeStore.setDock(vm.context, autoHide = on)
        else -> Unit
    }
}

internal fun routeCocoonOptionAction(vm: SettingsViewModel, key: String) {
    val context = vm.context
    when (CocoonOption.entries.firstOrNull { it.key == key }) {
        CocoonOption.RestoreHome -> CocoonBackup.restore(context).fold(
            onSuccess = {
                CocoonHomeStore.load(context)
                Toast.makeText(context, R.string.cocoon_restore_done, Toast.LENGTH_SHORT).show()
            },
            onFailure = {
                Toast.makeText(context, context.getString(R.string.cocoon_backup_failed, it.message ?: ""), Toast.LENGTH_LONG).show()
            }
        )
        CocoonOption.BackupHome -> CocoonBackup.backup(context).fold(
            onSuccess = {
                Toast.makeText(context, context.getString(R.string.cocoon_backup_done, it.absolutePath), Toast.LENGTH_LONG).show()
            },
            onFailure = {
                Toast.makeText(context, context.getString(R.string.cocoon_backup_failed, it.message ?: ""), Toast.LENGTH_LONG).show()
            }
        )
        CocoonOption.Optimize -> CocoonOptimizer.start(context, force = true)
        CocoonOption.FreeMemory -> {
            CocoonOptimizer.freePictureMemory(context)
            Toast.makeText(context, R.string.cocoon_free_memory_done, Toast.LENGTH_SHORT).show()
        }
        else -> Unit
    }
}

internal fun routeCocoonOptionConfirm(vm: SettingsViewModel, key: String) {
    routeCocoonOptionStep(vm, key, 1)
}

private fun cocoonSwitchValue(option: CocoonOption): Boolean = when (option) {
    CocoonOption.DockBackground -> CocoonHomeStore.dockBackground
    CocoonOption.DockAutoHide -> CocoonHomeStore.dockAutoHide
    else -> false
}

internal fun CocoonOption.demoKind(): com.nendo.argosy.ui.cocoon.theme.CocoonPreviewKind? = when (this) {
    CocoonOption.Cursor, CocoonOption.Thickness, CocoonOption.DockShape, CocoonOption.DockPosition,
    CocoonOption.DockBackground, CocoonOption.DockAutoHide, CocoonOption.IconStyle,
    CocoonOption.Motion -> com.nendo.argosy.ui.cocoon.theme.CocoonPreviewKind.HOME
    CocoonOption.Surface -> com.nendo.argosy.ui.cocoon.theme.CocoonPreviewKind.SURFACE
    CocoonOption.NowPlaying, CocoonOption.SingleScreen, CocoonOption.HeroArt,
    CocoonOption.DetailsLayout -> com.nendo.argosy.ui.cocoon.theme.CocoonPreviewKind.TOP
    CocoonOption.HomeMenu -> com.nendo.argosy.ui.cocoon.theme.CocoonPreviewKind.HANDLE
    else -> null
}

internal fun cocoonDemoKindAtFocus(state: SettingsUiState): com.nendo.argosy.ui.cocoon.theme.CocoonPreviewKind? =
    CocoonOption.entries.firstOrNull { it.key == cocoonOptionKeyAtFocus(state) }?.demoKind()

@Composable
internal fun CocoonOptionDemo(owner: String, option: CocoonOption) {
    val kind = option.demoKind() ?: return
    com.nendo.argosy.ui.dualscreen.PresentOnCompanion(
        owner = com.nendo.argosy.ui.dualscreen.SlotOwner(owner),
        slot = com.nendo.argosy.ui.dualscreen.PresentationSlot.CocoonDemo(kind)
    )
}

internal fun cocoonOptionKeyAtFocus(state: SettingsUiState): String? = when (state.currentSection) {
    SettingsSection.HOME_SCREEN ->
        (homeScreenItemAtFocusIndex(state.focusedIndex, state.display) as? HomeScreenItem.CocoonRow)?.option?.key
    SettingsSection.PRESENTATION ->
        (presentationItemAtFocusIndex(state.focusedIndex, state.display.presentationStyle) as? PresentationItem.CocoonRow)?.option?.key
    SettingsSection.NAVIGATION ->
        (navigationItemAtFocusIndex(state.focusedIndex, state.controls) as? NavigationItem.CocoonRow)?.option?.key
    SettingsSection.ABOUT ->
        (aboutItemAtFocusIndex(
            state.focusedIndex,
            state.fileLoggingPath != null,
            aboutHasChangelog(state.updateCheck)
        ) as? AboutItem.CocoonRow)?.option?.key
    SettingsSection.THEME ->
        (themeItemAtFocusIndex(state.focusedIndex) as? ThemeItem.CocoonRow)?.option?.key
    else -> null
}

@Composable
private fun valueLabel(option: CocoonOption): String = when (option) {
    CocoonOption.Thickness -> option.currentToken().let {
        if (it == THEME_TOKEN) stringResource(R.string.cocoon_personal_theme) else "${(it.toFloat() * 100).toInt()}%"
    }
    else -> {
        val token = option.currentToken()
        val index = option.tokens().indexOf(token)
        val label = option.labels().getOrNull(index)
        when {
            label == null || label == 0 -> token
            else -> stringResource(label)
        }
    }
}

@Composable
internal fun CocoonOptionRow(
    option: CocoonOption,
    isFocused: Boolean,
    uiState: SettingsUiState,
    viewModel: SettingsViewModel
) {
    val title = stringResource(option.labelRes)
    when {
        option.isSwitch -> SwitchPreference(
            title = title,
            isEnabled = cocoonSwitchValue(option),
            isFocused = isFocused,
            onToggle = { viewModel.cocoonOptionSwitch(option.key, it) }
        )
        option.isAction -> ActionPreference(
            icon = when (option) {
                CocoonOption.Optimize -> Icons.Outlined.Speed
                CocoonOption.FreeMemory -> Icons.Outlined.DeleteSweep
                CocoonOption.BackupHome -> Icons.Outlined.Upload
                else -> Icons.Outlined.Download
            },
            title = title,
            subtitle = "",
            isFocused = isFocused,
            onClick = { viewModel.cocoonOptionAction(option.key) }
        )
        else -> CyclePreference(
            title = title,
            value = valueLabel(option),
            isFocused = isFocused,
            onClick = { viewModel.cocoonOptionStep(option.key, 1) },
            onPrev = { viewModel.cocoonOptionStep(option.key, -1) },
            pickerRequestToken = 0
        )
    }
}
