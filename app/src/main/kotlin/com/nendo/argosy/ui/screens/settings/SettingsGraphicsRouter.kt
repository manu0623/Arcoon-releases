package com.nendo.argosy.ui.screens.settings

import androidx.lifecycle.viewModelScope
import com.nendo.argosy.R
import com.nendo.argosy.core.notification.NotificationText
import com.nendo.argosy.core.notification.NotificationType
import com.nendo.argosy.data.emulator.GpuDriverPrecedence
import com.nendo.argosy.data.emulator.GpuDriverToken
import com.nendo.argosy.data.local.entity.CoreOptionOverrideEntity
import com.nendo.argosy.data.local.entity.PlatformLibretroSettingsEntity
import com.nendo.argosy.libretro.GraphicsBackend
import com.nendo.argosy.libretro.GraphicsBackendRegistry
import com.nendo.argosy.libretro.LibretroCoreRegistry
import com.nendo.argosy.ui.common.optionsUi
import com.nendo.argosy.ui.common.toOption
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal const val GRAPHICS_BACKEND_PICKER_KEY = "builtin_graphics_backend"
internal const val GPU_DRIVER_PICKER_KEY = "builtin_gpu_driver"

internal fun routeRefreshBuiltinGraphics(vm: SettingsViewModel) {
    vm.viewModelScope.launch {
        val context = vm._uiState.value.builtinVideo.currentPlatformContext
        val customSupported = vm.vulkanDriverRepository.customDriversSupported()
        val installed = vm.vulkanDriverRepository.listInstalled().map { it.toOption() }
        val installable = if (customSupported) {
            vm.vulkanDriverRepository.listInstallablePackages().map { it.name }
        } else {
            emptyList()
        }
        val coreId = context?.let {
            vm.builtinCoreResolver.resolveCoreId(null, it.platformId, it.platformSlug)
        }
        val backend = context?.let { loadBackendUi(vm, coreId, it.platformId) }
        vm._uiState.update { state ->
            if (state.builtinVideo.currentPlatformContext?.platformId != context?.platformId) {
                return@update state
            }
            state.copy(
                builtinVideo = state.builtinVideo.copy(
                    graphics = state.builtinVideo.graphics.copy(
                        isLoaded = true,
                        loadedPlatformId = context?.platformId,
                        coreName = coreId?.let { LibretroCoreRegistry.displayNameFor(it) },
                        backend = backend,
                        customDriversSupported = customSupported,
                        installedDrivers = installed,
                        installablePackages = installable
                    )
                )
            )
        }
    }
}

private suspend fun loadBackendUi(
    vm: SettingsViewModel,
    coreId: String?,
    platformId: Long
): GraphicsBackendUi? {
    val backend = GraphicsBackendRegistry.forCore(coreId) ?: return null
    val coreName = LibretroCoreRegistry.displayNameFor(backend.coreId)
    return when (backend) {
        is GraphicsBackend.CoreOption -> {
            val def = GraphicsBackendRegistry.optionDef(backend) ?: return null
            val stored = vm.coreOptionsRepo.getOverridesForCore(backend.coreId)
                .firstOrNull { it.optionKey == backend.optionKey }
                ?.value
            val current = stored?.let { def.resolveStored(it) } ?: def.defaultValue
            GraphicsBackendUi(
                coreName = coreName,
                isCoreOption = true,
                options = backend.optionsUi(),
                currentValue = current,
                isOverridden = stored != null,
                supportsVulkan = GraphicsBackendRegistry.supportsVulkan(backend),
                isVulkan = GraphicsBackendRegistry.isVulkanValue(backend, current),
                acceptsCustomDriver = GraphicsBackendRegistry.acceptsCustomVulkanDriver(backend.coreId)
            )
        }
        is GraphicsBackend.FrontendApi -> {
            val stored = vm.libretroSettingsRepo.getByPlatformId(platformId)?.graphicsApi
            val current = GraphicsBackendRegistry.effectiveValue(backend, emptyMap(), stored)
            GraphicsBackendUi(
                coreName = coreName,
                isCoreOption = false,
                options = backend.optionsUi(),
                currentValue = current.orEmpty(),
                isOverridden = stored != null,
                supportsVulkan = GraphicsBackendRegistry.supportsVulkan(backend),
                isVulkan = GraphicsBackendRegistry.isVulkanValue(backend, current),
                acceptsCustomDriver = GraphicsBackendRegistry.acceptsCustomVulkanDriver(backend.coreId)
            )
        }
    }
}

internal fun routeCycleGraphicsBackend(vm: SettingsViewModel, direction: Int) {
    val backend = vm._uiState.value.builtinVideo.graphics.backend ?: return
    if (backend.options.isEmpty()) return
    val currentIndex = backend.options.indexOfFirst { it.token == backend.currentValue }.coerceAtLeast(0)
    routeSetGraphicsBackend(vm, backend.options[(currentIndex + direction).mod(backend.options.size)].token)
}

internal fun routeSelectGraphicsBackend(vm: SettingsViewModel, index: Int) {
    val backend = vm._uiState.value.builtinVideo.graphics.backend ?: return
    val option = backend.options.getOrNull(index) ?: return
    routeSetGraphicsBackend(vm, option.token)
}

