package com.nendo.argosy.ui.components.launch

import kotlin.math.PI
import kotlin.math.cos

/**
 * The alpha of the light behind the launch art [elapsedMs] after the card appeared: a sine that
 * starts at [floor], peaks at [peak] halfway through each [periodMs] and returns to [floor]. Under
 * [reduced] motion, or with no period, it holds the midpoint.
 */
fun breathAlpha(
    elapsedMs: Long,
    periodMs: Int,
    floor: Float,
    peak: Float,
    reduced: Boolean
): Float {
    val swing = peak - floor
    if (reduced || periodMs <= 0) return floor + swing / 2f
    val phase = elapsedMs.mod(periodMs.toLong()).toDouble() / periodMs
    return floor + swing * ((1.0 - cos(2.0 * PI * phase)) / 2.0).toFloat()
}
