package com.nendo.argosy.ui.components.launch

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
class LaunchVeilTest {
    @get:Rule
    val compose = createComposeRule()

    private val veilMs = ComponentDefaults.LaunchOverlay.veilMs
    private val frameMs = 16
    private val maxStep = frameMs.toFloat() / veilMs * 1.5f
    private val tolerance = 0.001f

    @Test
    fun veilFadesInToBlackWithoutAnyFrameJumping() {
        val trace = alphaTrace(from = false, to = true, tier = MotionTier.Comfortable)
        assertEquals(0f, trace.first(), tolerance)
        assertEquals(1f, trace.last(), tolerance)
        trace.zipWithNext().forEach { (before, after) ->
            assertTrue("alpha fell from $before to $after", after >= before - tolerance)
            assertTrue("alpha stepped from $before to $after", after - before <= maxStep + tolerance)
        }
        assertTrue("the fade never left its start", trace.any { it > tolerance && it < 1f - tolerance })
    }

    @Test
    fun veilLiftsBackToTheShowcaseWithoutAnyFrameJumping() {
        val trace = alphaTrace(from = true, to = false, tier = MotionTier.Comfortable)
        assertEquals(1f, trace.first(), tolerance)
        assertEquals(0f, trace.last(), tolerance)
        trace.zipWithNext().forEach { (before, after) ->
            assertTrue("alpha rose from $before to $after", after <= before + tolerance)
            assertTrue("alpha stepped from $before to $after", before - after <= maxStep + tolerance)
        }
        assertTrue("the lift never left its start", trace.any { it > tolerance && it < 1f - tolerance })
    }

    @Test
    fun reducedMotionCutsToBlackWithinOneFrame() {
        val trace = alphaTrace(from = false, to = true, tier = MotionTier.Reduced, frames = 1)
        assertEquals(0f, trace.first(), tolerance)
        assertEquals(1f, trace.last(), tolerance)
    }

    private fun alphaTrace(
        from: Boolean,
        to: Boolean,
        tier: MotionTier,
        frames: Int = veilMs / frameMs + 6
    ): List<Float> {
        val veiled = mutableStateOf(from)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CompositionLocalProvider(LocalMotionTier provides tier) {
                LaunchVeil(veiled = veiled.value, modifier = Modifier.fillMaxSize())
            }
        }
        compose.mainClock.advanceTimeByFrame()
        val trace = mutableListOf(readAlpha())
        compose.runOnIdle { veiled.value = to }
        repeat(frames) {
            compose.mainClock.advanceTimeByFrame()
            trace += readAlpha()
        }
        return trace
    }

    private fun readAlpha(): Float =
        compose.onNode(SemanticsMatcher.keyIsDefined(VeilAlpha)).fetchSemanticsNode().config[VeilAlpha]
}
