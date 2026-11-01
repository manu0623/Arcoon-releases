package com.nendo.argosy.data.platform

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlatformTitleIdTest {

    @Test
    fun `every platform sigil reads a title id from has one, under any of its slugs`() {
        val sigilSlugs = listOf(
            "psp", "psx", "ps1", "playstation", "ps2", "psvita", "vita", "switch", "3ds", "wii",
            "wiiu", "gamecube", "ngc", "gc", "ps3", "xbox360", "dreamcast", "dc", "xbox",
            "gb", "gbc", "snes", "n64"
        )
        sigilSlugs.forEach { assertTrue(it, PlatformDefinitions.hasTitleId(it)) }
        assertTrue(PlatformDefinitions.hasTitleId("DC"))
    }

    @Test
    fun `a platform sigil does not read has none`() {
        listOf("nes", "genesis", "gba", "nds", "saturn", "arcade", "android").forEach {
            assertFalse(it, PlatformDefinitions.hasTitleId(it))
        }
    }
}
