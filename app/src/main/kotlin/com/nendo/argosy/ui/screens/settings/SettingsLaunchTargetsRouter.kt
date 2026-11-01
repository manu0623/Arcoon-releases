package com.nendo.argosy.ui.screens.settings

import androidx.lifecycle.viewModelScope
import com.nendo.argosy.DualScreenManagerHolder
import com.nendo.argosy.data.emulator.EmulatorRegistry
import com.nendo.argosy.data.emulator.EmulatorSettingScope
import com.nendo.argosy.data.local.entity.getDisplayName
import com.nendo.argosy.data.preferences.EmulatorDisplayTarget
import com.nendo.argosy.data.preferences.SessionStateStore
import com.nendo.argosy.domain.model.ScreenRole
import com.nendo.argosy.ui.screens.common.EmulatorLaunchTargetResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal enum class LaunchTargetKind { GAMES, APPS }

internal fun routeRefreshLaunchTargets(vm: SettingsViewModel) {
    vm.viewModelScope.launch {
        val (screens, setCount) = withContext(Dispatchers.IO) {
            val helper = vm.displayAffinityHelper
            val rolesSwapped = SessionStateStore(vm.context).isRolesSwapped()
            val numbers = helper.screenNumbersByDisplayId()
            val roles = helper.getRoleDisplayIds(rolesSwapped)
            val screens = LaunchTargetScreens(
                primary = roles?.first?.let { numbers[it] },
                presentation = roles?.second?.let { numbers[it] },
                appScreen = helper.appScreenDisplayId(rolesSwapped)?.let { numbers[it] }
            )
            val listed = listedLaunchTargetPlatforms(vm).map { it.id }.toSet()
            val platformsSet = vm.emulatorConfigRepo.getPlatformDisplayTargets().count {
                it.platformId in listed &&
                    EmulatorDisplayTarget.fromString(it.displayTarget) != EmulatorDisplayTarget.DEFAULT
            }
            val appsSet = vm.preferencesRepository.userPreferences.first().appDisplayTargets.keys
                .count { isLaunchTargetApp(vm, it) }
            screens to platformsSet + appsSet
        }
        vm.displayDelegate.setLaunchTargetSummary(screens, setCount)
    }
}

internal fun routeCycleLaunchTarget(vm: SettingsViewModel, kind: LaunchTargetKind, direction: Int) {
    val display = vm._uiState.value.display
    val options = display.launchTargetScreens.options
    val current = options.indexOf(display.launchTargetScreens.shown(storedLaunchTarget(display, kind)))
    val next = if (current < 0) 0 else (current + direction).mod(options.size)
    routeSelectLaunchTarget(vm, kind, next)
}

internal fun routeSelectLaunchTarget(vm: SettingsViewModel, kind: LaunchTargetKind, index: Int) {
    val target = vm._uiState.value.display.launchTargetScreens.options.getOrNull(index) ?: return
    when (kind) {
        LaunchTargetKind.GAMES -> vm.displayDelegate.setGamesLaunchTarget(vm.viewModelScope, target)
        LaunchTargetKind.APPS -> vm.displayDelegate.setAppsLaunchTarget(vm.viewModelScope, target)
    }
}

private fun storedLaunchTarget(display: DisplayState, kind: LaunchTargetKind): EmulatorDisplayTarget =
    when (kind) {
        LaunchTargetKind.GAMES -> display.gamesLaunchTarget
        LaunchTargetKind.APPS -> display.appsLaunchTarget
    }

internal fun routeNavigateToLaunchTargets(vm: SettingsViewModel) {
    routeNavigateToSection(vm, SettingsSection.LAUNCH_TARGETS)
}

internal fun routeEnterLaunchTargets(vm: SettingsViewModel) {
    vm._uiState.update { it.copy(launchTargets = LaunchTargetsState()) }
    routeLoadLaunchTargetEntries(vm)
}

internal fun routeLeaveLaunchTargets(vm: SettingsViewModel) {
    vm.launchTargetsJob?.cancel()
    vm._uiState.update { it.copy(launchTargets = it.launchTargets.copy(pickerOpen = false)) }
    routeRefreshLaunchTargets(vm)
}

