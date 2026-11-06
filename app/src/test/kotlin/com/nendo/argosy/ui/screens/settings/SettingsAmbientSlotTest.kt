package com.nendo.argosy.ui.screens.settings

import com.nendo.argosy.ui.dualscreen.PresentationSlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Entering Settings must never leave the lower screen with nothing published: the presentation
 * fallback is Cocoon's near-black wallpaper, so an unpublished section reads as a black screen.
 */
class SettingsAmbientSlotTest {

    @Test
    fun `publishes a slot before any cover is known`() {
        val slot: PresentationSlot = settingsAmbientSlot(name = "SYNC SETTINGS", coverPaths = emptyList())

        assertTrue("a section with no covers still publishes a showcase", slot is PresentationSlot.PlatformShowcase)
        assertEquals("SYNC SETTINGS", (slot as PresentationSlot.PlatformShowcase).name)
        assertTrue(slot.coverPaths.isEmpty())
    }

    @Test
    fun `keeps the section name while the collage fills in`() {
        val slot = settingsAmbientSlot(name = "PLAY TIME", coverPaths = listOf("/a/cover.png", "/b/cover.png"))

        assertEquals("PLAY TIME", slot.name)
        assertEquals(listOf("/a/cover.png", "/b/cover.png"), slot.coverPaths)
        assertTrue(slot.facts.isEmpty())
    }

    @Test
    fun `a platform section shows the platform name`() {
        val slot = settingsAmbientSlot(name = "NINTENDO 3DS", coverPaths = emptyList())

        assertEquals("NINTENDO 3DS", slot.name)
    }
}