private fun routeSetGraphicsBackend(vm: SettingsViewModel, token: String?) {
    val context = vm._uiState.value.builtinVideo.currentPlatformContext ?: return
    vm.viewModelScope.launch {
        val coreId = vm.builtinCoreResolver.resolveCoreId(null, context.platformId, context.platformSlug)
        when (val backend = GraphicsBackendRegistry.forCore(coreId)) {
            is GraphicsBackend.CoreOption -> {
                val def = GraphicsBackendRegistry.optionDef(backend)
                if (token == null || token == def?.defaultValue) {
                    vm.coreOptionsRepo.delete(backend.coreId, backend.optionKey)
                } else {
                    vm.coreOptionsRepo.upsert(CoreOptionOverrideEntity(backend.coreId, backend.optionKey, token))
                }
            }
            is GraphicsBackend.FrontendApi ->
                updatePlatformGraphics(vm, context.platformId) { it.copy(graphicsApi = token) }
            null -> return@launch
        }
        routeRefreshBuiltinGraphics(vm)
    }
}

internal fun routeResetGraphicsBackend(vm: SettingsViewModel) = routeSetGraphicsBackend(vm, null)

internal fun selectableGpuDriverTokens(graphics: BuiltinGraphicsState): List<String> =
    listOf(GpuDriverToken.SYSTEM) +
        if (graphics.customDriversSupported) {
            graphics.installedDrivers.map { GpuDriverToken.custom(it.id) }
        } else {
            emptyList()
        }

internal fun platformGpuDriverOverride(state: SettingsUiState): String? {
    val context = state.builtinVideo.currentPlatformContext ?: return null
    return state.platformLibretro.platformSettings[context.platformId]?.gpuDriver
}

internal fun currentGpuDriverToken(state: SettingsUiState): String =
    GpuDriverPrecedence.select(null, platformGpuDriverOverride(state), state.builtinVideo.gpuDriver)

internal fun routeCycleGpuDriver(vm: SettingsViewModel, direction: Int) {
    val state = vm._uiState.value
    val tokens = selectableGpuDriverTokens(state.builtinVideo.graphics)
    val currentIndex = tokens.indexOf(currentGpuDriverToken(state)).coerceAtLeast(0)
    routeSetGpuDriver(vm, tokens[(currentIndex + direction).mod(tokens.size)])
}

internal fun routeSetGpuDriver(vm: SettingsViewModel, token: String?) {
    val context = vm._uiState.value.builtinVideo.currentPlatformContext
    if (context == null) {
        val globalToken = token ?: GpuDriverToken.SYSTEM
        vm._uiState.update { it.copy(builtinVideo = it.builtinVideo.copy(gpuDriver = globalToken)) }
        vm.viewModelScope.launch { vm.libretroSettingsRepo.setBuiltinGpuDriver(globalToken) }
        return
    }
    vm.viewModelScope.launch {
        updatePlatformGraphics(vm, context.platformId) { it.copy(gpuDriver = token) }
    }
}

internal fun routeResetGpuDriver(vm: SettingsViewModel) {
    if (vm._uiState.value.builtinVideo.isGlobalContext) return
    routeSetGpuDriver(vm, null)
}

internal fun routeInstallVulkanDriver(vm: SettingsViewModel, fileName: String) {
    if (vm._uiState.value.builtinVideo.graphics.installingPackage != null) return
    vm._uiState.update {
        it.copy(builtinVideo = it.builtinVideo.copy(graphics = it.builtinVideo.graphics.copy(installingPackage = fileName)))
    }
    vm.viewModelScope.launch {
        val result = vm.vulkanDriverRepository.installFromDownloads(fileName)
        vm._uiState.update {
            it.copy(builtinVideo = it.builtinVideo.copy(graphics = it.builtinVideo.graphics.copy(installingPackage = null)))
        }
        result
            .onSuccess { driver ->
                vm.notificationManager.show(
                    title = NotificationText.Res(R.string.settings_builtin_gpu_driver_installed, listOf(driver.name)),
                    type = NotificationType.SUCCESS
                )
                routeSetGpuDriver(vm, GpuDriverToken.custom(driver.id))
            }
            .onFailure {
                vm.notificationManager.show(
                    title = NotificationText.Res(R.string.settings_builtin_gpu_driver_install_failed, listOf(fileName)),
                    type = NotificationType.ERROR
                )
            }
        routeRefreshBuiltinGraphics(vm)
    }
}

private suspend fun updatePlatformGraphics(
    vm: SettingsViewModel,
    platformId: Long,
    transform: (PlatformLibretroSettingsEntity) -> PlatformLibretroSettingsEntity
) {
    val current = vm.libretroSettingsRepo.getByPlatformId(platformId)
        ?: PlatformLibretroSettingsEntity(platformId = platformId)
    val updated = transform(current)
    if (updated.hasAnyOverrides()) {
        vm.libretroSettingsRepo.upsert(updated)
    } else {
        vm.libretroSettingsRepo.deleteByPlatformId(platformId)
    }
}
