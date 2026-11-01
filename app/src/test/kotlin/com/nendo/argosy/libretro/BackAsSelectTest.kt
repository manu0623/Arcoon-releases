package com.nendo.argosy.libretro

import com.nendo.argosy.data.local.entity.SelectAsBackMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackAsSelectTest {

    @Test
    fun `auto plays Back as Select only on a gamepad without its own Select`() {
        assertTrue(backPlaysAsSelect(SelectAsBackMode.AUTO, fromGamepad = true, deviceHasSelect = false))
        assertFalse(backPlaysAsSelect(SelectAsBackMode.AUTO, fromGamepad = true, deviceHasSelect = true))
        assertFalse(backPlaysAsSelect(SelectAsBackMode.AUTO, fromGamepad = false, deviceHasSelect = false))
    }

    @Test
    fun `on plays Back as Select on every gamepad and never off one`() {
        assertTrue(backPlaysAsSelect(SelectAsBackMode.ON, fromGamepad = true, deviceHasSelect = true))
        assertTrue(backPlaysAsSelect(SelectAsBackMode.ON, fromGamepad = true, deviceHasSelect = false))
        assertFalse(backPlaysAsSelect(SelectAsBackMode.ON, fromGamepad = false, deviceHasSelect = false))
    }

    @Test
    fun `off always keeps Back as the menu`() {
        assertFalse(backPlaysAsSelect(SelectAsBackMode.OFF, fromGamepad = true, deviceHasSelect = false))
    }

    @Test
    fun `an unknown stored value reads as auto`() {
        assertEquals(SelectAsBackMode.AUTO, SelectAsBackMode.fromString(null))
        assertEquals(SelectAsBackMode.AUTO, SelectAsBackMode.fromString("garbage"))
        assertEquals(SelectAsBackMode.ON, SelectAsBackMode.fromString("ON"))
    }
}
