package com.nendo.argosy.libretro

import com.nendo.argosy.ui.theme.generated.ComponentDefaults
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioRiseTest {

    private val riseMs = ComponentDefaults.LaunchOverlay.audioRiseMs
    private val target = 0.8f
    private val tolerance = 0.0001f

    @Test
    fun `the rise starts from silence`() {
        assertEquals(0f, riseVolume(0L, riseMs, target), tolerance)
    }

    @Test
    fun `the rise ends at the configured gain and stays there`() {
        assertEquals(target, riseVolume(riseMs.toLong(), riseMs, target), tolerance)
        assertEquals(target, riseVolume(riseMs * 3L, riseMs, target), tolerance)
    }

    @Test
    fun `the rise is linear and never falls back`() {
        assertEquals(target / 2, riseVolume(riseMs / 2L, riseMs, target), tolerance)
        val curve = (0..riseMs step 50).map { riseVolume(it.toLong(), riseMs, target) }
        curve.zipWithNext().forEach { (before, after) ->
            assertTrue("volume fell from $before to $after", after >= before)
        }
    }

    @Test
    fun `time before the first frame clamps to silence`() {
        assertEquals(0f, riseVolume(-100L, riseMs, target), tolerance)
    }

    @Test
    fun `a zero or negative duration applies the gain at once`() {
        assertEquals(target, riseVolume(0L, 0, target), tolerance)
        assertEquals(target, riseVolume(0L, -1, target), tolerance)
    }
}
