package com.nendo.argosy.ui.screens.gamedetail

import com.nendo.argosy.ui.screens.gamedetail.components.paginateText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentPaginationTest {

    @Test
    fun `a short document is one page`() {
        assertEquals(1, paginateText("one\ntwo\nthree", linesPerPage = 10).size)
    }

    @Test
    fun `a document splits at the line count`() {
        val body = (1..25).joinToString("\n") { "line $it" }

        val pages = paginateText(body, linesPerPage = 10)

        assertEquals(3, pages.size)
        assertEquals(10, pages[0].lines().size)
        assertEquals(5, pages[2].lines().size)
    }

    @Test
    fun `every line survives the split`() {
        val body = (1..97).joinToString("\n") { "line $it" }

        val rejoined = paginateText(body, linesPerPage = 8).joinToString("\n")

        assertEquals(body, rejoined)
    }

    @Test
    fun `an empty document still has a page to show`() {
        val pages = paginateText("", linesPerPage = 10)

        assertEquals(1, pages.size)
        assertTrue(pages.single().isEmpty())
    }

    @Test
    fun `fixed-width art keeps its leading spaces`() {
        val body = "    ---- TITLE ----\n      by someone"

        val pages = paginateText(body, linesPerPage = 10)

        assertTrue(pages.single().startsWith("    ----"))
    }
}
