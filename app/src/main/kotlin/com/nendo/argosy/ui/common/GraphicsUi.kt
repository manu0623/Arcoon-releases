package com.nendo.argosy.ui.common

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.nendo.argosy.R
import com.nendo.argosy.data.emulator.InstalledVulkanDriver
import com.nendo.argosy.libretro.GraphicsApi
import com.nendo.argosy.libretro.GraphicsBackend
import com.nendo.argosy.libretro.GraphicsBackendRegistry

@get:StringRes
val GraphicsApi.labelRes: Int
    get() = when (this) {
        GraphicsApi.OPENGL -> R.string.settings_builtin_graphics_api_opengl
        GraphicsApi.VULKAN -> R.string.settings_builtin_graphics_api_vulkan
    }

/**
 * One value of a Backend row. [token] is what gets stored; the label is [rawLabel] for a core's
 * own option, in the core's upstream wording, or [labelRes] for an Argosy value.
 */
data class GraphicsBackendOptionUi(
    val token: String,
    val rawLabel: String? = null,
    @StringRes val labelRes: Int? = null
)

fun GraphicsBackend.optionsUi(): List<GraphicsBackendOptionUi> = when (this) {
    is GraphicsBackend.CoreOption -> GraphicsBackendRegistry.optionDef(this)
        ?.let { def -> def.values.map { GraphicsBackendOptionUi(token = it, rawLabel = def.displayValueFor(it)) } }
        .orEmpty()
    is GraphicsBackend.FrontendApi -> GraphicsApi.entries.map { GraphicsBackendOptionUi(token = it.token, labelRes = it.labelRes) }
}

@Composable
fun GraphicsBackendOptionUi.label(): String =
    labelRes?.let { stringResource(it) } ?: rawLabel ?: token

sealed interface GpuDriverPickerEntry {
    data class Value(val token: String?) : GpuDriverPickerEntry
    data class Install(val fileName: String) : GpuDriverPickerEntry
}

fun gpuDriverPickerEntries(values: List<String?>, installable: List<String>): List<GpuDriverPickerEntry> =
    values.map { GpuDriverPickerEntry.Value(it) } + installable.map { GpuDriverPickerEntry.Install(it) }

data class VulkanDriverOption(
    val id: String,
    val name: String,
    val version: String?
)

fun InstalledVulkanDriver.toOption(): VulkanDriverOption =
    VulkanDriverOption(id = id, name = name, version = driverVersion)

enum class GpuDriverBlock {
    CORE_UNSUPPORTED,
    CORE_SYSTEM_DRIVER_ONLY,
    BACKEND_NOT_VULKAN,
    NO_CUSTOM_DRIVERS
}

/**
 * Why the GPU driver row cannot be changed, or null when it can. [backendSupportsVulkan] and
 * [backendIsVulkan] are null where no core is in scope, as in the global context, and only the
 * device check applies there. [coreAcceptsCustomDriver] is false for a core that always runs on
 * the system Vulkan driver.
 */
fun gpuDriverBlock(
    backendSupportsVulkan: Boolean?,
    backendIsVulkan: Boolean?,
    customDriversSupported: Boolean,
    coreAcceptsCustomDriver: Boolean = true
): GpuDriverBlock? = when {
    backendSupportsVulkan == false -> GpuDriverBlock.CORE_UNSUPPORTED
    !coreAcceptsCustomDriver -> GpuDriverBlock.CORE_SYSTEM_DRIVER_ONLY
    backendIsVulkan == false -> GpuDriverBlock.BACKEND_NOT_VULKAN
    !customDriversSupported -> GpuDriverBlock.NO_CUSTOM_DRIVERS
    else -> null
}
