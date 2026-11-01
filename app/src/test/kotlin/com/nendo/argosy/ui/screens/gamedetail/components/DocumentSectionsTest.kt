package com.nendo.argosy.ui.screens.gamedetail.components

import com.nendo.argosy.data.repository.DocumentHighlight
import com.nendo.argosy.data.repository.decodeHighlights
import com.nendo.argosy.data.repository.encodeHighlights
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DocumentSectionsTest {

    private val doc = listOf(
        "Title",
        "=====",
        "First paragraph line one",
        "first paragraph line two",
        "",
        "Second paragraph",
        "--------------",
        "After the rule"
    )

    @Test
    fun `a touched line expands to its paragraph`() {
        assertEquals(2..3, sectionAt(doc, 3))
    }

    @Test
    fun `divider rules bound a section`() {
        assertEquals(5..5, sectionAt(doc, 5))
        assertEquals(7..7, sectionAt(doc, 7))
    }

    @Test
    fun `a blank line resolves to the section below it`() {
        assertEquals(5..5, sectionAt(doc, 4))
    }

    @Test
    fun `a divider line is not a section`() {
        assertNull(sectionAt(doc, 1))
    }

    @Test
    fun `touching a highlighted line removes that highlight`() {
        assertEquals(
            emptyList<DocumentHighlight>(),
            toggleHighlight(listOf(DocumentHighlight(2..3)), 2..3, 3)
        )
    }

    @Test
    fun `touching an unhighlighted line adds its section in order`() {
        assertEquals(
            listOf(DocumentHighlight(2..3), DocumentHighlight(5..5, 2)),
            toggleHighlight(listOf(DocumentHighlight(5..5, 2)), 2..3, 2)
        )
    }

    @Test
    fun `tapping a bookmark cycles only its colour and wraps`() {
        val highlights = listOf(DocumentHighlight(2..3, 4), DocumentHighlight(5..5, 1))
        assertEquals(
            listOf(DocumentHighlight(2..3, 0), DocumentHighlight(5..5, 1)),
            cycleHighlightColor(highlights, 2, 5)
        )
    }

    @Test
    fun `highlights survive a round trip through storage`() {
        val highlights = listOf(DocumentHighlight(5..9, 3), DocumentHighlight(1..2))
        assertEquals(
            listOf(DocumentHighlight(1..2), DocumentHighlight(5..9, 3)),
            decodeHighlights(encodeHighlights(highlights))
        )
    }

    @Test
    fun `highlights saved without a colour load with the first colour`() {
        assertEquals(listOf(DocumentHighlight(4..6, 0)), decodeHighlights("4 6"))
    }
}
