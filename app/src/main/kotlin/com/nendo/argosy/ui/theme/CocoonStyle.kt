package com.nendo.argosy.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nendo.argosy.R

/**
 * Poppins (SIL Open Font License 1.1, see assets/licenses/poppins-OFL.txt), the geometric face Cocoon
 * draws its UI in. Every generated text style points here; a user-picked font still overrides it.
 * There is no SemiBold file, so weight 600 resolves to the nearest bundled weight.
 */
val PoppinsFamily: FontFamily = FontFamily(
    Font(R.font.poppins_light, FontWeight.Light),
    Font(R.font.poppins_regular, FontWeight.Normal),
    Font(R.font.poppins_medium, FontWeight.Medium),
    Font(R.font.poppins_bold, FontWeight.Bold)
)

/**
 * Cocoon's out-of-game surface, measured from Cocoon 3.11 captures on the AYN Thor: near-black base,
 * a field of tilted rounded tiles one shade lighter, dark rounded chips and pills on top.
 * The pattern is drawn in code so the fork ships no Cocoon artwork.
 */
object CocoonSurface {
    // Each colour follows the active Cocoon theme (Settings ▸ Theme ▸ Themes) when it sets one.
    private val theme get() = com.nendo.argosy.ui.cocoon.theme.CocoonThemeEngine.colors
    val Background: Color get() = theme?.backgroundStart ?: Color(0xFF000000)
    val Tile: Color get() = theme?.tileBackground ?: Color(0xFF151515)
    private val glass get() = com.nendo.argosy.ui.cocoon.theme.CocoonThemeEngine.settings.glass
    // Glass surfaces: the same panels, see-through over whatever is (blurred) behind them.
    val Panel: Color get() = (theme?.cardStart ?: Color(0xFF141414)).let { if (glass) it.copy(alpha = 0.55f) else it }
    val Chip: Color get() = (theme?.cardEnd ?: theme?.cardStart ?: Color(0xFF1C1C1C)).let { if (glass) it.copy(alpha = 0.6f) else it }
    val TextPrimary: Color get() = theme?.textPrimary ?: Color(0xFFF5F5F5)
    val TextSecondary: Color get() = theme?.textSecondary ?: Color(0xFF8A8A8A)

    val tileWidth: Dp = 48.dp
    val tileHeight: Dp = 66.dp
    val tileGapX: Dp = 22.dp
    val tileGapY: Dp = 18.dp
    val tileCorner: Dp = 10.dp
    const val tiltDegrees: Float = -12f
}

/** One tile of the wallpaper briefly shrinking and growing back. */
private class WallpaperPulse(val col: Int, val row: Int, val start: Long)

private const val PULSE_MS = 900L

/** Where the wallpaper's tiles sit for a given canvas size (all measures follow its height). */
private class WallpaperGeometry(width: Float, height: Float) {
    val tileW = height * 0.171f
    val tileH = height * 0.144f
    val pitchX = tileW + height * 0.052f
    val pitchY = height * 0.18f
    val skew = height * 0.065f
    val corner = height * 0.0185f
    val cols = (width / pitchX).toInt() + 3
    val rows = (height / pitchY).toInt() + 2

    fun centerX(col: Int) = col * pitchX - pitchX * 0.5f
    fun centerY(row: Int) = row * pitchY + pitchY * 0.25f

    /** A tile centred on the origin, [scale]d, with its lean reduced by [straighten] (0..1). */
    fun tilePath(scale: Float, straighten: Float): androidx.compose.ui.graphics.Path {
        val w = tileW * scale
        val h = tileH * scale
        val path = androidx.compose.ui.graphics.Path().apply {
            addRoundRect(
                androidx.compose.ui.geometry.RoundRect(
                    left = -w / 2f, top = -h / 2f, right = w / 2f, bottom = h / 2f,
                    cornerRadius = CornerRadius(corner * scale)
                )
            )
        }
        val matrix = androidx.compose.ui.graphics.Matrix()
        // shear: the top of the tile leans right
        matrix.values[androidx.compose.ui.graphics.Matrix.SkewX] = -(skew * (1f - straighten) / tileH)
        path.transform(matrix)
        return path
    }
}

/**
 * Cocoon's animated wallpaper, measured from its top screen on the Thor: a black field of slanted,
 * rounded tiles a shade lighter, in straight columns. Every half second or so one tile, picked at
 * random, shrinks to a small square and grows back. The still field is drawn once into a bitmap;
 * each frame only repaints the tiles that are moving, so it costs next to nothing.
 */
@Composable
fun CocoonWallpaper(modifier: Modifier = Modifier, animated: Boolean = true) {
    // A theme's top-screen wallpaper replaces the built-in tile field.
    com.nendo.argosy.ui.cocoon.theme.CocoonThemeEngine.active.value?.wallpaperExternal?.let { file ->
        com.nendo.argosy.ui.cocoon.theme.CocoonThemeWallpaper(file, modifier)
        return
    }
    val pulses = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateListOf<WallpaperPulse>() }
    var now by androidx.compose.runtime.remember { androidx.compose.runtime.mutableLongStateOf(0L) }
    if (animated) {
        androidx.compose.runtime.LaunchedEffect(Unit) {
            val random = kotlin.random.Random
            var next = 0L
            while (true) {
                androidx.compose.runtime.withFrameMillis { t ->
                    now = t
                    if (next == 0L) next = t + 300
                    if (t >= next) {
                        pulses.add(WallpaperPulse(random.nextInt(0, 1000), random.nextInt(0, 1000), t))
                        next = t + random.nextLong(380, 900)
                    }
                    if (pulses.any { t - it.start > PULSE_MS }) pulses.removeAll { t - it.start > PULSE_MS }
                }
            }
        }
    }
    androidx.compose.foundation.layout.Spacer(
        modifier = modifier
            .fillMaxSize()
            .drawWithCache {
                val geo = WallpaperGeometry(size.width, size.height)
                val base = geo.tilePath(1f, 0f)
                val still = androidx.compose.ui.graphics.ImageBitmap(
                    size.width.toInt().coerceAtLeast(1), size.height.toInt().coerceAtLeast(1)
                )
                androidx.compose.ui.graphics.drawscope.CanvasDrawScope().draw(
                    this, layoutDirection, androidx.compose.ui.graphics.Canvas(still), size
                ) {
                    drawRect(WallpaperBackground)
                    for (row in 0 until geo.rows) {
                        for (col in 0 until geo.cols) {
                            translate(geo.centerX(col), geo.centerY(row)) { drawPath(base, WallpaperTile) }
                        }
                    }
                }
                onDrawBehind {
                    drawImage(still)
                    val t = now
                    pulses.forEach { pulse ->
                        val col = pulse.col % geo.cols
                        val row = pulse.row % geo.rows
                        val p = ((t - pulse.start).toFloat() / PULSE_MS).coerceIn(0f, 1f)
                        val dip = kotlin.math.sin(p * kotlin.math.PI.toFloat())
                        translate(geo.centerX(col), geo.centerY(row)) {
                            drawPath(base, WallpaperBackground)
                            drawPath(geo.tilePath(1f - 0.65f * dip, dip), WallpaperTile)
                        }
                    }
                }
            }
    )
}

private val WallpaperBackground = Color(0xFF000000)
private val WallpaperTile = Color(0xFF0A0A0A)
