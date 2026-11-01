package com.nendo.argosy.ui.screens.settings

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.nendo.argosy.R
import com.nendo.argosy.core.notification.NotificationProgress
import com.nendo.argosy.core.notification.NotificationText
import com.nendo.argosy.core.notification.NotificationType
import com.nendo.argosy.core.notification.showError
import com.nendo.argosy.data.emulator.EmulatorRegistry
import com.nendo.argosy.libretro.CoreBulkUpdateResult
import com.nendo.argosy.libretro.LibretroCoreRegistry
import com.nendo.argosy.libretro.NetplaySupportLevel
import com.nendo.argosy.ui.input.HapticPattern
import com.nendo.argosy.ui.screens.settings.sections.CoreManagementItem
import com.nendo.argosy.ui.screens.settings.sections.coreManagementFocusedPlatform
import com.nendo.argosy.ui.screens.settings.sections.coreManagementItemAtFocusIndex
import com.nendo.argosy.ui.screens.settings.sections.coreManagementMaxFocusIndex
import com.nendo.argosy.ui.screens.settings.sections.createCoreManagementLayoutInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val TAG = "CoreManagement"
private const val CORE_BULK_UPDATE_NOTIFICATION_KEY = "core_bulk_update"

internal fun routeLoadCoreManagementState(vm: SettingsViewModel, preserveFocus: Boolean = false) {
    vm.viewModelScope.launch {
        try {
            loadCoreManagementStateInternal(vm, preserveFocus)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load core management state", e)
            vm._uiState.update { it.copy(coreManagement = CoreManagementState()) }
            vm.notificationManager.showError(NotificationText.Res(R.string.settings_shell_router_cores_load_failed))
        }
    }
}

private suspend fun loadCoreManagementStateInternal(vm: SettingsViewModel, preserveFocus: Boolean) {
    val isOnline = com.nendo.argosy.util.NetworkUtils.isOnline(vm.context)
    val syncEnabledPlatforms = vm.platformRepository.getSyncEnabledPlatforms()
    val installedCoreIds = vm.getInstalledCoreIds()
    val pendingBuilds = vm.coreManager.getPendingCoreUpdates().associate { pending ->
        pending.coreInfo.coreId to CoreUpdateBuilds(
            installedVersion = pending.installedVersion,
            latestVersion = pending.latestVersion
        )
    }

    val platformRows = syncEnabledPlatforms
        .filter { LibretroCoreRegistry.isPlatformSupported(it.slug) }
        .distinctBy { it.slug }
        .map { platform ->
            val availableCores = LibretroCoreRegistry.getCoresForPlatform(platform.slug)
            val activeCoreId = vm.builtinCoreResolver.resolveCoreId(
                gameId = null,
                platformId = platform.id,
                platformSlug = platform.slug
            )

            PlatformCoreRow(
                platformId = platform.id,
                platformSlug = platform.slug,
                platformName = platform.name,
                cores = availableCores.map { core ->
                    CoreChipState(
                        coreId = core.coreId,
                        displayName = core.displayName,
                        isInstalled = core.coreId in installedCoreIds,
                        isActive = core.coreId == activeCoreId,
                        netplaySupported = core.netplaySupport == NetplaySupportLevel.SUPPORTED,
                        pendingUpdate = pendingBuilds[core.coreId]
                    )
                }
            )
        }
        .filter { it.cores.isNotEmpty() }

    vm._uiState.update { current ->
        val previous = current.coreManagement
        val reloaded = previous.copy(
            platforms = platformRows,
            isOnline = isOnline,
            pendingUpdateCount = pendingBuilds.size,
            bulkUpdateFailedCount = if (preserveFocus) previous.bulkUpdateFailedCount else 0
        )
        current.copy(coreManagement = reloaded.withFocusCarriedFrom(previous, preserveFocus))
    }
}

private fun CoreManagementState.withFocusCarriedFrom(
    previous: CoreManagementState,
    preserveFocus: Boolean
): CoreManagementState {
    if (!preserveFocus) {
        val firstPlatform = coreManagementItemAtFocusIndex(0, this) as? CoreManagementItem.Platform
        return copy(focusedRowIndex = 0, focusedCoreIndex = firstPlatform?.row?.activeCoreIndex ?: 0)
    }
    val previousKey = coreManagementItemAtFocusIndex(previous.focusedRowIndex, previous)?.key
    val rowIndex = previousKey?.let { createCoreManagementLayoutInfo(this).focusIndexOfKey(it) }
        ?: previous.focusedRowIndex.coerceIn(0, coreManagementMaxFocusIndex(this))
    val focusedPlatform = (coreManagementItemAtFocusIndex(rowIndex, this) as? CoreManagementItem.Platform)?.row
    val coreIndex = when {
        focusedPlatform == null -> 0
        focusedPlatform.platformSlug == previousKey ->
            previous.focusedCoreIndex.coerceIn(0, (focusedPlatform.cores.size - 1).coerceAtLeast(0))
        else -> focusedPlatform.activeCoreIndex
    }
    return copy(focusedRowIndex = rowIndex, focusedCoreIndex = coreIndex)
}

