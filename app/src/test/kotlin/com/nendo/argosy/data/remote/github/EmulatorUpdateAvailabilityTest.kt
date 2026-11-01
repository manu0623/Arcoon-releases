package com.nendo.argosy.data.remote.github

import com.nendo.argosy.data.remote.github.EmulatorUpdateRepository.Companion.isUpdateAvailable
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class EmulatorUpdateAvailabilityTest {

    @Test
    fun `an unresolved build installed after the release is current`() {
        assertFalse(hashUpdate(installedAt = PUBLISHED.plusSeconds(60)))
    }

    @Test
    fun `an unresolved build installed before the release is outdated`() {
        assertTrue(hashUpdate(installedAt = PUBLISHED.minusSeconds(60)))
    }

    @Test
    fun `an unresolved build installed the moment the release published is current`() {
        assertFalse(hashUpdate(installedAt = PUBLISHED))
    }

    @Test
    fun `an unresolved build with no install time is outdated`() {
        assertTrue(hashUpdate(installedAt = null))
    }

    @Test
    fun `an unresolved build against a release with no publish date is outdated`() {
        assertTrue(
            isUpdateAvailable(HASH, "0.2.1", PUBLISHED.plusSeconds(60), releasePublishedAt = null)
        )
    }

    @Test
    fun `semver versions compare by version whatever the install time`() {
        assertTrue(isUpdateAvailable("0.2.0", "0.2.1", PUBLISHED.plusSeconds(60), PUBLISHED_TEXT))
        assertFalse(isUpdateAvailable("0.2.1", "0.2.1", PUBLISHED.minusSeconds(60), PUBLISHED_TEXT))
    }

    @Test
    fun `nothing installed is never an update`() {
        assertFalse(isUpdateAvailable(null, "0.2.1", null, PUBLISHED_TEXT))
    }

    private fun hashUpdate(installedAt: Instant?) =
        isUpdateAvailable(HASH, "0.2.1", installedAt, PUBLISHED_TEXT)

    private companion object {
        const val HASH = "1f6734c"
        const val PUBLISHED_TEXT = "2026-10-12T18:30:00+02:00"
        val PUBLISHED: Instant = Instant.parse("2026-10-12T16:30:00Z")
    }
}
