package com.nendo.argosy.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.core.graphics.ColorUtils
import com.nendo.argosy.ui.theme.generated.ColorTokens

object ALauncherColors {
    val Indigo = Color(0xFF5C6BC0)
    val IndigoDark = Color(0xFF26418F)

    val Cyan = Color(0xFF00ACC1)
    val CyanDark = Color(0xFF007C91)

    val Teal = Color(0xFF26A69A)
    val TealDark = Color(0xFF00766C)

    val Orange = Color(0xFFFF7043)
    val OrangeDark = Color(0xFFC63F17)

    val Green = Color(0xFF66BB6A)
    val GreenDark = Color(0xFF388E3C)

    val Mint = Color(0xFF3FD9A8)
    val MintDark = Color(0xFF189C76)

    val SurfaceDark = Color(0xFF121212)
    val SurfaceDarkVariant = Color(0xFF1E1E1E)
    val SurfaceLight = Color(0xFFFFFBFE)
    val SurfaceLightVariant = Color(0xFFF5F5F5)

    val OnSurfaceDark = Color(0xFFE1E1E1)
    val OnSurfaceLight = Color(0xFF1C1B1F)

    val StarGold = Color(0xFFFFD700)
    val DifficultyRed = Color(0xFFE53935)
    val TrophyAmber = Color(0xFFFFB300)

    val CompletionPlaying = Color(0xFF5C6BC0)
    val CompletionBeaten = Color(0xFF66BB6A)
    val CompletionCompleted = Color(0xFFFFB300)
}

fun hueToColorInt(hue: Float, saturation: Float = 0.7f, lightness: Float = 0.5f): Int {
    return ColorUtils.HSLToColor(floatArrayOf(hue, saturation, lightness))
}

private const val ACCENT_MIN_CONTRAST = 4.5f
private const val ACCENT_DARK_LIGHTNESS_CAP = 0.7f
private const val ACCENT_LIGHT_LIGHTNESS_CAP = 0.4f
private const val ACCENT_LIGHTNESS_STEP = 0.01f
private const val CONTRAST_OFFSET = 0.05f

/**
 * [accent] with its HSL lightness moved away from the theme surface until it reaches text contrast
 * against it: raised toward 70% in dark mode, lowered toward 40% in light mode, and never past
 * those caps. Hue and saturation are kept, and an accent that already reads is returned unchanged.
 */
fun readableAccent(accent: Color, isDarkTheme: Boolean): Color {
    val surface = if (isDarkTheme) ColorTokens.Scheme.Dark.surface else ColorTokens.Scheme.Light.surface
    val (hue, saturation, lightness) = accent.toHsl()
    val cap = if (isDarkTheme) ACCENT_DARK_LIGHTNESS_CAP else ACCENT_LIGHT_LIGHTNESS_CAP
    val step = if (isDarkTheme) ACCENT_LIGHTNESS_STEP else -ACCENT_LIGHTNESS_STEP
    var current = lightness
    var candidate = accent
    while (contrast(candidate, surface) < ACCENT_MIN_CONTRAST) {
        val next = current + step
        if (if (isDarkTheme) next > cap else next < cap) break
        current = next
        candidate = Color.hsl(hue, saturation, current, accent.alpha)
    }
    return candidate
}

private fun contrast(a: Color, b: Color): Float {
    val la = a.luminance() + CONTRAST_OFFSET
    val lb = b.luminance() + CONTRAST_OFFSET
    return maxOf(la, lb) / minOf(la, lb)
}

private fun Color.toHsl(): Triple<Float, Float, Float> {
    val max = maxOf(red, green, blue)
    val min = minOf(red, green, blue)
    val lightness = (max + min) / 2f
    val delta = max - min
    if (delta == 0f) return Triple(0f, 0f, lightness)
    val saturation = delta / (1f - kotlin.math.abs(2f * lightness - 1f))
    val hue = when (max) {
        red -> ((green - blue) / delta).mod(6f)
        green -> (blue - red) / delta + 2f
        else -> (red - green) / delta + 4f
    } * 60f
    return Triple(hue, saturation.coerceIn(0f, 1f), lightness)
}

fun colorIntToHue(colorInt: Int): Float {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(colorInt, hsl)
    return hsl[0]
}
