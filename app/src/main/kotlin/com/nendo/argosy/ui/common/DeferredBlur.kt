package com.nendo.argosy.ui.common

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp

/**
 * [Modifier.blur][androidx.compose.ui.draw.blur] for a radius that animates. The radius is read
 * inside the layer, at draw time, so an opening drawer or menu only repaints the blurred content;
 * `blur(animatedValue)` re-ran the whole screen's composition on every frame of the animation.
 * Draws exactly like `blur` with its default edge treatment (clamped edges, clipped to bounds).
 */
fun Modifier.deferredBlur(radius: () -> Dp): Modifier = graphicsLayer {
    val px = radius().toPx()
    renderEffect = if (px > 0f) BlurEffect(px, px, TileMode.Clamp) else null
    clip = true
}
