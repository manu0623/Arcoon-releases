package com.nendo.argosy.ui.cocoon

import android.graphics.Bitmap
import coil.size.Size
import coil.transform.Transformation
import kotlin.math.abs

/**
 * Takes off the flat bands some covers carry on their left and right: a tall box art padded out
 * to a square with one solid colour. Left on, the square tile zooms the whole padded picture and
 * the bands stay; trimmed, the cover itself fills the tile.
 *
 * Only true padding is removed: both bands one solid colour over the full height, the same colour
 * and nearly the same width on each side, and a clear edge where the cover begins. A design that
 * merely sits on a plain background (a logo on yellow) has no such edge and is left whole.
 */
object CocoonCoverTrim : Transformation {
    override val cacheKey: String = "cocoon-cover-trim-1"

    private const val MIN_BAND = 0.05f
    private const val MAX_BAND = 0.3f
    private const val BAND_MISMATCH = 0.04f
    private const val TOLERANCE = 20
    private const val EDGE_SHARE = 0.6f
    private const val SAMPLE_ROWS = 48

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        val w = input.width
        val h = input.height
        if (w < 32 || h < 32 || w < h * 0.9f) return input
        val rows = (0 until SAMPLE_ROWS).map { (it * (h - 1)) / (SAMPLE_ROWS - 1) }
        val leftColor = input.getPixel(1, h / 2)
        val rightColor = input.getPixel(w - 2, h / 2)
        if (!close(leftColor, rightColor)) return input
        val limit = (w * MAX_BAND).toInt()
        val left = (0 until limit).firstOrNull { x -> !columnIs(input, x, rows, leftColor) } ?: return input
        val right = (0 until limit).firstOrNull { x -> !columnIs(input, w - 1 - x, rows, rightColor) } ?: return input
        if (left < w * MIN_BAND || right < w * MIN_BAND || abs(left - right) > w * BAND_MISMATCH) return input
        if (!sharpEdge(input, left + 1, rows, leftColor) || !sharpEdge(input, w - 2 - right, rows, rightColor)) {
            return input
        }
        return Bitmap.createBitmap(input, left, 0, w - left - right, h)
    }

    private fun columnIs(bitmap: Bitmap, x: Int, rows: List<Int>, color: Int): Boolean =
        rows.all { y -> close(bitmap.getPixel(x, y), color) }

    private fun sharpEdge(bitmap: Bitmap, x: Int, rows: List<Int>, band: Int): Boolean {
        if (x !in 0 until bitmap.width) return false
        return rows.count { y -> !close(bitmap.getPixel(x, y), band) } >= rows.size * EDGE_SHARE
    }

    private fun close(a: Int, b: Int): Boolean =
        abs(((a shr 16) and 0xFF) - ((b shr 16) and 0xFF)) <= TOLERANCE &&
            abs(((a shr 8) and 0xFF) - ((b shr 8) and 0xFF)) <= TOLERANCE &&
            abs((a and 0xFF) - (b and 0xFF)) <= TOLERANCE
}