internal fun routeLoadLaunchTargetEntries(vm: SettingsViewModel, committedKey: String? = null) {
    vm.launchTargetsJob?.cancel()
    vm.launchTargetsJob = vm.viewModelScope.launch {
        val current = vm._uiState.value.launchTargets
        val inputs = withContext(Dispatchers.IO) { readLaunchTargetInputs(vm) }
        val withPlatforms = withContext(Dispatchers.IO) {
            current.copy(
                screens = inputs.screens,
                platforms = platformEntries(vm, inputs),
                apps = if (current.appsLoaded) appEntries(vm, inputs, current.apps.map { it.packageName to it.label }) else emptyList(),
                platformDefaultRole = EmulatorLaunchTargetResolver
                    .resolvedTarget(EmulatorDisplayTarget.DEFAULT, inputs.gamesDefault).screenRole,
                appDefaultNumber = inputs.numberOf(appDisplayId(vm, inputs, null))
                    ?: inputs.screens.firstOrNull()?.number
                    ?: 0
            )
        }
        publishLaunchTargets(vm, withPlatforms, committedKey)
        if (withPlatforms.appsLoaded) return@launch
        val withApps = withContext(Dispatchers.IO) {
            val catalog = vm.appsRepository.getInstalledApps(includeSystemApps = true)
                .filter { isLaunchTargetApp(vm, it.packageName) }
                .map { it.packageName to it.label }
            withPlatforms.copy(apps = appEntries(vm, inputs, catalog), appsLoaded = true)
        }
        publishLaunchTargets(vm, withApps, null)
    }
}

internal fun routeOpenLaunchTargetPicker(vm: SettingsViewModel): Boolean {
    val state = vm._uiState.value
    val entry = launchTargetsLayout(state.launchTargets).entryAt(state.focusedIndex) ?: return false
    vm._uiState.update {
        it.copy(launchTargets = it.launchTargets.copy(pickerKey = entry.key, pickerOpen = true))
    }
    return true
}

internal fun routeCloseLaunchTargetPicker(vm: SettingsViewModel) {
    vm._uiState.update { it.copy(launchTargets = it.launchTargets.copy(pickerOpen = false)) }
}

internal fun routePickLaunchTarget(vm: SettingsViewModel, index: Int) {
    val targets = vm._uiState.value.launchTargets
    routeCloseLaunchTargetPicker(vm)
    val entry = targets.pickerEntry ?: return
    if (!targets.isPickable(index)) return
    when (entry) {
        is LaunchTargetPlatformEntry -> storePlatformTarget(vm, entry, targets.platformPick(index) ?: return)
        is LaunchTargetAppEntry -> storeAppPin(vm, entry, targets.appPick(index))
    }
}

internal fun routeResetLaunchTarget(vm: SettingsViewModel, key: String) {
    val entry = launchTargetsLayout(vm._uiState.value.launchTargets).entries
        .firstOrNull { it.key == key }
        ?.takeIf { it.isSet }
        ?: return
    when (entry) {
        is LaunchTargetPlatformEntry -> storePlatformTarget(vm, entry, EmulatorDisplayTarget.DEFAULT)
        is LaunchTargetAppEntry -> storeAppPin(vm, entry, null)
    }
}

private fun storePlatformTarget(
    vm: SettingsViewModel,
    entry: LaunchTargetPlatformEntry,
    target: EmulatorDisplayTarget
) {
    if (target == entry.stored) return
    vm.emulatorDelegate.setDisplayTarget(vm.viewModelScope, entry.platformId, target) {
        routeLoadLaunchTargetEntries(vm, entry.key)
    }
}

private fun storeAppPin(vm: SettingsViewModel, entry: LaunchTargetAppEntry, screenKey: String?) {
    if (screenKey == entry.pinnedKey) return
    vm.viewModelScope.launch {
        vm.preferencesRepository.setAppDisplayTarget(entry.packageName, screenKey)
        routeLoadLaunchTargetEntries(vm, entry.key)
    }
}

private fun publishLaunchTargets(vm: SettingsViewModel, loaded: LaunchTargetsState, committedKey: String?) {
    vm._uiState.update { state ->
        val next = loaded.copy(
            pickerKey = state.launchTargets.pickerKey,
            pickerOpen = state.launchTargets.pickerOpen
        )
        if (state.currentSection != SettingsSection.LAUNCH_TARGETS) {
            return@update state.copy(launchTargets = next)
        }
        val before = launchTargetsLayout(state.launchTargets)
        val after = launchTargetsLayout(next)
        val focus = if (committedKey != null) {
            refocusAfterCommit(before, after, committedKey, state.focusedIndex)
        } else {
            refocusByKey(before, after, state.focusedIndex)
        }
        state.copy(launchTargets = next, focusedIndex = focus)
    }
}

