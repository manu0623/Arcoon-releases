package com.nendo.argosy.ui.cocoon

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import kotlinx.coroutines.launch

/** When the screens last swapped, for windows that are rebuilt by the swap. */
object CocoonSwap {
    @Volatile var lastSwapAt = 0L
}

/** True for a moment after X / the quick toggle swapped the screens. */
fun cocoonSwapRecent(windowMs: Long = 1500): Boolean = System.currentTimeMillis() - CocoonSwap.lastSwapAt < windowMs

/**
 * Screen swap motion. The window keeps the last picture of what it showed and slides that picture
 * away toward the other screen (the top screen's slides down, the bottom screen's slides up),
 * uncovering the new content already in place underneath. Because the old picture covers the
 * screen until it moves, the moment the new content takes to draw never shows as a black flash.
 */
@Composable
fun CocoonSwapSlide(
    key: Any?,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val displayId = androidx.compose.ui.platform.LocalView.current.display?.displayId
    val isTop = displayId == null || displayId == android.view.Display.DEFAULT_DISPLAY
    val layerA = rememberGraphicsLayer()
    val layerB = rememberGraphicsLayer()
    val live = remember { intArrayOf(0) }
    val seen = remember { BooleanArray(1) }
    // Decided during composition: the layer still holds the old frame, so it becomes the
    // outgoing picture and the other layer starts recording the new content.
    val outgoing = remember(key) {
        val first = !seen[0]
        seen[0] = true
        if (!first && cocoonSwapRecent()) {
            val old = live[0]
            live[0] = 1 - old
            mutableIntStateOf(old)
        } else {
            mutableIntStateOf(-1)
        }
    }
    val progress = remember(key) { mutableFloatStateOf(0f) }
    LaunchedEffect(outgoing) {
        if (outgoing.intValue < 0) return@LaunchedEffect
        launch {
            // never leave the old picture stuck if this window stops getting frames
            kotlinx.coroutines.delay(900)
            outgoing.intValue = -1
        }
        kotlinx.coroutines.delay(80)
        animate(0f, 1f, animationSpec = tween(360, easing = com.nendo.argosy.ui.theme.Motion.argosyEase)) { value, _ ->
            progress.floatValue = value
        }
        outgoing.intValue = -1
    }
    Box(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
            .drawWithContent {
                val liveLayer = if (live[0] == 0) layerA else layerB
                liveLayer.record { this@drawWithContent.drawContent() }
                drawLayer(liveLayer)
                val out = outgoing.intValue
                if (out >= 0) {
                    val direction = if (isTop) 1f else -1f
                    translate(top = direction * progress.floatValue * size.height) {
                        drawLayer(if (out == 0) layerA else layerB)
                    }
                }
            },
        content = content
    )
}
