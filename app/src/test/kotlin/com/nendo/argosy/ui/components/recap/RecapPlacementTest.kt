package com.nendo.argosy.ui.components.recap

import org.junit.Assert.assertEquals
import org.junit.Test

class RecapPlacementTest {

    @Test
    fun `a larger presentation display takes the full card`() {
        assertEquals(RecapSurface.PRESENTATION, RecapPlacement.fullCardSurface(1_339_200L, 2_073_600L))
    }

    @Test
    fun `a larger control display keeps the full card`() {
        assertEquals(RecapSurface.CONTROL, RecapPlacement.fullCardSurface(2_073_600L, 1_339_200L))
    }

    @Test
    fun `equal displays give the full card to the presentation display`() {
        assertEquals(RecapSurface.PRESENTATION, RecapPlacement.fullCardSurface(2_073_600L, 2_073_600L))
    }

    @Test
    fun `a single screen keeps the full card on the control surface`() {
        assertEquals(RecapSurface.CONTROL, RecapPlacement.fullCardSurface(null, null))
        assertEquals(RecapSurface.CONTROL, RecapPlacement.fullCardSurface(2_073_600L, null))
    }

    @Test
    fun `an unknown control display keeps the full card on the control surface`() {
        assertEquals(RecapSurface.CONTROL, RecapPlacement.fullCardSurface(null, 2_073_600L))
    }

    @Test
    fun `growing the presentation display never moves the card back to control`() {
        val control = 1_500_000L
        val surfaces = listOf(1_000_000L, 1_499_999L, 1_500_000L, 1_500_001L, 3_000_000L)
            .map { RecapPlacement.fullCardSurface(control, it) }
        val firstPresentation = surfaces.indexOf(RecapSurface.PRESENTATION)
        assertEquals(2, firstPresentation)
        assertEquals(List(surfaces.size - firstPresentation) { RecapSurface.PRESENTATION }, surfaces.drop(firstPresentation))
    }
}
