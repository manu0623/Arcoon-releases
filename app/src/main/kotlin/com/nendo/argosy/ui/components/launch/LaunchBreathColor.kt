package com.nendo.argosy.ui.components.launch

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlin.math.PI
import kotlin.math.cos

private const val WARMEST_HUE_DEGREES = 30.0

/**
 * Whichever of [a] and [b] is warmer, warmth being saturation times the cosine of the hue's
 * distance from orange (30°) on the colour circle. Equal warmth, as with two greys, goes to the
 * lighter of the two, and to [a] when they are also equally light.
 */
fun warmerOf(a: Color, b: Color): Color {
    val warmthA = warmth(a)
    val warmthB = warmth(b)
    return when {
        warmthA > warmthB -> a
        warmthB > warmthA -> b
        b.luminance() > a.luminance() -> b
        else -> a
    }
}

private fun warmth(color: Color): Double {
    val max = maxOf(color.red, color.green, color.blue)
    val min = minOf(color.red, color.green, color.blue)
    val chroma = max - min
    if (max <= 0f || chroma <= 0f) return 0.0
    val sector = when (max) {
        color.red -> ((color.green - color.blue) / chroma).toDouble().mod(6.0)
        color.green -> (color.blue - color.red) / chroma + 2.0
        else -> (color.red - color.green) / chroma + 4.0
    }
    val hueRadians = (sector * 60.0 - WARMEST_HUE_DEGREES) * PI / 180.0
    return (chroma / max) * cos(hueRadians)
}
