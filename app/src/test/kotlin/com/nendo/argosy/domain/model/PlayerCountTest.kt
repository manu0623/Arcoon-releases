package com.nendo.argosy.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The one parser behind the game-detail glyph and both Players filters. RomM's player_count is
 * free text, so the shapes here are the ones a library actually holds, not a grammar.
 */
class PlayerCountTest {

    @Test
    fun `a plain number is its own maximum and bounded`() {
        assertEquals(PlayerCount(max = 1, unbounded = false), PlayerCount.parse("1"))
        assertEquals(PlayerCount(max = 12, unbounded = false), PlayerCount.parse("12"))
    }

    @Test
    fun `a range takes its larger end`() {
        assertEquals(PlayerCount(max = 2, unbounded = false), PlayerCount.parse("1-2"))
        assertEquals(PlayerCount(max = 4, unbounded = false), PlayerCount.parse("2-4"))
        assertEquals(PlayerCount(max = 8, unbounded = false), PlayerCount.parse("1 to 8 players"))
    }

    @Test
    fun `a trailing plus is unbounded from the last number`() {
        assertEquals(PlayerCount(max = 4, unbounded = true), PlayerCount.parse("4+"))
        assertEquals(PlayerCount(max = 4, unbounded = true), PlayerCount.parse("1-4+"))
        assertEquals(PlayerCount(max = 2, unbounded = true), PlayerCount.parse("2+ players"))
    }

    @Test
    fun `a plus before the number does not make it unbounded`() {
        assertEquals(PlayerCount(max = 4, unbounded = false), PlayerCount.parse("+4"))
    }

    @Test
    fun `words with no digits parse to nothing`() {
        assertNull(PlayerCount.parse("Single player"))
        assertNull(PlayerCount.parse("Multiplayer"))
        assertNull(PlayerCount.parse("+"))
    }

    @Test
    fun `empty and null parse to nothing`() {
        assertNull(PlayerCount.parse(""))
        assertNull(PlayerCount.parse("   "))
        assertNull(PlayerCount.parse(null))
    }

    @Test
    fun `each bucket admits a maximum at or above its minimum and rejects one below`() {
        PlayerCountBucket.entries.forEach { bucket ->
            val below = PlayerCount(max = bucket.minPlayers - 1, unbounded = false)
            val equal = PlayerCount(max = bucket.minPlayers, unbounded = false)
            val above = PlayerCount(max = bucket.minPlayers + 1, unbounded = false)
            assertFalse("${bucket.name} below", bucket.admits(below))
            assertTrue("${bucket.name} equal", bucket.admits(equal))
            assertTrue("${bucket.name} above", bucket.admits(above))
        }
    }

    @Test
    fun `an unbounded count passes every bucket whatever its number`() {
        val twoOrMore = PlayerCount(max = 2, unbounded = true)
        PlayerCountBucket.entries.forEach { bucket ->
            assertTrue(bucket.name, bucket.admits(twoOrMore))
        }
    }

    @Test
    fun `an unknown count is hidden by every bucket`() {
        PlayerCountBucket.entries.forEach { bucket ->
            assertFalse(bucket.name, bucket.admits(null))
        }
    }

    @Test
    fun `the four buckets read one two three and four plus in that order`() {
        assertEquals(listOf(1, 2, 3, 4), PlayerCountBucket.entries.map { it.minPlayers })
    }

    @Test
    fun `a bucket round-trips through its stored name and an unknown name is nothing`() {
        PlayerCountBucket.entries.forEach { bucket ->
            assertEquals(bucket, PlayerCountBucket.fromName(bucket.name))
        }
        assertNull(PlayerCountBucket.fromName("FIVE"))
        assertNull(PlayerCountBucket.fromName(null))
    }
}
