package com.nendo.argosy.ui.dualscreen

import com.nendo.argosy.ui.theme.generated.ComponentDefaults
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShowcaseMotionDurationsTest {
    @Test
    fun `comfortable motion reads every value from the tokens`() {
        val durations = showcaseMotionDurations(reduced = false)

        assertEquals(ComponentDefaults.GameShowcase.backdropCrossfadeMs, durations.backdropCrossfadeMs)
        assertEquals(ComponentDefaults.GameShowcase.enterMs, durations.enterMs)
        assertEquals(ComponentDefaults.GameShowcase.coverDelayMs, durations.coverDelayMs)
        assertEquals(ComponentDefaults.GameShowcase.textDelayMs, durations.textDelayMs)
        assertEquals(ComponentDefaults.GameShowcase.exitMs, durations.exitMs)
        assertEquals(ComponentDefaults.GameShowcase.coverDriftDp, durations.coverDriftDp)
        assertEquals(ComponentDefaults.GameShowcase.textRiseDp, durations.textRiseDp)
        assertEquals(ComponentDefaults.GameShowcase.floatTravelDp, durations.floatTravelDp)
        assertEquals(ComponentDefaults.GameShowcase.floatSlideMs, durations.floatSlideMs)
        assertEquals(ComponentDefaults.GameShowcase.floatHoldMs, durations.floatHoldMs)
        assertTrue(durations.floatEnabled)
        assertTrue(durations.enterMs > 0)
        assertTrue(durations.backdropCrossfadeMs > 0)
    }

    @Test
    fun `reduced motion collapses every duration and distance to zero and disables the float`() {
        val durations = showcaseMotionDurations(reduced = true)

        assertEquals(
            ShowcaseMotionDurations(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, floatEnabled = false),
            durations
        )
        assertFalse(durations.floatEnabled)
    }
}
