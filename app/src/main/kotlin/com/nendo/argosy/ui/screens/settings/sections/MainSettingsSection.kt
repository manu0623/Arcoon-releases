package com.nendo.argosy.ui.screens.settings.sections

import android.content.Intent
import android.provider.Settings
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DeveloperBoard
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.nendo.argosy.R
import com.nendo.argosy.ui.common.resolve
import com.nendo.argosy.ui.components.FocusedScroll
import com.nendo.argosy.ui.components.NavigationPreference
import com.nendo.argosy.ui.screens.settings.ConnectionStatus
import com.nendo.argosy.ui.screens.settings.ControlsState
import com.nendo.argosy.ui.screens.settings.EmulatorState
import com.nendo.argosy.ui.screens.settings.SettingsSection
import com.nendo.argosy.ui.screens.settings.SocialAuthStatus
import com.nendo.argosy.ui.screens.settings.SettingsUiState
import com.nendo.argosy.ui.screens.settings.SettingsViewModel
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import com.nendo.argosy.ui.screens.settings.components.LocalSettingsTouchFocus
import com.nendo.argosy.ui.screens.settings.components.SectionHeader
import com.nendo.argosy.ui.screens.settings.menu.SettingsLayout
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.util.formatClockDateTime

internal sealed class MainSettingsItem(
    val key: String,
    val icon: ImageVector,
    @StringRes val titleRes: Int,
    val section: String,
    val visibleWhen: (ControlsState) -> Boolean = { true }
) {
    val isFocusable: Boolean get() = this !is Header

    class Header(key: String, section: String, @StringRes titleRes: Int) :
        MainSettingsItem(key, Icons.Default.Info, titleRes, section)

    data object ManagedInstallers : MainSettingsItem(
        "managedInstallers",
        Icons.Default.Download,
        R.string.settings_main_installers_title,
        "system"
    )

    data object Theme :
        MainSettingsItem("theme", Icons.Default.Palette, R.string.settings_main_theme_title, "launcher")
    data object Interface : MainSettingsItem(
        "interface",
        Icons.Default.Dashboard,
        R.string.settings_main_interface_title,
        "launcher"
    )
    data object Navigation : MainSettingsItem(
        "navigation",
        Icons.Default.TouchApp,
        R.string.settings_main_navigation_title,
        "launcher"
    )
    data object Audio :
        MainSettingsItem("audio", Icons.Default.GraphicEq, R.string.settings_main_audio_title, "launcher")
    data object Displays : MainSettingsItem(
        "displays",
        Icons.Default.Devices,
        R.string.settings_main_displays_title,
        "launcher"
    )

    data object BuiltinEmulator : MainSettingsItem(
        "builtin_emulator",
        Icons.Default.Build,
        R.string.settings_main_builtin_emulator_title,
        "gameplay"
    )
    data object Saves :
        MainSettingsItem("saves", Icons.Default.Save, R.string.settings_main_saves_title, "gameplay")
    data object RetroAchievements : MainSettingsItem(
        "retroAchievements",
        Icons.Default.EmojiEvents,
        R.string.settings_main_retroachievements_title,
        "connections"
    )
    data object Bios :
        MainSettingsItem("bios", Icons.Default.Memory, R.string.settings_main_bios_title, "gameplay")
    data object Drivers : MainSettingsItem(
        "drivers",
        Icons.Default.DeveloperBoard,
        R.string.settings_main_drivers_title,
        "gameplay"
    )

    data object Platforms : MainSettingsItem(
        "platforms",
        Icons.Default.Gamepad,
        R.string.settings_main_platforms_title,
        "library"
    )
    data object Storage :
        MainSettingsItem("storage", Icons.Default.Storage, R.string.settings_main_storage_title, "library")
    data object PlayTime : MainSettingsItem(
        "playTime",
        Icons.Default.Timer,
        R.string.settings_main_play_time_title,
        "library"
    )

    data object RomM :
        MainSettingsItem("romm", Icons.Default.Dns, R.string.settings_main_romm_title, "connections")
    data object Steam : MainSettingsItem(
        "steam",
        Icons.Default.CloudQueue,
        R.string.settings_main_steam_title,
        "connections"
    )
    data object Jellyfin : MainSettingsItem(
        "jellyfin",
        Icons.Default.Movie,
        R.string.settings_main_jellyfin_title,
        "connections"
    )
    data object Social :
        MainSettingsItem("social", Icons.Default.Group, R.string.settings_main_social_title, "connections")

    /**
     * Jingles: the home board's sounds. An Integrations row of its own, directly under Social,
     * because nothing about it is social and a page reached from inside Social reads as one.
     */
    data object Jingles :
        MainSettingsItem("jingles", Icons.Default.MusicNote, R.string.cocoon_jingles, "connections")

    data object SteamGridDb : MainSettingsItem(
        "steamGridDb",
        Icons.Default.Movie,
        R.string.cocoon_steamgriddb_title,
        "connections"
    )

    data object RomMedia : MainSettingsItem(
        "romMedia",
        Icons.Default.Storage,
        R.string.cocoon_rom_media_title,
        "connections"
    )

    data object Permissions : MainSettingsItem(
        "permissions",
        Icons.Default.Security,
        R.string.settings_main_permissions_title,
        "system"
    )
    data object DeviceSettings : MainSettingsItem(
        "device",
        Icons.Default.PhoneAndroid,
        R.string.settings_main_device_title,
        "system"
    )
    data object About :
        MainSettingsItem("about", Icons.Default.Info, R.string.settings_main_about_title, "about")

    /** Opens Cocoon's settings search; it belongs here rather than on Home, which searches games. */
    data object SearchSettings : MainSettingsItem(
        "searchSettings",
        Icons.Default.Search,
        R.string.cocoon_search_settings,
        "launcher"
    )

    companion object {
        val ALL: List<MainSettingsItem>
            // Grouped in the order of Cocoon's settings navigation: Personalization, Library & data,
            // Argosy's own Gameplay & saves, Integrations, System & Advance, About.
            get() = listOf(
                Header("launcherHeader", "launcher", R.string.settings_main_section_launcher),
                SearchSettings, Theme, Interface, Navigation, Audio, Displays,
                Header("libraryHeader", "library", R.string.settings_main_section_library),
                Platforms, Storage, PlayTime,
                Header("gameplayHeader", "gameplay", R.string.settings_main_section_gameplay),
                BuiltinEmulator, Saves, Bios, Drivers,
                Header("connectionsHeader", "connections", R.string.settings_main_section_connections),
                RomM, RetroAchievements, Steam, Jellyfin, Social, Jingles, SteamGridDb, RomMedia,
                Header("systemHeader", "system", R.string.settings_main_section_system),
                Permissions, ManagedInstallers, DeviceSettings,
                Header("aboutHeader", "about", R.string.cocoon_settings_about),
                About
            )
    }
}

