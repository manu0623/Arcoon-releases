package com.nendo.argosy.hardware

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.core.graphics.luminance

object ScreenColorExtractor {

    private const val BLACK_THRESHOLD = 0.03f

    fun extract(bitmap: Bitmap): Pair<SideColors, SideColors> {
        val content = findContentBounds(bitmap)
        val midX = (content.left + content.right) / 2

        val leftColors = sampleRegion(bitmap, content.left, content.top, midX, content.bottom)
        val rightColors = sampleRegion(bitmap, midX, content.top, content.right, content.bottom)

        return Pair(
            extractSideColors(leftColors),
            extractSideColors(rightColors)
        )
    }

    private fun findContentBounds(bitmap: Bitmap): ContentRect {
        val w = bitmap.width
        val h = bitmap.height
        val stepX = (w / 16).coerceAtLeast(1)
        val stepY = (h / 16).coerceAtLeast(1)

        var top = 0
        for (y in 0 until h step stepY) {
            if (rowHasContent(bitmap, y, w, stepX)) { top = y; break }
        }

        var bottom = h
        for (y in h - 1 downTo top step stepY) {
            if (rowHasContent(bitmap, y, w, stepX)) { bottom = (y + 1).coerceAtMost(h); break }
        }

        var left = 0
        for (x in 0 until w step stepX) {
            if (colHasContent(bitmap, x, top, bottom, stepY)) { left = x; break }
        }

        var right = w
        for (x in w - 1 downTo left step stepX) {
            if (colHasContent(bitmap, x, top, bottom, stepY)) { right = (x + 1).coerceAtMost(w); break }
        }

        if (right - left < w / 8 || bottom - top < h / 8) {
            return ContentRect(0, 0, w, h)
        }

        return ContentRect(left, top, right, bottom)
    }

    private fun rowHasContent(bitmap: Bitmap, y: Int, width: Int, step: Int): Boolean {
        for (x in 0 until width step step) {
            if (!isBlack(bitmap.getPixel(x, y))) return true
        }
        return false
    }

    private fun colHasContent(bitmap: Bitmap, x: Int, top: Int, bottom: Int, step: Int): Boolean {
        for (y in top until bottom step step) {
            if (!isBlack(bitmap.getPixel(x, y))) return true
        }
        return false
    }

    private fun isBlack(pixel: Int): Boolean {
        val r = android.graphics.Color.red(pixel) / 255f
        val g = android.graphics.Color.green(pixel) / 255f
        val b = android.graphics.Color.blue(pixel) / 255f
        return r < BLACK_THRESHOLD && g < BLACK_THRESHOLD && b < BLACK_THRESHOLD
    }

    private data class ContentRect(val left: Int, val top: Int, val right: Int, val bottom: Int)

    private fun sampleRegion(
        bitmap: Bitmap,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int
    ): List<Color> {
        val colors = mutableListOf<Color>()
        val stepX = (right - left) / 4
        val stepY = (bottom - top) / 4
        if (stepX <= 0 || stepY <= 0) return listOf(Color.Black, Color.Black, Color.Black)

        for (x in 1..3) {
            for (y in 1..3) {
                val px = (left + x * stepX).coerceIn(0, bitmap.width - 1)
                val py = (top + y * stepY).coerceIn(0, bitmap.height - 1)
                val pixel = bitmap.getPixel(px, py)
                colors.add(Color(pixel))
            }
        }
        return colors
    }

    private fun extractSideColors(samples: List<Color>): SideColors {
        val nonBlack = samples.filter { !isBlackColor(it) }
        val usable = if (nonBlack.size >= 3) nonBlack else samples
        val sorted = usable.sortedBy { it.toArgb().luminance }

        return SideColors(
            low = sorted[sorted.size / 4],
            mid = sorted[sorted.size / 2],
            high = sorted[sorted.size * 3 / 4]
        )
    }

    private fun isBlackColor(color: Color): Boolean {
        return color.red < BLACK_THRESHOLD && color.green < BLACK_THRESHOLD && color.blue < BLACK_THRESHOLD
    }

    private fun Color.toArgb(): Int {
        return android.graphics.Color.argb(
            (alpha * 255).toInt(),
            (red * 255).toInt(),
            (green * 255).toInt(),
            (blue * 255).toInt()
        )
    }
}
