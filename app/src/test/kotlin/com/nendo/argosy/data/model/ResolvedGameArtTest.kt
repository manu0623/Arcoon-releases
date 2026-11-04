package com.nendo.argosy.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ResolvedGameArtTest {

    @Test
    fun `embedded banner replaces remote hero while preserving the game logo`() {
        val art = ResolvedGameArt(
            backgroundPath = "server-hero",
            logoPath = "server-logo"
        )

        val result = art.withEmbedded3dsBanner("local-banner.png")

        assertEquals("local-banner.png", result.backgroundPath)
        assertEquals("server-logo", result.logoPath)
    }

    @Test
    fun `manual background override remains authoritative`() {
        val art = ResolvedGameArt(
            backgroundPath = "manual-hero",
            overriddenSlots = setOf(ArtSlot.BACKGROUND)
        )

        assertEquals(art, art.withEmbedded3dsBanner("local-banner.png"))
    }

    @Test
    fun `missing embedded banner keeps the existing hero`() {
        val art = ResolvedGameArt(backgroundPath = "server-hero")

        assertEquals(art, art.withEmbedded3dsBanner(null))
    }
}