private val mainSettingsLayout = SettingsLayout<MainSettingsItem, ControlsState>(
    allItems = MainSettingsItem.ALL,
    isFocusable = { it.isFocusable },
    visibleWhen = { item, state -> item.visibleWhen(state) },
    sectionOf = { it.section }
)

internal fun mainSettingsMaxFocusIndex(controls: ControlsState): Int =
    mainSettingsLayout.maxFocusIndex(controls)

internal fun mainSettingsItemAtFocusIndex(index: Int, controls: ControlsState): MainSettingsItem? =
    mainSettingsLayout.itemAtFocusIndex(index, controls)

internal fun mainSettingsSections(controls: ControlsState) = mainSettingsLayout.buildSections(controls)

/**
 * The settings root's categories in display order, each with the focus indices of its rows. The
 * Cocoon two-pane root moves between these on the left and within one of them on the right.
 */
internal fun mainSettingsCategoryRanges(controls: ControlsState): List<Pair<String, IntRange>> {
    val max = mainSettingsLayout.maxFocusIndex(controls)
    val result = mutableListOf<Pair<String, IntRange>>()
    var start = 0
    var current: String? = null
    for (i in 0..max) {
        val section = mainSettingsLayout.itemAtFocusIndex(i, controls)?.section ?: continue
        if (section != current) {
            current?.let { result += it to (start until i) }
            current = section
            start = i
        }
    }
    current?.let { result += it to (start..max) }
    return result
}


