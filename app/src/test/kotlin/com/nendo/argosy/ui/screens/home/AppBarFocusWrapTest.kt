package com.nendo.argosy.ui.screens.home

import org.junit.Assert.assertEquals
import org.junit.Test

private const val DRAWER = -1

/**
 * The app bar runs from the drawer at -1 through each slot, and wraps at both ends.
 */
class AppBarFocusWrapTest {

    private fun move(current: Int, delta: Int, slots: Int): Int =
        appBarFocusMove(current, delta, slots)

    @Test
    fun `right steps off the drawer onto the first slot`() {
        assertEquals(0, move(current = DRAWER, delta = 1, slots = 4))
    }

    @Test
    fun `right advances one slot at a time`() {
        assertEquals(1, move(current = 0, delta = 1, slots = 4))
        assertEquals(2, move(current = 1, delta = 1, slots = 4))
        assertEquals(3, move(current = 2, delta = 1, slots = 4))
    }

    @Test
    fun `right off the last slot wraps to the drawer`() {
        assertEquals(DRAWER, move(current = 3, delta = 1, slots = 4))
    }

    @Test
    fun `left off the drawer wraps to the last slot`() {
        assertEquals(3, move(current = DRAWER, delta = -1, slots = 4))
    }

    @Test
    fun `left retreats one slot at a time`() {
        assertEquals(2, move(current = 3, delta = -1, slots = 4))
        assertEquals(0, move(current = 1, delta = -1, slots = 4))
    }

    @Test
    fun `left off the first slot lands on the drawer`() {
        assertEquals(DRAWER, move(current = 0, delta = -1, slots = 4))
    }

    @Test
    fun `a bar with one slot alternates between it and the drawer`() {
        assertEquals(0, move(current = DRAWER, delta = 1, slots = 1))
        assertEquals(DRAWER, move(current = 0, delta = 1, slots = 1))
    }
}
