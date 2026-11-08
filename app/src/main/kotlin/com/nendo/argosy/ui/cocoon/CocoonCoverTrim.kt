package com.nendo.argosy.ui.cocoon

import android.graphics.Bitmap
import coil.size.Size
import coil.transform.Transformation
import kotlin.math.abs

/**
 * Takes off the filler bands some covers carry on their left and right: a tall box art padded out
 * to a square with a flat colour, a gradient or a blurred copy of itself. Left on, the square tile
 * shows the bands as coloured strips down the sides; trimmed, the tile is filled by the cover.
 *
 * A band is told apart from artwork by its lack of detail: filler is flat or smooth from top to
 * bottom, artwork is not. Only true padding is removed: smooth bands on both sides of about the
 * same width, and a clear edge where the cover itself begins. A design that merely sits on a plain
 * background has no such edge and is left whole.
 */
object CocoonCoverTrim : Transformation {
    override val cacheKey: String = "cocoon-cover-trim-3"

    private const val MIN_BAND = 0.03f
    private const val MAX_BAND = 0.36f
    private const val BAND_MISMATCH = 0.06f
    private const val SAMPLE_ROWS = 64
    /** Average luminance change between neighbouring sampled rows that still counts as smooth. */
    private const val SMOOTH_DETAIL = 12f
    /** A band must also be far smoother than the artwork in the middle. */
    private const val SMOOTH_SHARE = 0.45f
    /** How far inside the detected band the cover's own edge may sit (its edge can be plain too). */
    private const val EDGE_SEARCH = 10
    private const val EDGE_STEP = 28
    private const val EDGE_SHARE = 0.45f

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        val w = input.width
        val h = input.height
        if (w < 48 || h < 48) return input
        // Padding is added to make a picture square (or wider); a tall cover has none to remove.
        if (w < h * 0.85f) return input
        val rows = IntArray(SAMPLE_ROWS) { (it * (h - 1)) / (SAMPLE_ROWS - 1) }
        val detail = FloatArray(w) { x -> columnDetail(input, x, rows) }
        val middle = detail.copyOfRange((w * 0.4f).toInt(), (w * 0.6f).toInt()).sorted()
        val artDetail = middle.getOrElse(middle.size / 2) { 0f }
        val smoothLimit = minOf(SMOOTH_DETAIL, artDetail * SMOOTH_SHARE).coerceAtLeast(1.5f)
        val limit = (w * MAX_BAND).toInt()
        val left = (0 until limit).firstOrNull { x -> detail[x] > smoothLimit } ?: return input
        val right = (0 until limit).firstOrNull { x -> detail[w - 1 - x] > smoothLimit } ?: return input
        if (left < w * MIN_BAND || right < w * MIN_BAND || abs(left - right) > w * BAND_MISMATCH) return input
        val leftEdge = findEdge(input, left, rows, inward = 1) ?: return input
        val rightEdge = findEdge(input, w - 1 - right, rows, inward = -1) ?: return input
        val width = rightEdge - leftEdge + 1
        if (width < w / 2) return input
        return Bitmap.createBitmap(input, leftEdge, 0, width, h)
    }

    /** How much a column changes from top to bottom: low for flat or smoothly shaded filler. */
    private fun columnDetail(bitmap: Bitmap, x: Int, rows: IntArray): Float {
        var total = 0f
        var previous = luma(bitmap.getPixel(x, rows[0]))
        for (i in 1 until rows.size) {
            val current = luma(bitmap.getPixel(x, rows[i]))
            total += abs(current - previous)
            previous = current
        }
        return total / (rows.size - 1)
    }

    /**
     * Where the cover begins: the column at or a little before [x] (towards the band) with the
     * clearest step from the band on most rows, or null when there is no clear step at all.
     */
    private fun findEdge(bitmap: Bitmap, x: Int, rows: IntArray, inward: Int): Int? {
        var best: Int? = null
        var bestCount = (rows.size * EDGE_SHARE).toInt() - 1
        for (k in 0..EDGE_SEARCH) {
            val at = x - inward * k
            val outside = at - inward * 2
            val inside = at + inward
            if (outside !in 0 until bitmap.width || inside !in 0 until bitmap.width) continue
            val stepped = rows.count { y ->
                colourDistance(bitmap.getPixel(outside, y), bitmap.getPixel(inside, y)) > EDGE_STEP
            }
            if (stepped > bestCount) {
                bestCount = stepped
                best = at
            }
        }
        return best
    }

    private fun luma(c: Int): Float =
        0.299f * ((c shr 16) and 0xFF) + 0.587f * ((c shr 8) and 0xFF) + 0.114f * (c and 0xFF)

    private fun colourDistance(a: Int, b: Int): Int =
        maxOf(
            abs(((a shr 16) and 0xFF) - ((b shr 16) and 0xFF)),
            abs(((a shr 8) and 0xFF) - ((b shr 8) and 0xFF)),
            abs((a and 0xFF) - (b and 0xFF))
        )
}
