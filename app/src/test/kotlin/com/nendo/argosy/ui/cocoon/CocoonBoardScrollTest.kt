package com.nendo.argosy.ui.cocoon

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CocoonBoardScrollTest {

    // 3 rows, 200 px per column, 5 columns in view, 10 columns of folders.
    private fun slide(
        focusedIndex: Int,
        firstVisibleItemIndex: Int = 0,
        firstVisibleItemScrollOffset: Int = 0,
        visibleColumns: Int = 5,
        slotCount: Int = 30,
        rows: Int = 3,
        firstPlacement: Boolean = false,
    ) = boardSlide(
        focusedIndex = focusedIndex,
        rows = rows,
        slotCount = slotCount,
        visibleColumns = visibleColumns,
        firstVisibleItemIndex = firstVisibleItemIndex,
        firstVisibleItemScrollOffset = firstVisibleItemScrollOffset,
        pitchPx = 200f,
        firstPlacement = firstPlacement
    )

    @Test
    fun `a folder already in place needs no move`() {
        assertNull(slide(focusedIndex = 0))
        // column 3, board already sitting centred on column 1 (first visible column 1)
        assertNull(slide(focusedIndex = 9, firstVisibleItemIndex = 3))
    }

    @Test
    fun `the first placement after the board appears does not animate`() {
        val first = slide(focusedIndex = 15, firstPlacement = true)
        assertEquals(600f, first?.deltaPx)
        assertTrue(first?.instant == true)
    }

    @Test
    fun `every move after the first placement slides`() {
        // Same geometry, same target: the only difference is that the board has already appeared,
        // and that is what used to be an instant jump right after coming back from a folder.
        val later = slide(focusedIndex = 15, firstPlacement = false)
        assertEquals(600f, later?.deltaPx)
        assertFalse(later?.instant == true)
    }

    @Test
    fun `the focused column is centred once the board can scroll`() {
        // focus on column 5 of 10 with 5 in view: columns 3..7 visible, centred on 3
        assertEquals(600f, slide(focusedIndex = 15)?.deltaPx)
        // the board has drifted: half a column short of centre
        assertEquals(480f, slide(focusedIndex = 15, firstVisibleItemScrollOffset = 120)?.deltaPx)
    }

    @Test
    fun `the board never scrolls past its last column`() {
        // last column is 9: centring it would need first column 7, but only 5 fit, so cap at 5
        assertEquals(1_000f, slide(focusedIndex = 27)?.deltaPx)
    }

    @Test
    fun `a board that fits on one screen never moves`() {
        assertNull(slide(focusedIndex = 9, visibleColumns = 12, slotCount = 30))
    }

    @Test
    fun `nonsense geometry is ignored`() {
        assertNull(slide(focusedIndex = 3, rows = 0))
        assertNull(slide(focusedIndex = 3, slotCount = 0))
        assertNull(slide(focusedIndex = 3, visibleColumns = 0))
    }
}
