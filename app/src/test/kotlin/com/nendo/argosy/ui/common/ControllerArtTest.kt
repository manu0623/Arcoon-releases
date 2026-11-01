package com.nendo.argosy.ui.common

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ControllerArtTest {

    private val table = File("src/main/assets/controllers/sdl_controller_list.h")
        .useLines { ControllerArt.parseSdlTable(it) }

    private fun kind(vendorId: Int, productId: Int, name: String? = null) =
        ControllerArt.externalKind(vendorId, productId, name, table)

    @Test
    fun `the vendored SDL table parses and skips commented rows`() {
        assertTrue(table.size > 500)
        assertEquals(null, table[(0x057e shl 16) or 0x2017])
    }

    @Test
    fun `playstation pads resolve by product id to the symmetric pad`() {
        assertEquals(ControllerKind.SYMMETRIC, kind(0x054c, 0x0268))
        assertEquals(ControllerKind.SYMMETRIC, kind(0x054c, 0x09cc))
        assertEquals(ControllerKind.SYMMETRIC, kind(0x054c, 0x0ce6))
    }

    @Test
    fun `xbox and switch pro pads are the modern pad`() {
        assertEquals(ControllerKind.MODERN, kind(0x045e, 0x028e))
        assertEquals(ControllerKind.MODERN, kind(0x045e, 0x0b12))
        assertEquals(ControllerKind.MODERN, kind(0x057e, 0x2009, "Pro Controller"))
    }

    @Test
    fun `joy-cons and nintendo retro pads resolve by product id`() {
        assertEquals(ControllerKind.JOYCON, kind(0x057e, 0x2006))
        assertEquals(ControllerKind.JOYCON, kind(0x057e, 0x2066))
        assertEquals(ControllerKind.RETRO, kind(0x057e, 0x2017))
        assertEquals(ControllerKind.RETRO, kind(0x057e, 0x201e))
    }

    @Test
    fun `an unknown pad resolves by whole words in its name, then falls back`() {
        assertEquals(ControllerKind.RETRO, kind(0x2dc8, 0x9999, "8BitDo SN30 Pro"))
        assertEquals(ControllerKind.RETRO, kind(0x2dc8, 0x9998, "8BitDo M30 gamepad"))
        assertEquals(ControllerKind.MODERN, kind(0x1234, 0x5678, "Generic Gamepad Wireless"))
        assertEquals(ControllerKind.MODERN, kind(0x1234, 0x5678, null))
    }

    @Test
    fun `a built-in pad shows the dual-screen handheld only with two built-in screens`() {
        assertEquals(ControllerKind.HANDHELD, ControllerArt.handheldKind(builtInScreens = 1))
        assertEquals(ControllerKind.DUAL_SCREEN, ControllerArt.handheldKind(builtInScreens = 2))
    }
}
