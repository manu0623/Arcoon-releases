package com.nendo.argosy.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PresentationStyleTest {

    @Test
    fun `a style survives a round trip`() {
        val style = PresentationStyle(
            layout = PresentationLayout.JOURNAL,
            scrim = PresentationScrim.BLUR,
            scrimStrength = 40,
            art = PresentationArt.TITLE,
            hiddenStats = setOf(PresentationStat.PLAY_TIME, PresentationStat.ACHIEVEMENTS)
        )

        assertEquals(style, PresentationStyle.fromJson(style.toJson()))
    }

    @Test
    fun `nothing stored reads back as the shipped view`() {
        val style = PresentationStyle.fromJson(null)

        assertEquals(PresentationLayout.CINEMATIC, style.layout)
        assertEquals(PresentationScrim.GRADIENT, style.scrim)
        assertEquals(PresentationArt.COVER, style.art)
        assertFalse(style.shows(PresentationStat.DEVELOPER))
        PresentationStat.entries.filter { it != PresentationStat.DEVELOPER }.forEach {
            assertTrue(it.name, style.shows(it))
        }
    }

    @Test
    fun `an unknown stat or layout is dropped and the rest are kept`() {
        val stored = """{"hiddenStats":["GENRE","SOMETHING_NEWER"],"art":"BOX_3D","layout":"GALLERY"}"""

        val style = PresentationStyle.fromJson(stored)

        assertEquals(setOf(PresentationStat.GENRE), style.hiddenStats)
        assertEquals(PresentationArt.BOX_3D, style.art)
        assertEquals(PresentationLayout.CINEMATIC, style.layout)
        assertEquals(PresentationScrim.GRADIENT, style.scrim)
    }

    @Test
    fun `showing every stat stays that way instead of reverting to the defaults`() {
        val style = PresentationStyle.fromJson(PresentationStyle(hiddenStats = emptySet()).toJson())

        assertEquals(emptySet<PresentationStat>(), style.hiddenStats)
    }

    @Test
    fun `withStat hides and shows one stat without touching the others`() {
        val style = PresentationStyle(hiddenStats = setOf(PresentationStat.GENRE))

        val hidden = style.withStat(PresentationStat.PLAYERS, shown = false)
        assertEquals(setOf(PresentationStat.GENRE, PresentationStat.PLAYERS), hidden.hiddenStats)
        assertEquals(setOf(PresentationStat.PLAYERS), hidden.withStat(PresentationStat.GENRE, shown = true).hiddenStats)
    }

    @Test
    fun `a strength outside the range is clamped`() {
        assertEquals(PRESENTATION_SCRIM_MAX, PresentationStyle.fromJson("""{"scrimStrength":250}""").scrimStrength)
        assertEquals(PRESENTATION_SCRIM_MIN, PresentationStyle.fromJson("""{"scrimStrength":-5}""").scrimStrength)
    }
}
