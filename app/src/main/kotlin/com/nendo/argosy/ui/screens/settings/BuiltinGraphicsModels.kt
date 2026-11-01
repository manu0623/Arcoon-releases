package com.nendo.argosy.ui.screens.settings

import com.nendo.argosy.ui.common.GraphicsBackendOptionUi
import com.nendo.argosy.ui.common.VulkanDriverOption

data class GraphicsBackendUi(
    val coreName: String,
    val isCoreOption: Boolean,
    val options: List<GraphicsBackendOptionUi>,
    val currentValue: String,
    val isOverridden: Boolean,
    val supportsVulkan: Boolean,
    val isVulkan: Boolean,
    val acceptsCustomDriver: Boolean
)

data class BuiltinGraphicsState(
    val isLoaded: Boolean = false,
    val loadedPlatformId: Long? = null,
    val coreName: String? = null,
    val backend: GraphicsBackendUi? = null,
    val customDriversSupported: Boolean = false,
    val installedDrivers: List<VulkanDriverOption> = emptyList(),
    val installablePackages: List<String> = emptyList(),
    val installingPackage: String? = null
)