internal fun routeMoveCoreManagementPlatformFocus(vm: SettingsViewModel, delta: Int): Boolean {
    val state = vm._uiState.value.coreManagement
    if (coreManagementItemAtFocusIndex(0, state) == null) return false
    val newIndex = (state.focusedRowIndex + delta).coerceIn(0, coreManagementMaxFocusIndex(state))
    if (newIndex == state.focusedRowIndex) {
        vm.hapticManager.vibrate(HapticPattern.BOUNDARY_HIT)
        return false
    }
    val target = coreManagementItemAtFocusIndex(newIndex, state) as? CoreManagementItem.Platform
    vm._uiState.update {
        it.copy(
            coreManagement = it.coreManagement.copy(
                focusedRowIndex = newIndex,
                focusedCoreIndex = target?.row?.activeCoreIndex ?: 0
            )
        )
    }
    return true
}

internal fun routeMoveCoreManagementCoreFocus(vm: SettingsViewModel, delta: Int): Boolean {
    val state = vm._uiState.value.coreManagement
    val platform = coreManagementFocusedPlatform(state) ?: return false
    if (platform.cores.isEmpty()) return false
    val newIndex = (state.focusedCoreIndex + delta).coerceIn(0, platform.cores.size - 1)
    if (newIndex == state.focusedCoreIndex) {
        vm.hapticManager.vibrate(HapticPattern.BOUNDARY_HIT)
        return false
    }
    vm._uiState.update {
        it.copy(coreManagement = it.coreManagement.copy(focusedCoreIndex = newIndex))
    }
    return true
}

internal fun routeCoreManagementConfirm(vm: SettingsViewModel) {
    val state = vm._uiState.value.coreManagement
    when (coreManagementItemAtFocusIndex(state.focusedRowIndex, state)) {
        CoreManagementItem.UpdateAll -> routeUpdateAllCores(vm)
        is CoreManagementItem.Platform -> routeSelectCoreForPlatform(vm)
        CoreManagementItem.Header, null -> Unit
    }
}

internal fun routeSelectCoreAt(vm: SettingsViewModel, rowIndex: Int, coreIndex: Int) {
    val state = vm._uiState.value.coreManagement
    val platform = coreManagementItemAtFocusIndex(rowIndex, state) as? CoreManagementItem.Platform ?: return
    if (coreIndex !in platform.row.cores.indices) return

    vm._uiState.update {
        it.copy(
            coreManagement = it.coreManagement.copy(
                focusedRowIndex = rowIndex,
                focusedCoreIndex = coreIndex
            )
        )
    }
    routeSelectCoreForPlatform(vm)
}

internal fun routeSelectCoreForPlatform(vm: SettingsViewModel) {
    val state = vm._uiState.value.coreManagement
    val platform = coreManagementFocusedPlatform(state) ?: return
    val core = platform.cores.getOrNull(state.focusedCoreIndex) ?: return

    if (!core.isInstalled) {
        if (state.isOnline) {
            routeDownloadCoreWithNotification(vm, core.coreId)
        } else {
            vm.notificationManager.showError(NotificationText.Res(R.string.settings_shell_router_cannot_download_offline))
        }
        return
    }

    if (core.updateAvailable && core.isActive) {
        if (state.isOnline) {
            routeDownloadCoreWithNotification(vm, core.coreId)
        } else {
            vm.notificationManager.showError(NotificationText.Res(R.string.settings_shell_router_cannot_update_offline))
        }
        return
    }

    vm.viewModelScope.launch {
        vm.libretroSettingsRepo.setBuiltinCoreForPlatform(platform.platformSlug, core.coreId)
        val platformConfig = vm.emulatorConfigRepo.getDefaultForPlatform(platform.platformId)
        if (platformConfig?.packageName == EmulatorRegistry.BUILTIN_PACKAGE && platformConfig.coreName != null) {
            vm.emulatorConfigRepo.updateCoreNameForPlatform(platform.platformId, null)
        }
        vm.loadCoreManagementState(preserveFocus = true)
    }
}

