package com.nendo.argosy.libretro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class DualScreenOutputTest {

    @Test
    fun `melonDS DS splits across both displays held to a single Top-Bottom layout`() {
        val output = DualScreenOutput.forCore("melondsds")

        assertNotNull(output)
        assertEquals(
            mapOf(
                "melonds_number_of_screen_layouts" to "1",
                "melonds_screen_layout1" to "top-bottom",
                "melonds_screen_gap" to "0",
                "melonds_touch_mode" to "touch"
            ),
            output!!.coreOptions
        )
    }

    @Test
    fun `both melonDS cores show the same halves of the frame`() {
        val legacy = DualScreenOutput.forCore("melonds")!!
        val ds = DualScreenOutput.forCore("melondsds")!!

        assertEquals(legacy.primaryAspect, ds.primaryAspect, 0f)
        assertEquals(legacy.secondaryAspect, ds.secondaryAspect, 0f)
        assertSame(legacy.primary, ds.primary)
        assertSame(legacy.secondary, ds.secondary)
    }

    @Test
    fun `match screen size applies to both melonDS cores and not to Azahar`() {
        assertEquals(true, DualScreenOutput.forCore("melonds")!!.matchesScreenSize)
        assertEquals(true, DualScreenOutput.forCore("melondsds")!!.matchesScreenSize)
        assertEquals(false, DualScreenOutput.forCore("azahar")!!.matchesScreenSize)
    }

    @Test
    fun `a single-screen core has no split`() {
        assertNull(DualScreenOutput.forCore("snes9x"))
    }
}
