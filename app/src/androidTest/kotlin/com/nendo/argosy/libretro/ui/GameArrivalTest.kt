package com.nendo.argosy.libretro.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nendo.argosy.ui.theme.LocalMotionTier
import com.nendo.argosy.ui.theme.MotionTier
import com.nendo.argosy.ui.theme.generated.ComponentDefaults
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GameArrivalTest {
    @get:Rule
    val compose = createComposeRule()

    private val arrivalMs = ComponentDefaults.LaunchOverlay.arrivalMs
    private val frameMs = 16
    private val maxStep = frameMs.toFloat() / arrivalMs * 1.5f
    private val tolerance = 0.001f

    @Test
    fun coverFadesOffTheGameWithoutAnyFrameJumping() {
        val trace = alphaTrace(tier = MotionTier.Comfortable)
        assertEquals(1f, trace.first(), tolerance)
        assertEquals(0f, trace.last(), tolerance)
        trace.zipWithNext().forEach { (before, after) ->
            assertTrue("alpha rose from $before to $after", after <= before + tolerance)
            assertTrue("alpha stepped from $before to $after", before - after <= maxStep + tolerance)
        }
        assertTrue("the fade never left its start", trace.any { it > tolerance && it < 1f - tolerance })
    }

    @Test
    fun reducedMotionRevealsTheGameWithinOneFrame() {
        val trace = alphaTrace(tier = MotionTier.Reduced, frames = 1)
        assertEquals(1f, trace.first(), tolerance)
        assertEquals(0f, trace.last(), tolerance)
    }

    private fun alphaTrace(
        tier: MotionTier,
        frames: Int = arrivalMs / frameMs + 6
    ): List<Float> {
        val revealed = mutableStateOf(false)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CompositionLocalProvider(LocalMotionTier provides tier) {
                GameArrival(revealed = revealed.value, modifier = Modifier.fillMaxSize())
            }
        }
        compose.mainClock.advanceTimeByFrame()
        val trace = mutableListOf(readAlpha())
        compose.runOnIdle { revealed.value = true }
        repeat(frames) {
            compose.mainClock.advanceTimeByFrame()
            trace += readAlpha()
        }
        return trace
    }

    private fun readAlpha(): Float =
        compose.onNode(SemanticsMatcher.keyIsDefined(ArrivalAlpha)).fetchSemanticsNode().config[ArrivalAlpha]
}
