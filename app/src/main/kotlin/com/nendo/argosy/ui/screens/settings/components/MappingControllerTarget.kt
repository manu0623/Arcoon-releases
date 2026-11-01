package com.nendo.argosy.ui.screens.settings.components

import com.nendo.argosy.core.input.ActiveController
import com.nendo.argosy.data.repository.ControllerInfo

internal fun ActiveController.resolveIn(controllers: List<ControllerInfo>): ControllerInfo? =
    controllers.firstOrNull { it.deviceId == deviceId && it.controllerId == controllerId }
        ?: controllers.firstOrNull { it.controllerId == controllerId }

internal fun mappingControllerToLoad(
    editing: ControllerInfo?,
    loadedControllerId: String?,
    recording: Boolean,
    active: ActiveController?,
    controllers: List<ControllerInfo>
): ControllerInfo? {
    if (recording) return null
    val resolved = active?.resolveIn(controllers)
    if (resolved != null && resolved.controllerId != editing?.controllerId) return resolved
    return editing?.takeIf { it.controllerId != loadedControllerId }
}
