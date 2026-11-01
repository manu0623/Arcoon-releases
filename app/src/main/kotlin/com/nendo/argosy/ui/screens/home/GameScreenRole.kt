package com.nendo.argosy.ui.screens.home

import com.nendo.argosy.data.preferences.EmulatorDisplayTarget

internal fun gameDisplayTargetFor(
    displayId: Int,
    roleDisplayIds: Pair<Int, Int>?,
    appScreenDisplayId: Int?
): EmulatorDisplayTarget? = when {
    roleDisplayIds == null -> null
    displayId == roleDisplayIds.first -> EmulatorDisplayTarget.PRIMARY
    displayId == roleDisplayIds.second -> EmulatorDisplayTarget.PRESENTATION
    displayId == appScreenDisplayId -> EmulatorDisplayTarget.APP_SCREEN
    else -> null
}
