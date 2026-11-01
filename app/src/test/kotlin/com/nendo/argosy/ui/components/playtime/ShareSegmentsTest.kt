package com.nendo.argosy.ui.components.playtime

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShareSegmentsTest {

    private val series = listOf(Color.Red, Color.Green, Color.Blue, Color.Yellow)
    private val others = Color.Gray

    private fun build(entries: List<ShareEntry>, limit: Int) = buildShareSegments(
        entries = entries,
        limit = limit,
        series = series,
        othersLabel = "Others",
        othersColor = others,
        valueLabel = { ms -> "${ms}ms" },
        shareLabel = { percent -> "$percent%" }
    )

    private val month = listOf(
        ShareEntry("A", 500L, badge = "New"),
        ShareEntry("B", 200L),
        ShareEntry("C", 150L, badge = "New"),
        ShareEntry("D", 100L),
        ShareEntry("E", 50L)
    )

    @Test
    fun `the top three take series colours and the rest fold into others`() {
        val segments = build(month, limit = 3)

        assertEquals(listOf("A", "B", "C", "Others"), segments.map { it.label })
        assertEquals(listOf(Color.Red, Color.Green, Color.Blue, others), segments.map { it.color })
        assertEquals(150L, segments.last().valueMs)
        assertEquals("150ms", segments.last().valueLabel)
    }

    @Test
    fun `shares are of the whole month, others included`() {
        val segments = build(month, limit = 3)

        assertEquals(listOf("50%", "20%", "15%", "15%"), segments.map { it.shareLabel })
    }

    @Test
    fun `new badges ride on their games and never on others`() {
        val segments = build(month, limit = 3)

        assertEquals(listOf("New", null, "New", null), segments.map { it.badge })
    }

    @Test
    fun `a short screen shows the top two and folds the rest`() {
        val segments = build(month, limit = 2)

        assertEquals(listOf("A", "B", "Others"), segments.map { it.label })
        assertEquals(300L, segments.last().valueMs)
    }

    @Test
    fun `no others segment when every game is named`() {
        val segments = build(month.take(3), limit = 3)

        assertEquals(listOf("A", "B", "C"), segments.map { it.label })
    }

    @Test
    fun `an empty month has no segments`() {
        assertEquals(emptyList<ShareSegment>(), build(emptyList(), limit = 3))
    }

    @Test
    fun `platform glyphs pass through on named segments only`() {
        val segments = build(listOf(ShareEntry("SNES", 10L, platformSlug = "snes"), ShareEntry("GB", 5L)), limit = 1)

        assertEquals("snes", segments.first().platformSlug)
        assertNull(segments.last().platformSlug)
    }
}