@Composable
fun MainSettingsSection(uiState: SettingsUiState, viewModel: SettingsViewModel) {
    val context = LocalContext.current
    val listState = rememberLazyListState()

    val controls = uiState.controls

    val visibleItems = remember { mainSettingsLayout.visibleItems(controls) }

    fun isFocused(item: MainSettingsItem): Boolean =
        uiState.focusedIndex == mainSettingsLayout.focusIndexOf(item, controls)

    // Settings search: opened from its own row (or A on it), closed with B.
    val searchOpen by com.nendo.argosy.ui.cocoon.CocoonSettingsSearchState.open.collectAsState()

    fun getSubtitle(item: MainSettingsItem): String = when (item) {
        is MainSettingsItem.Header -> ""
        MainSettingsItem.ManagedInstallers ->
            context.getString(R.string.settings_main_installers_subtitle)
        MainSettingsItem.DeviceSettings -> context.getString(R.string.settings_main_device_subtitle)
        MainSettingsItem.RomM -> when (uiState.server.connectionStatus) {
            ConnectionStatus.NOT_CONFIGURED ->
                context.getString(R.string.settings_main_romm_subtitle_unconfigured)
            ConnectionStatus.CHECKING -> context.getString(R.string.settings_main_romm_subtitle_checking)
            ConnectionStatus.OFFLINE -> context.getString(R.string.settings_main_romm_subtitle_offline)
            ConnectionStatus.ONLINE -> {
                uiState.server.lastRommSync?.let { instant ->
                    context.getString(
                        R.string.settings_main_romm_subtitle_last_sync,
                        formatClockDateTime(context, instant.toEpochMilli())
                    )
                } ?: context.getString(R.string.settings_main_romm_subtitle_never_synced)
            }
        }
        MainSettingsItem.Saves -> if (uiState.syncSettings.saveSyncEnabled) {
            context.getString(R.string.settings_main_saves_subtitle_on)
        } else {
            context.getString(R.string.settings_main_saves_subtitle_off)
        }
        MainSettingsItem.RetroAchievements -> if (uiState.retroAchievements.isLoggedIn) {
            context.getString(
                R.string.settings_main_retroachievements_subtitle_logged_in,
                uiState.retroAchievements.username
            )
        } else {
            context.getString(R.string.settings_main_retroachievements_subtitle_logged_out)
        }
        MainSettingsItem.Storage -> if (uiState.storage.downloadedGamesCount > 0) {
            context.resources.getQuantityString(
                R.plurals.settings_main_storage_subtitle_downloaded,
                uiState.storage.downloadedGamesCount,
                uiState.storage.downloadedGamesCount
            )
        } else {
            context.getString(R.string.settings_main_storage_subtitle_empty)
        }
        MainSettingsItem.PlayTime -> context.getString(R.string.settings_main_play_time_subtitle)
        MainSettingsItem.Theme -> context.getString(R.string.settings_main_theme_subtitle)
        MainSettingsItem.Interface -> context.getString(R.string.settings_main_interface_subtitle)
        MainSettingsItem.Navigation -> context.getString(R.string.settings_main_navigation_subtitle)
        MainSettingsItem.Audio -> context.getString(R.string.settings_main_audio_subtitle)
        MainSettingsItem.Displays -> context.getString(R.string.settings_main_displays_subtitle)
        MainSettingsItem.Platforms -> context.resources.getQuantityString(
            R.plurals.settings_main_platforms_subtitle,
            uiState.emulators.platforms.size,
            uiState.emulators.platforms.size
        )
        MainSettingsItem.BuiltinEmulator -> if (uiState.emulators.builtinLibretroEnabled) {
            context.getString(R.string.settings_main_builtin_emulator_subtitle_on)
        } else {
            context.getString(R.string.settings_main_builtin_emulator_subtitle_off)
        }
        MainSettingsItem.Bios -> uiState.bios.summaryText.resolve(context)
        MainSettingsItem.Drivers -> uiState.drivers.summary.resolve(context)
        MainSettingsItem.Steam -> if (uiState.steam.username != null) {
            uiState.steam.username
        } else {
            context.getString(R.string.settings_main_steam_subtitle_signed_out)
        }
        MainSettingsItem.Jellyfin -> when {
            !uiState.jellyfin.hasServer ->
                context.getString(R.string.settings_main_jellyfin_subtitle_unconfigured)
            uiState.jellyfin.isSignedIn -> uiState.jellyfin.userName.takeIf { it.isNotBlank() }
                ?.let { context.getString(R.string.settings_main_jellyfin_subtitle_signed_in_as, it) }
                ?: context.getString(R.string.settings_main_jellyfin_subtitle_signed_in)
            else -> context.getString(R.string.settings_main_jellyfin_subtitle_signed_out)
        }
        MainSettingsItem.Social -> when (uiState.social.authStatus) {
            SocialAuthStatus.CONNECTED -> context.getString(
                R.string.settings_main_social_subtitle_linked,
                uiState.social.displayName ?: uiState.social.username
            )
            SocialAuthStatus.CONNECTING -> context.getString(R.string.settings_main_social_subtitle_connecting)
            else -> context.getString(R.string.settings_main_social_subtitle_unlinked)
        }
        MainSettingsItem.Jingles -> context.getString(R.string.cocoon_jingles_subtitle)
        MainSettingsItem.SteamGridDb -> context.getString(R.string.cocoon_steamgriddb_subtitle)
        MainSettingsItem.RomMedia -> context.getString(R.string.cocoon_rom_media_subtitle)
        MainSettingsItem.Permissions -> if (uiState.permissions.allGranted) {
            context.getString(R.string.settings_main_permissions_subtitle_all)
        } else {
            context.getString(
                R.string.settings_main_permissions_subtitle_partial,
                uiState.permissions.grantedCount,
                uiState.permissions.totalCount
            )
        }
        MainSettingsItem.About -> context.getString(R.string.settings_main_about_subtitle, uiState.appVersion)
        MainSettingsItem.SearchSettings -> context.getString(R.string.cocoon_search_settings_sub)
    }

    fun handleClick(item: MainSettingsItem) {
        if (item !is MainSettingsItem.Header) {
            viewModel.setFocusIndex(mainSettingsLayout.focusIndexOf(item, controls))
        }
        when (item) {
            is MainSettingsItem.Header -> Unit
            MainSettingsItem.SearchSettings ->
                com.nendo.argosy.ui.cocoon.CocoonSettingsSearchState.open.value = true
            MainSettingsItem.ManagedInstallers ->
                viewModel.navigateToSection(SettingsSection.MANAGED_INSTALLERS)
            MainSettingsItem.DeviceSettings -> context.startActivity(Intent(Settings.ACTION_SETTINGS))
            MainSettingsItem.RomM -> viewModel.navigateToSection(SettingsSection.ROMM)
            MainSettingsItem.Saves -> viewModel.navigateToSection(SettingsSection.SAVES)
            MainSettingsItem.RetroAchievements -> viewModel.navigateToSection(SettingsSection.RETRO_ACHIEVEMENTS)
            MainSettingsItem.Storage -> viewModel.navigateToSection(SettingsSection.STORAGE)
            MainSettingsItem.PlayTime -> viewModel.navigateToSection(SettingsSection.PLAY_TIME)
            MainSettingsItem.Theme -> viewModel.navigateToSection(SettingsSection.THEME)
            MainSettingsItem.Interface -> viewModel.navigateToSection(SettingsSection.INTERFACE)
            MainSettingsItem.Navigation -> viewModel.navigateToSection(SettingsSection.NAVIGATION)
            MainSettingsItem.Audio -> viewModel.navigateToSection(SettingsSection.AUDIO)
            MainSettingsItem.Displays -> viewModel.navigateToSection(SettingsSection.DISPLAYS)
            MainSettingsItem.Platforms -> viewModel.navigateToSection(SettingsSection.PLATFORMS)
            MainSettingsItem.BuiltinEmulator -> viewModel.navigateToSection(SettingsSection.BUILTIN_EMULATOR)
            MainSettingsItem.Bios -> viewModel.navigateToSection(SettingsSection.BIOS)
            MainSettingsItem.Drivers -> viewModel.navigateToSection(SettingsSection.DRIVERS)
            MainSettingsItem.Steam -> viewModel.navigateToSection(SettingsSection.STEAM_SETTINGS)
            MainSettingsItem.Jellyfin -> viewModel.navigateToSection(SettingsSection.JELLYFIN)
            MainSettingsItem.Social -> viewModel.navigateToSection(SettingsSection.SOCIAL)
            MainSettingsItem.Jingles ->
                com.nendo.argosy.ui.cocoon.theme.CocoonThemeEngine.jinglesOpen.value = true
            MainSettingsItem.SteamGridDb ->
                com.nendo.argosy.ui.cocoon.CocoonIntegrationsState.steamGridDbOpen.value = true
            MainSettingsItem.RomMedia ->
                com.nendo.argosy.ui.cocoon.CocoonIntegrationsState.romMediaOpen.value = true
            MainSettingsItem.Permissions -> viewModel.navigateToSection(SettingsSection.PERMISSIONS)
            MainSettingsItem.About -> viewModel.navigateToSection(SettingsSection.ABOUT)
        }
    }

    fun pickerToken(item: MainSettingsItem): Int =
        if (uiState.enumPickerKey == item.key) uiState.enumPickerToken else 0

    val paneFocused by com.nendo.argosy.ui.cocoon.CocoonSettingsFocus.paneFocused.collectAsState()
    val ranges = remember(controls) { mainSettingsCategoryRanges(controls) }
    val activeCategory = ranges.indexOfFirst { uiState.focusedIndex in it.second }.coerceAtLeast(0)
    val activeRange = ranges.getOrNull(activeCategory)?.second ?: IntRange.EMPTY

    LaunchedEffect(uiState.focusedIndex, activeCategory) {
        val row = uiState.focusedIndex - activeRange.first
        if (row >= 0) listState.animateScrollToItem(row.coerceAtLeast(0))
    }

    // Cocoon's settings root: categories on the left, the open category's rows on the right.
    Box(modifier = Modifier.fillMaxSize()) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Dimens.spacingMd, vertical = Dimens.spacingSm)
    ) {
        // Wide enough for a category's whole name: a truncated "Personaliz…" is not a menu item
        // a player can read, and the pane's left column is the only place those names appear.
        Column(
            modifier = Modifier.fillMaxWidth(0.42f),
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs)
        ) {
            ranges.forEachIndexed { index, (key, range) ->
                val category = com.nendo.argosy.ui.cocoon.CocoonSettingsCategory.forKey(key) ?: return@forEachIndexed
                com.nendo.argosy.ui.cocoon.CocoonNavRow(
                    category = category,
                    selected = index == activeCategory,
                    focused = !paneFocused && index == activeCategory,
                    onClick = {
                        com.nendo.argosy.ui.cocoon.CocoonSettingsFocus.paneFocused.value = false
                        viewModel.setFocusIndex(range.first)
                    }
                )
            }
        }
        Spacer(modifier = Modifier.width(Dimens.spacingMd))
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = Dimens.spacingXs)
        ) {
            items(activeRange.toList(), key = { it }) { focusIndex ->
                val item = mainSettingsItemAtFocusIndex(focusIndex, controls) ?: return@items
                com.nendo.argosy.ui.cocoon.CocoonSettingsRow(
                    icon = item.icon,
                    title = stringResource(item.titleRes),
                    subtitle = getSubtitle(item),
                    focused = paneFocused && uiState.focusedIndex == focusIndex,
                    onClick = {
                        com.nendo.argosy.ui.cocoon.CocoonSettingsFocus.paneFocused.value = true
                        handleClick(item)
                    },
                    badge = updatesBadge(item, uiState.emulators),
                    isFirst = focusIndex == activeRange.first,
                    isLast = focusIndex == activeRange.last
                )
            }
        }
    }
        if (searchOpen) {
            com.nendo.argosy.ui.cocoon.CocoonSettingsSearch(
                onOpenSection = { section ->
                    com.nendo.argosy.ui.cocoon.CocoonSettingsSearchState.open.value = false
                    viewModel.navigateToSection(section)
                },
                onDismiss = { com.nendo.argosy.ui.cocoon.CocoonSettingsSearchState.open.value = false }
            )
        }
    }
}

@Composable
private fun updatesBadge(item: MainSettingsItem, emulators: EmulatorState): String? {
    val (pluralRes, count) = when (item) {
        MainSettingsItem.Platforms ->
            R.plurals.settings_main_platforms_updates_badge to emulators.outdatedEmulatorCount
        MainSettingsItem.BuiltinEmulator ->
            R.plurals.settings_main_builtin_updates_badge to emulators.coreUpdatesAvailable
        else -> return null
    }
    return if (count > 0) pluralStringResource(pluralRes, count, count) else null
}
