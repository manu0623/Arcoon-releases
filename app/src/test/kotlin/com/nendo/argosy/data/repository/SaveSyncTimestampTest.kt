package com.nendo.argosy.data.repository

import com.nendo.argosy.data.repository.SaveSyncApiClient.Companion.isTimestampSaveName
import com.nendo.argosy.data.repository.SaveSyncApiClient.Companion.parseTimestamp
import com.nendo.argosy.data.repository.SaveSyncApiClient.Companion.parseTimestampOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class SaveSyncTimestampTest {

    // --- parseTimestamp ---

    @Test
    fun `parseTimestamp parses ISO instant format`() {
        val result = parseTimestamp("2025-01-15T12:00:00Z")
        assertEquals(Instant.parse("2025-01-15T12:00:00Z"), result)
    }

    @Test
    fun `parseTimestamp parses ISO with positive timezone offset`() {
        val result = parseTimestamp("2025-01-15T12:00:00+05:30")
        val expected = java.time.OffsetDateTime.parse("2025-01-15T12:00:00+05:30").toInstant()
        assertEquals(expected, result)
    }

    @Test
    fun `parseTimestamp parses ISO with negative timezone offset`() {
        val result = parseTimestamp("2025-01-15T12:00:00-08:00")
        val expected = java.time.OffsetDateTime.parse("2025-01-15T12:00:00-08:00").toInstant()
        assertEquals(expected, result)
    }

    @Test
    fun `parseTimestamp parses ZonedDateTime format`() {
        val result = parseTimestamp("2025-01-15T12:00:00+00:00[UTC]")
        assertEquals(Instant.parse("2025-01-15T12:00:00Z"), result)
    }

    @Test
    fun `parseTimestamp returns fallback for malformed timestamp`() {
        val before = Instant.now()
        val result = parseTimestamp("not-a-date")
        val after = Instant.now()
        assertTrue(
            "Fallback should be approximately now",
            !result.isBefore(before.minusSeconds(1)) && !result.isAfter(after.plusSeconds(1))
        )
    }

    @Test
    fun `parseTimestamp reads a timestamp without a zone as UTC`() {
        assertEquals(Instant.parse("2026-02-28T12:09:00Z"), parseTimestamp("2026-02-28T12:09:00"))
        assertEquals(Instant.parse("2026-02-28T12:09:00Z"), parseTimestamp("2026-02-28 12:09:00"))
    }

    @Test
    fun `parseTimestampOrNull keeps sub-millisecond precision and refuses garbage`() {
        assertEquals(
            Instant.parse("2026-02-28T12:09:00.123456Z"),
            parseTimestampOrNull("2026-02-28T12:09:00.123456+00:00")
        )
        assertNull(parseTimestampOrNull("not-a-date"))
    }

    // --- isTimestampSaveName (tested via TIMESTAMP_ONLY_PATTERN) ---

    @Test
    fun `isTimestampSaveName matches date-time with hyphens`() {
        assertTrue(isTimestampSaveName("2024-01-15-14-30-00"))
    }

    @Test
    fun `isTimestampSaveName matches date-time with underscores`() {
        assertTrue(isTimestampSaveName("2024-01-15_14_30_00"))
    }

    @Test
    fun `isTimestampSaveName rejects rom name`() {
        assertFalse(isTimestampSaveName("Pokemon Violet"))
    }

    @Test
    fun `isTimestampSaveName rejects timestamp with extra text`() {
        assertFalse(isTimestampSaveName("2024-01-15_14-30-00_backup"))
    }

}
