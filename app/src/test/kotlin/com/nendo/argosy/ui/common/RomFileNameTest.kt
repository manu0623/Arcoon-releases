package com.nendo.argosy.ui.common

import org.junit.Assert.assertEquals
import org.junit.Test

class RomFileNameTest {

    @Test
    fun `region and revision are lifted out of the title`() {
        val parts = parseRomFileName("Donkey Kong Country 2 - Diddy's Kong Quest (USA) (Rev 1).sfc")

        assertEquals("Donkey Kong Country 2 - Diddy's Kong Quest", parts.title)
        assertEquals(listOf("USA", "Rev 1"), parts.tags)
    }

    @Test
    fun `two variants of one game differ only in their tags`() {
        val europe = parseRomFileName("Donkey Kong Country 2 - Diddy's Kong Quest (Europe).sfc")
        val usa = parseRomFileName("Donkey Kong Country 2 - Diddy's Kong Quest (USA) (Rev 1).sfc")

        assertEquals(europe.title, usa.title)
        assertEquals(listOf("Europe"), europe.tags)
        assertEquals(listOf("USA", "Rev 1"), usa.tags)
    }

    @Test
    fun `a comma inside one group becomes separate tags`() {
        val parts = parseRomFileName("Excitebike (Japan, USA).nes")

        assertEquals("Excitebike", parts.title)
        assertEquals(listOf("Japan", "USA"), parts.tags)
    }

    @Test
    fun `square bracket tags are lifted too`() {
        val parts = parseRomFileName("Treasure Island Dizzy (World) [Unl].nes")

        assertEquals("Treasure Island Dizzy", parts.title)
        assertEquals(listOf("World", "Unl"), parts.tags)
    }

    @Test
    fun `a name with no tags keeps its whole title`() {
        val parts = parseRomFileName("Super Mario Bros..nes")

        assertEquals("Super Mario Bros.", parts.title)
        assertEquals(emptyList<String>(), parts.tags)
    }

    @Test
    fun `a name that is only tags keeps something to render`() {
        val parts = parseRomFileName("(USA).sfc")

        assertEquals("(USA)", parts.title)
        assertEquals(listOf("USA"), parts.tags)
    }

    @Test
    fun `a folder name with no extension is left whole`() {
        val parts = parseRomFileName("Airwolf (USA) (Acclaim)")

        assertEquals("Airwolf", parts.title)
        assertEquals(listOf("USA", "Acclaim"), parts.tags)
    }
}
