package com.nendo.argosy.ui.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RevealHapticsTest {

    @Test
    fun `no animation means no beats`() {
        assertTrue(revealBeatDelays(0, 3).isEmpty())
        assertTrue(revealBeatDelays(-120, 3).isEmpty())
        assertTrue(revealBeatDelays(300, 0).isEmpty())
    }

    @Test
    fun `beats are spread across the animation from its first frame`() {
        assertEquals(listOf(0L, 93L, 186L), revealBeatDelays(280, 3))
        assertEquals(listOf(0L, 100L, 200L, 300L), revealBeatDelays(400, 4))
    }

    @Test
    fun `every beat lands inside the animation`() {
        listOf(120, 250, 280, 300, 460).forEach { duration ->
            listOf(1, 2, 3, 5).forEach { beats ->
                val delays = revealBeatDelays(duration, beats)
                assertEquals(beats, delays.size)
                assertEquals(0L, delays.first())
                assertTrue(duration.toString(), delays.last() < duration)
                assertTrue(delays.zipWithNext().all { (a, b) -> b > a })
            }
        }
    }

    @Test
    fun `a very short animation still gives each beat a distinct moment`() {
        assertEquals(listOf(0L, 1L, 2L), revealBeatDelays(3, 3))
        // more beats than milliseconds: each still gets its own moment, a millisecond apart
        assertEquals(listOf(0L, 1L, 2L, 3L, 4L), revealBeatDelays(2, 5))
    }
}
