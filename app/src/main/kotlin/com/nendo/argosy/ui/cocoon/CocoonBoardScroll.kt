package com.nendo.argosy.ui.cocoon

/**
 * How far the home board has to move for [focusedIndex] to be in view, and whether that move may be
 * animated.
 *
 * Cocoon's folder grid always slides: a cover coming into view enters from the edge of the screen
 * over the scroll animation, so the movement reads as travel. The board used to jump all at once
 * while it was still "settling" after appearing, so a folder that was out of its frame was simply
 * drawn on the next frame - it popped in instead of sliding in. That window reopened every time the
 * board came back after a folder had been open, which is exactly when it was noticed.
 *
 * The one move allowed to be instant is the first placement after the board appears, so a cold start
 * or a return does not play a long slide to the folder that already has the cursor. Every move after
 * that is animated, and the caller animates it with the same motion as the folder grid.
 */
internal data class BoardSlide(val deltaPx: Float, val instant: Boolean)

/**
 * The move needed to keep [focusedIndex] centred, or null when it is already where it should be.
 *
 * [firstVisibleItemIndex] and [firstVisibleItemScrollOffset] describe where the board is now,
 * [pitchPx] is one column's width plus the gap, and [firstPlacement] marks the move that follows the
 * board appearing.
 */
internal fun boardSlide(
    focusedIndex: Int,
    rows: Int,
    slotCount: Int,
    visibleColumns: Int,
    firstVisibleItemIndex: Int,
    firstVisibleItemScrollOffset: Int,
    pitchPx: Float,
    firstPlacement: Boolean,
): BoardSlide? {
    if (rows <= 0 || visibleColumns <= 0 || slotCount <= 0 || pitchPx <= 0f) return null
    val column = focusedIndex / rows
    val lastColumn = (slotCount - 1) / rows
    val maxFirst = (lastColumn - visibleColumns + 1).coerceAtLeast(0)
    val targetFirst = (column - visibleColumns / 2).coerceIn(0, maxFirst)
    val currentPx = firstVisibleItemIndex / rows * pitchPx + firstVisibleItemScrollOffset
    val delta = targetFirst * pitchPx - currentPx
    if (kotlin.math.abs(delta) < 1f) return null
    return BoardSlide(deltaPx = delta, instant = firstPlacement)
}





