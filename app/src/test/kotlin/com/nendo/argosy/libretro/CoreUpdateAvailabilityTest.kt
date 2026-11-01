package com.nendo.argosy.libretro

import com.nendo.argosy.data.local.entity.CoreVersionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreUpdateAvailabilityTest {

    private val installed = "Mon, 05 Oct 2026 03:12:44 GMT"
    private val latest = "Sun, 11 Oct 2026 03:09:01 GMT"

    @Test
    fun `a newer build with no rollback on record is an update`() {
        assertTrue(LibretroCoreManager.isUpdateAvailable(installed, latest, blockedVersion = null))
    }

    @Test
    fun `the build the user rolled back from is never offered again`() {
        assertFalse(LibretroCoreManager.isUpdateAvailable(installed, latest, blockedVersion = latest))
    }

    @Test
    fun `a rollback from an older build does not hide a newer one`() {
        val olderBlocked = "Thu, 01 Oct 2026 03:00:00 GMT"
        assertTrue(LibretroCoreManager.isUpdateAvailable(installed, latest, blockedVersion = olderBlocked))
    }

    @Test
    fun `the installed build is not an update over itself`() {
        assertFalse(LibretroCoreManager.isUpdateAvailable(installed, installed, blockedVersion = null))
        assertFalse(LibretroCoreManager.isUpdateAvailable(installed, installed, blockedVersion = installed))
    }

    @Test
    fun `an unknown installed or latest build is not an update`() {
        assertFalse(LibretroCoreManager.isUpdateAvailable(null, latest, blockedVersion = null))
        assertFalse(LibretroCoreManager.isUpdateAvailable(installed, null, blockedVersion = null))
    }

    @Test
    fun `a pending row carries both builds`() {
        val pending = LibretroCoreManager.pendingUpdateOf(row(blockedVersion = null))
        assertEquals("mgba", pending?.coreInfo?.coreId)
        assertEquals(installed, pending?.installedVersion)
        assertEquals(latest, pending?.latestVersion)
    }

    @Test
    fun `a stale row flagged for the blocked build is not pending`() {
        assertNull(LibretroCoreManager.pendingUpdateOf(row(blockedVersion = latest)))
    }

    @Test
    fun `a row not flagged for update is not pending`() {
        assertNull(LibretroCoreManager.pendingUpdateOf(row(blockedVersion = null, updateAvailable = false)))
    }

    @Test
    fun `a row for a core outside the registry is not pending`() {
        assertNull(LibretroCoreManager.pendingUpdateOf(row(blockedVersion = null, coreId = "not_a_core")))
    }

    private fun row(
        blockedVersion: String?,
        updateAvailable: Boolean = true,
        coreId: String = "mgba"
    ) = CoreVersionEntity(
        coreId = coreId,
        installedVersion = installed,
        latestVersion = latest,
        updateAvailable = updateAvailable,
        blockedVersion = blockedVersion
    )
}
