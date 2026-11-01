package com.nendo.argosy.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp

data class UiScaleConfig(
    val scale: Float = 1.0f,
    val aspectRatioClass: AspectRatioClass = AspectRatioClass.STANDARD,
    val bottomReservedFraction: Float = 0f,
    val compactFooter: Boolean = false
)

enum class AspectRatioClass {
    ULTRA_WIDE,   // >= 2.0 (21:9)
    WIDE,         // 1.6-2.0 (16:9, 16:10)
    STANDARD,     // 0.5-1.6
    TALL,         // 0.35-0.5 (9:16)
    ULTRA_TALL    // < 0.35 (9:21)
    ;

    val isWide: Boolean get() = this == WIDE || this == ULTRA_WIDE
}

fun aspectRatioClassOf(widthDp: Int, heightDp: Int): AspectRatioClass {
    val aspectRatio = widthDp.toFloat() / heightDp.coerceAtLeast(1).toFloat()
    return when {
        aspectRatio >= 2.0f -> AspectRatioClass.ULTRA_WIDE
        aspectRatio >= 1.6f -> AspectRatioClass.WIDE
        aspectRatio >= 0.5f -> AspectRatioClass.STANDARD
        aspectRatio >= 0.35f -> AspectRatioClass.TALL
        else -> AspectRatioClass.ULTRA_TALL
    }
}

val LocalUiScale = staticCompositionLocalOf { UiScaleConfig() }

@Composable
fun Dp.scaled(): Dp = this * LocalUiScale.current.scale
