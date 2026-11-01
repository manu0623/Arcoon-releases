package com.nendo.argosy.data.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class PlayRecordCutoffsTest {

    private val cutoff = Instant.parse("2026-10-20T00:00:00Z")
    private val cutoffs = PlayRecordCutoffs(mapOf(7L to cutoff))

    @Test
    fun `a session that started before the cutoff is blocked`() {
        assertTrue(cutoffs.blocks(7L, cutoff.minusMillis(1)))
    }

    @Test
    fun `a session that started at the cutoff is not blocked`() {
        assertFalse(cutoffs.blocks(7L, cutoff))
    }

    @Test
    fun `a session that started after the cutoff is not blocked`() {
        assertFalse(cutoffs.blocks(7L, cutoff.plusMillis(1)))
    }

    @Test
    fun `another game's session is never blocked`() {
        assertFalse(cutoffs.blocks(8L, cutoff.minusMillis(1)))
    }

    @Test
    fun `no cutoffs block nothing`() {
        assertFalse(PlayRecordCutoffs.NONE.blocks(7L, Instant.EPOCH))
    }
}