private class LaunchTargetInputs(
    val rolesSwapped: Boolean,
    val gamesDefault: EmulatorDisplayTarget,
    val appsDefault: EmulatorDisplayTarget,
    val appPins: Map<String, String>,
    val attached: List<LaunchTargetScreen>
) {
    val screens: List<LaunchTargetScreen> get() = attached.filter { it.role != ScreenRole.OFF }

    fun numberOf(displayId: Int?): Int? = attached.firstOrNull { it.displayId == displayId }?.number
}

private suspend fun readLaunchTargetInputs(vm: SettingsViewModel): LaunchTargetInputs {
    val rolesSwapped = SessionStateStore(vm.context).isRolesSwapped()
    val prefs = vm.preferencesRepository.userPreferences.first()
    val helper = vm.displayAffinityHelper
    val roleDisplayIds = helper.getRoleDisplayIds(rolesSwapped)
    val appScreenDisplayId = helper.appScreenDisplayId(rolesSwapped)
    val resolved = DualScreenManagerHolder.instance?.resolveScreenLayout()
    val attached = if (resolved == null) {
        emptyList()
    } else {
        resolved.attached.map { screen ->
            LaunchTargetScreen(
                key = screen.key,
                displayId = screen.displayId,
                number = screen.number,
                role = currentScreenRole(
                    displayId = screen.displayId,
                    layoutRole = resolved.layout.roleFor(screen.key) ?: ScreenRole.PRESENTATION,
                    roleDisplayIds = roleDisplayIds,
                    appScreenDisplayId = appScreenDisplayId
                )
            )
        }.sortedBy { it.number }
    }
    return LaunchTargetInputs(
        rolesSwapped = rolesSwapped,
        gamesDefault = prefs.gamesDisplayTarget,
        appsDefault = prefs.appsDisplayTarget,
        appPins = prefs.appDisplayTargets,
        attached = attached
    )
}

private suspend fun listedLaunchTargetPlatforms(vm: SettingsViewModel) = launchTargetPlatforms(
    platforms = vm.platformRepository.getAllPlatformsOrdered(),
    showsDisplayTarget = EmulatorSettingScope.showsDisplayTarget(vm.displayAffinityHelper.hasSecondaryDisplay)
)

private fun isLaunchTargetApp(vm: SettingsViewModel, packageName: String): Boolean =
    !EmulatorRegistry.isKnownPackage(packageName) && !vm.appsRepository.isArgosy(packageName)

private suspend fun platformEntries(
    vm: SettingsViewModel,
    inputs: LaunchTargetInputs
): List<LaunchTargetPlatformEntry> {
    val stored = vm.emulatorConfigRepo.getPlatformDisplayTargets()
        .associate { it.platformId to EmulatorDisplayTarget.fromString(it.displayTarget) }
    val overrides = vm.emulatorConfigRepo.getGameDisplayTargetCounts()
        .associate { it.platformId to it.gameCount }
    return listedLaunchTargetPlatforms(vm).map { platform ->
        val target = stored[platform.id] ?: EmulatorDisplayTarget.DEFAULT
        LaunchTargetPlatformEntry(
            platformId = platform.id,
            slug = platform.slug,
            label = platform.getDisplayName(),
            stored = target,
            gameOverrides = overrides[platform.id] ?: 0,
            displayId = vm.emulatorLaunchTargetResolver.placementDisplayId(
                stored = target,
                gamesDefault = inputs.gamesDefault,
                rolesSwapped = inputs.rolesSwapped
            )
        )
    }
}

private fun appEntries(
    vm: SettingsViewModel,
    inputs: LaunchTargetInputs,
    catalog: List<Pair<String, String>>
): List<LaunchTargetAppEntry> = catalog.map { (packageName, label) ->
    val pinnedKey = inputs.appPins[packageName]
    val pinnedScreen = pinnedKey?.let { key -> inputs.attached.firstOrNull { it.key == key } }
    val honouredPin = pinnedKey?.takeIf { pinnedScreen?.role != ScreenRole.OFF }
    LaunchTargetAppEntry(
        packageName = packageName,
        label = label,
        pinnedKey = pinnedKey,
        pinnedScreen = pinnedScreen,
        displayId = appDisplayId(vm, inputs, honouredPin)
    )
}

private fun appDisplayId(vm: SettingsViewModel, inputs: LaunchTargetInputs, pinnedKey: String?): Int? =
    vm.displayAffinityHelper.appLaunchDisplayId(
        preferredScreenKey = pinnedKey,
        rolesSwapped = inputs.rolesSwapped,
        defaultTarget = inputs.appsDefault
    )
