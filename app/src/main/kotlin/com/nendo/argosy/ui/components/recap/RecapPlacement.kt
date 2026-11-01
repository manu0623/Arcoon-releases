package com.nendo.argosy.ui.components.recap

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.nendo.argosy.DualScreenManagerHolder
import com.nendo.argosy.ui.dualscreen.PresentationSlot

enum class RecapSurface { CONTROL, PRESENTATION }

object RecapPlacement {
    /**
     * The surface that shows the full recap card: the one on the larger display by pixel area, the
     * presentation display on a tie, and the control surface whenever either area is unknown.
     */
    fun fullCardSurface(controlArea: Long?, presentationArea: Long?): RecapSurface = when {
        controlArea == null || presentationArea == null -> RecapSurface.CONTROL
        presentationArea >= controlArea -> RecapSurface.PRESENTATION
        else -> RecapSurface.CONTROL
    }
}

@Composable
fun rememberRecapFullCardSurface(): RecapSurface {
    val manager = DualScreenManagerHolder.instance ?: return RecapSurface.CONTROL
    val presentationUp by manager.presentationShowsHints.collectAsState()
    val slot by manager.presentationSlot.collectAsState()
    val swapped by manager.isRolesSwapped.collectAsState()
    val presentationHostsOverlay = presentationUp && slot !is PresentationSlot.InGame
    val areas = remember(presentationHostsOverlay, swapped) {
        if (presentationHostsOverlay) manager.roleDisplayAreas() else null to null
    }
    return RecapPlacement.fullCardSurface(areas.first, areas.second)
}
