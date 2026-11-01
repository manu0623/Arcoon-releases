package com.nendo.argosy.data.repository

import com.nendo.argosy.data.remote.romm.RomMSave
import org.junit.Assert.assertEquals
import org.junit.Test

class SelectLatestServerSaveTest {

    private fun save(id: Long, fileName: String, updatedAt: String, slot: String? = null) = RomMSave(
        id = id,
        romId = 1L,
        userId = 1L,
        emulator = null,
        fileName = fileName,
        updatedAt = updatedAt,
        slot = slot
    )

    private val older = save(1, "Game [2026-01-01_10-00-00].srm", "2026-01-01T10:00:00+00:00")
    private val newer = save(2, "Game [2026-02-01_10-00-00].srm", "2026-02-01T10:00:00+00:00")

    @Test
    fun `the latest channel picks the newest rom-named save whichever order the server lists them`() {
        assertEquals(2L, SaveSyncApiClient.selectLatestServerSave(listOf(older, newer), null, "Game", false)?.id)
        assertEquals(2L, SaveSyncApiClient.selectLatestServerSave(listOf(newer, older), null, "Game", false)?.id)
    }

    @Test
    fun `a named channel picks the newest save filed under its slot`() {
        val slotOld = save(3, "a.srm", "2026-01-01T10:00:00+00:00", slot = "boss")
        val slotNew = save(4, "b.srm", "2026-03-01T10:00:00+00:00", slot = "boss")
        assertEquals(4L, SaveSyncApiClient.selectLatestServerSave(listOf(slotNew, slotOld, newer), "boss", "Game", false)?.id)
        assertEquals(4L, SaveSyncApiClient.selectLatestServerSave(listOf(slotOld, slotNew, newer), "boss", "Game", false)?.id)
    }

    @Test
    fun `a gci bundle prefers the newest zip over a newer loose file`() {
        val zip = save(5, "Game [2026-01-01_10-00-00].zip", "2026-01-01T10:00:00+00:00")
        val gci = save(6, "Game [2026-04-01_10-00-00].gci", "2026-04-01T10:00:00+00:00")
        assertEquals(5L, SaveSyncApiClient.selectLatestServerSave(listOf(gci, zip), null, "Game", true)?.id)
    }
}
