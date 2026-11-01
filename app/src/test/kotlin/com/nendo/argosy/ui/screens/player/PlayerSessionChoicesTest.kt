package com.nendo.argosy.ui.screens.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlayerSessionChoicesTest {

    private fun track(index: Int, language: String?, label: String, text: Boolean = true) =
        PlayerTrack(streamIndex = index, ordinal = index, label = label, language = language, isTextSubtitle = text)

    @Test
    fun `the same track is found in the next episode under a different stream index`() {
        val choice = PlayerTrackChoice("jpn", "Japanese - AAC 2.0")
        val next = listOf(track(1, "eng", "English - AAC 2.0"), track(2, "jpn", "Japanese - AAC 2.0"))

        assertEquals(2, choice.matchIn(next)?.streamIndex)
    }

    @Test
    fun `a relabelled track still matches on its language`() {
        val choice = PlayerTrackChoice("jpn", "Japanese - AAC 2.0")
        val next = listOf(track(4, "eng", "English"), track(5, "jpn", "Japanese - FLAC 5.1"))

        assertEquals(5, choice.matchIn(next)?.streamIndex)
    }

    @Test
    fun `a text subtitle is preferred over a picture one in the same language`() {
        val choice = PlayerTrackChoice("eng", "English Full", isTextSubtitle = true)
        val next = listOf(track(3, "eng", "English PGS", text = false), track(4, "eng", "English SRT"))

        assertEquals(4, choice.matchIn(next)?.streamIndex)
    }

    @Test
    fun `no track in the chosen language means no match`() {
        val choice = PlayerTrackChoice("fre", "French")

        assertNull(choice.matchIn(listOf(track(1, "eng", "English"))))
    }

    @Test
    fun `an untouched session carries nothing`() {
        assertEquals(true, PlayerSessionChoices().isEmpty)
        assertNull(PlayerSessionChoices().matchAudio(listOf(track(1, "eng", "English"))))
    }
}
