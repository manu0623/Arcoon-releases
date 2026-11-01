package com.nendo.argosy.libretro

import android.os.Build
import kotlin.math.hypot
import kotlin.math.roundToInt

data class PanelSpec(val widthPx: Int, val heightPx: Int, val diagonalInches: Float) {
    val pixelsPerInch: Float
        get() = (hypot(widthPx.toDouble(), heightPx.toDouble()) / diagonalInches).toFloat()
}

data class PixelSize(val width: Int, val height: Int)

data class EdgePadding(val left: Int, val top: Int, val right: Int)

/**
 * Picture sizes that draw a console's two screens at one physical scale across two panels of
 * different density, for consoles whose two screens share a pixel height. The second screen fills
 * its panel; the first is scaled to the same height in inches and capped to its own panel.
 */
data class MatchedScreens(val primary: PixelSize, val secondary: PixelSize) {

    /**
     * The padding that leaves the first screen's picture in a view of [viewWidth] x [viewHeight]
     * centred across and resting on the bottom edge, against the second screen below.
     */
    fun primaryPadding(viewWidth: Int, viewHeight: Int): EdgePadding {
        val side = ((viewWidth - primary.width) / 2).coerceAtLeast(0)
        val top = (viewHeight - primary.height).coerceAtLeast(0)
        return EdgePadding(left = side, top = top, right = side)
    }

    companion object {
        fun of(
            primaryPanel: PanelSpec,
            secondaryPanel: PanelSpec,
            primaryAspect: Float,
            secondaryAspect: Float
        ): MatchedScreens {
            val secondary = fit(secondaryPanel.widthPx, secondaryPanel.heightPx, secondaryAspect)
            val inches = secondary.height / secondaryPanel.pixelsPerInch
            val scaledHeight = inches * primaryPanel.pixelsPerInch
            val primary = fit(
                minOf((scaledHeight * primaryAspect).roundToInt(), primaryPanel.widthPx),
                minOf(scaledHeight.roundToInt(), primaryPanel.heightPx),
                primaryAspect
            )
            return MatchedScreens(primary, secondary)
        }

        private fun fit(boxWidth: Int, boxHeight: Int, aspect: Float): PixelSize =
            if (boxWidth.toFloat() / boxHeight > aspect) {
                PixelSize((boxHeight * aspect).roundToInt(), boxHeight)
            } else {
                PixelSize(boxWidth, (boxWidth / aspect).roundToInt())
            }
    }
}

object KnownPanels {
    internal val THOR = listOf(
        PanelSpec(widthPx = 1920, heightPx = 1080, diagonalInches = 6.0f),
        PanelSpec(widthPx = 1240, heightPx = 1080, diagonalInches = 3.92f)
    )

    /**
     * The built-in panels of [model] whose physical sizes are known, or an empty list.
     */
    fun forModel(model: String = Build.MODEL): List<PanelSpec> =
        if (com.nendo.argosy.util.AynThor.matches(model)) THOR else emptyList()

    /**
     * The panel among [panels] that a display of [widthPx] x [heightPx] is, in either orientation.
     */
    fun specFor(panels: List<PanelSpec>, widthPx: Int, heightPx: Int): PanelSpec? {
        val long = maxOf(widthPx, heightPx)
        val short = minOf(widthPx, heightPx)
        return panels.firstOrNull { it.widthPx == long && it.heightPx == short }
    }
}
