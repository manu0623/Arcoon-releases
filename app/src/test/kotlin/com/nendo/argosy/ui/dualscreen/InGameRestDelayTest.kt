package com.nendo.argosy.ui.dualscreen

import org.junit.Assert.assertEquals
import org.junit.Test

private const val DELAY_MS = 5_000L
private const val SESSION_START = 1_000_000L

class InGameRestDelayTest {

    @Test
    fun `a game just started waits the full delay`() {
        assertEquals(
            DELAY_MS,
            inGameRestDelayMs(sessionStartTimeMillis = SESSION_START, nowMs = SESSION_START, delayMs = DELAY_MS)
        )
    }

    @Test
    fun `time already played comes off the wait`() {
        assertEquals(
            DELAY_MS - 3_000L,
            inGameRestDelayMs(sessionStartTimeMillis = SESSION_START, nowMs = SESSION_START + 3_000L, delayMs = DELAY_MS)
        )
    }

    @Test
    fun `a game started past the delay settles without waiting`() {
        assertEquals(
            0L,
            inGameRestDelayMs(sessionStartTimeMillis = SESSION_START, nowMs = SESSION_START + DELAY_MS, delayMs = DELAY_MS)
        )
        assertEquals(
            0L,
            inGameRestDelayMs(sessionStartTimeMillis = SESSION_START, nowMs = SESSION_START + 600_000L, delayMs = DELAY_MS)
        )
    }

    @Test
    fun `no recorded session start falls back to the full delay`() {
        assertEquals(
            DELAY_MS,
            inGameRestDelayMs(sessionStartTimeMillis = 0L, nowMs = SESSION_START, delayMs = DELAY_MS)
        )
    }

    @Test
    fun `a session start ahead of the clock waits the full delay rather than settling`() {
        assertEquals(
            DELAY_MS,
            inGameRestDelayMs(sessionStartTimeMillis = SESSION_START + 2_000L, nowMs = SESSION_START, delayMs = DELAY_MS)
        )
    }

    @Test
    fun `the settled screen keeps half its brightness`() {
        assertEquals(1f, inGameRestFade(0f), 0.001f)
        assertEquals(REST_LOGO_ALPHA, inGameRestFade(1f), 0.001f)
        assertEquals(null, inGameRestLogoFilter(0f))
        assertEquals(false, inGameRestLogoFilter(1f) == null)
    }
}