internal fun routeTapUpdateAllCores(vm: SettingsViewModel) {
    val state = vm._uiState.value.coreManagement
    val rowIndex = createCoreManagementLayoutInfo(state).focusIndexOfKey(CoreManagementItem.UpdateAll.key) ?: return
    vm._uiState.update {
        it.copy(coreManagement = it.coreManagement.copy(focusedRowIndex = rowIndex, focusedCoreIndex = 0))
    }
    routeUpdateAllCores(vm)
}

internal fun routeUpdateAllCores(vm: SettingsViewModel) {
    if (!vm._uiState.value.coreManagement.isOnline) {
        vm.notificationManager.showError(NotificationText.Res(R.string.settings_shell_router_cannot_update_all_offline))
        return
    }
    val previous = vm._uiState.getAndUpdate {
        if (it.isCoreDownloadBusy()) {
            it
        } else {
            it.copy(
                coreManagement = it.coreManagement.copy(
                    bulkUpdate = CoreBulkUpdateProgress(total = it.coreManagement.pendingUpdateCount),
                    bulkUpdateFailedCount = 0,
                    isDownloading = true
                ),
                coreOptions = it.coreOptions.copy(isDownloading = true)
            )
        }
    }
    if (previous.isCoreDownloadBusy()) return
    vm.viewModelScope.launch { runBulkCoreUpdate(vm) }
}

private fun SettingsUiState.isCoreDownloadBusy(): Boolean =
    coreManagement.bulkUpdate != null || coreOptions.isDownloading

private suspend fun runBulkCoreUpdate(vm: SettingsViewModel) {
    vm.notificationManager.showPersistent(
        title = NotificationText.Res(R.string.settings_shell_router_cores_updating_title),
        key = CORE_BULK_UPDATE_NOTIFICATION_KEY
    )
    var failedCount = 0
    var notificationSettled = false
    try {
        val result = vm.coreManager.updateInstalledCores { coreInfo, position, total ->
            vm._uiState.update {
                it.copy(
                    coreManagement = it.coreManagement.copy(
                        bulkUpdate = CoreBulkUpdateProgress(coreInfo.displayName, position, total),
                        downloadingCoreId = coreInfo.coreId
                    ),
                    coreOptions = it.coreOptions.copy(downloadingCoreId = coreInfo.coreId)
                )
            }
            vm.notificationManager.updatePersistent(
                key = CORE_BULK_UPDATE_NOTIFICATION_KEY,
                subtitle = NotificationText.Raw(coreInfo.displayName),
                progress = NotificationProgress(position, total)
            )
        }
        failedCount = result.failed.size
        reportBulkCoreUpdate(vm, result)
        notificationSettled = true
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.e(TAG, "Bulk core update failed", e)
        failedCount = vm._uiState.value.coreManagement.pendingUpdateCount
        vm.notificationManager.completePersistent(
            key = CORE_BULK_UPDATE_NOTIFICATION_KEY,
            title = NotificationText.Res(R.string.settings_shell_router_cores_update_error_title),
            subtitle = NotificationText.Res(R.string.settings_shell_router_core_download_failed_fallback),
            type = NotificationType.ERROR
        )
        notificationSettled = true
    } finally {
        if (!notificationSettled) vm.notificationManager.dismissByKey(CORE_BULK_UPDATE_NOTIFICATION_KEY)
        vm._uiState.update {
            it.copy(
                coreManagement = it.coreManagement.copy(
                    bulkUpdate = null,
                    bulkUpdateFailedCount = failedCount,
                    isDownloading = false,
                    downloadingCoreId = null
                ),
                coreOptions = it.coreOptions.copy(isDownloading = false, downloadingCoreId = null)
            )
        }
    }
    vm.emulatorDelegate.updateCoreCounts()
    vm.loadCoreManagementState(preserveFocus = true)
    refreshCoreOptionsInstallState(vm)
}

private fun reportBulkCoreUpdate(vm: SettingsViewModel, result: CoreBulkUpdateResult) {
    if (result.failed.isEmpty()) {
        vm.notificationManager.completePersistent(
            key = CORE_BULK_UPDATE_NOTIFICATION_KEY,
            title = NotificationText.Plural(
                R.plurals.settings_shell_router_cores_updated_title,
                result.updated.size,
                listOf(result.updated.size)
            ),
            type = NotificationType.SUCCESS
        )
        return
    }
    vm.notificationManager.completePersistent(
        key = CORE_BULK_UPDATE_NOTIFICATION_KEY,
        title = NotificationText.Plural(
            R.plurals.settings_shell_router_cores_update_failed_title,
            result.failed.size,
            listOf(result.failed.size)
        ),
        subtitle = NotificationText.Raw(result.failed.joinToString(", ") { it.displayName }),
        type = NotificationType.ERROR
    )
}
