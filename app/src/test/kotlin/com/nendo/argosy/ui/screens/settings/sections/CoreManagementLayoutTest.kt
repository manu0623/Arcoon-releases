package com.nendo.argosy.ui.screens.settings.sections

import com.nendo.argosy.ui.screens.settings.CoreBulkUpdateProgress
import com.nendo.argosy.ui.screens.settings.CoreChipState
import com.nendo.argosy.ui.screens.settings.CoreManagementState
import com.nendo.argosy.ui.screens.settings.PlatformCoreRow
import java.time.ZoneOffset
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreManagementLayoutTest {

    private val platforms = listOf(platform("gba"), platform("snes"))

    @Test
    fun `platforms start at focus zero with no updates pending`() {
        val state = CoreManagementState(platforms = platforms)
        assertEquals("gba", (coreManagementItemAtFocusIndex(0, state) as CoreManagementItem.Platform).row.platformSlug)
        assertEquals(1, coreManagementMaxFocusIndex(state))
    }

    @Test
    fun `update all takes focus zero and shifts every platform down one`() {
        val state = CoreManagementState(platforms = platforms, pendingUpdateCount = 2)
        assertEquals(CoreManagementItem.UpdateAll, coreManagementItemAtFocusIndex(0, state))
        assertEquals("gba", (coreManagementItemAtFocusIndex(1, state) as CoreManagementItem.Platform).row.platformSlug)
        assertEquals(2, coreManagementMaxFocusIndex(state))
        assertEquals(2, createCoreManagementLayoutInfo(state).focusIndexOfKey("snes"))
    }

    @Test
    fun `update all stays while a run is in progress after the count reaches zero`() {
        val state = CoreManagementState(
            platforms = platforms,
            bulkUpdate = CoreBulkUpdateProgress(total = 1)
        )
        assertEquals(CoreManagementItem.UpdateAll, coreManagementItemAtFocusIndex(0, state))
    }

    @Test
    fun `update all is reachable with no platforms enabled`() {
        val state = CoreManagementState(pendingUpdateCount = 1)
        assertEquals(CoreManagementItem.UpdateAll, coreManagementItemAtFocusIndex(0, state))
        assertNull(coreManagementFocusedPlatform(state))
    }

    @Test
    fun `an http date renders as a localized date`() {
        val raw = "Sun, 11 Oct 2026 03:09:01 GMT"
        val formatted = formatCoreBuildStamp(raw, ZoneOffset.UTC, Locale.US)
        assertNotEquals(raw, formatted)
        assertTrue(formatted, formatted.contains("2026"))
    }

    @Test
    fun `a value that is not an http date is shown as is`() {
        assertEquals("1048576", formatCoreBuildStamp("1048576", ZoneOffset.UTC, Locale.US))
    }

    private fun platform(slug: String) = PlatformCoreRow(
        platformId = slug.hashCode().toLong(),
        platformSlug = slug,
        platformName = slug,
        cores = listOf(CoreChipState(coreId = "$slug-core", displayName = slug, isInstalled = true, isActive = true))
    )
}
