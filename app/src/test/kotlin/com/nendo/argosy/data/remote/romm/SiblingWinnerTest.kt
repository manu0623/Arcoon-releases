package com.nendo.argosy.data.remote.romm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SiblingWinnerTest {

    private fun namedRom(
        id: Long,
        name: String,
        vararg siblings: RomMSibling,
        tags: List<String>? = null
    ) = RomMRom(
        id = id,
        name = name,
        fileName = "rom$id.gb",
        filePath = "/roms/gb/rom$id.gb",
        platformId = 5L,
        platformSlug = "gb",
        slug = "rom$id",
        igdbId = null,
        mobyId = null,
        summary = null,
        coverSmall = null,
        coverLarge = null,
        regions = listOf("Germany"),
        languages = null,
        revision = null,
        tags = tags,
        siblingRoms = siblings.toList()
    )

    private fun sibling(id: Long, name: String?, fileNameNoExt: String = "rom$id") = RomMSibling(
        id = id,
        name = name,
        fileNameNoTags = "rom$id",
        fileNameNoExt = fileNameNoExt
    )

    /**
     * The server's sibling list is the union over every metadata source, and Moby files
     * Pokemon Red and Blue under one entry, so the USA dumps of both list each other.
     * A sibling naming a different game is not a sibling of this one.
     */
    @Test
    fun `a sibling naming a different game stays out of the group`() {
        val blue = namedRom(
            3638, "Pokémon Blue Version",
            sibling(3636, "Pokémon Blue Version"),
            sibling(3644, "Pokémon Red Version")
        )

        assertEquals(listOf(3636L), blue.sameGameSiblings.map { it.id })
        assertTrue(blue.hasNonDiscSiblings)
    }

    @Test
    fun `a rom whose only sibling is a different game has no same-game siblings`() {
        val homebrew = namedRom(
            2989, "Into the Blue",
            sibling(3636, "Pokémon Blue Version")
        )

        assertFalse(homebrew.hasNonDiscSiblings)
    }

    @Test
    fun `a sibling without a name is kept in the group`() {
        val rom = namedRom(
            3645, "Pokémon Red Version",
            sibling(3644, null)
        )

        assertEquals(listOf(3644L), rom.sameGameSiblings.map { it.id })
    }

    /**
     * The disc paths delete redundant individual-disc games, so a cross-game sibling that
     * happens to carry a disc tag must not pull another game into a multi-disc group.
     */
    @Test
    fun `a disc tagged sibling of a different game does not make a multi-disc group`() {
        val disc = namedRom(
            10, "Some RPG",
            sibling(11, "Another RPG", fileNameNoExt = "Another RPG (Disc 2)"),
            tags = listOf("Disc 1")
        )
        val sameGame = namedRom(
            10, "Some RPG",
            sibling(11, "Some RPG", fileNameNoExt = "Some RPG (Disc 2)"),
            tags = listOf("Disc 1")
        )

        assertFalse(disc.hasDiscSiblings)
        assertTrue(sameGame.hasDiscSiblings)
    }
}
