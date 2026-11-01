package com.nendo.argosy.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The library admits a game by the highest number its player text carries, so a two-player game
 * written "1-2" reaches the two-player bucket. A tile counting the same games by the text's
 * leading number instead reports a tally the library it opens contradicts.
 */
class LibraryLinkPlayerMatchTest {

    @Test
    fun `a range reaches the bucket its highest number reaches`() {
        val twoPlayers = PlayerCount.parse("1-2")

        assertEquals(2, twoPlayers?.max)
        assertTrue(PlayerCountBucket.TWO.admits(twoPlayers))
        assertTrue(PlayerCountBucket.ONE.admits(twoPlayers))
    }

    @Test
    fun `a range does not reach past its highest number`() {
        assertTrue(!PlayerCountBucket.THREE.admits(PlayerCount.parse("1-2")))
        assertTrue(!PlayerCountBucket.FOUR_PLUS.admits(PlayerCount.parse("1-2")))
    }

    @Test
    fun `an unbounded count reaches every bucket`() {
        val unbounded = PlayerCount.parse("2+")

        assertTrue(PlayerCountBucket.FOUR_PLUS.admits(unbounded))
    }

    @Test
    fun `a game with no parsable count is admitted by no bucket`() {
        PlayerCountBucket.entries.forEach { bucket ->
            assertTrue(!bucket.admits(PlayerCount.parse(null)))
            assertTrue(!bucket.admits(PlayerCount.parse("single player")))
        }
    }
}
