package com.nendo.argosy.ui.common

import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GameConfirmControllerTest {

    private fun TestScope.controller(deleteFound: Boolean = true) = GameConfirmController(
        buildDelete = { id -> if (deleteFound) GameConfirm.DeleteDownload(id, "Gundam", true, 2) else null },
        buildHide = { id -> GameConfirm.Hide(id, "Gundam") },
        scope = this
    )

    @Test
    fun `a delete waits for the player and passes the soundtrack choice`() = runTest {
        val confirm = controller()
        val ran = mutableListOf<Boolean>()
        confirm.requestDelete(7L) { ran += it }
        advanceUntilIdle()

        assertTrue(ran.isEmpty())
        assertEquals(GameConfirm.DeleteDownload(7L, "Gundam", true, 2), confirm.pending.value)

        confirm.confirm(withSoundtrack = true)
        assertEquals(listOf(true), ran)
        assertNull(confirm.pending.value)
    }

    @Test
    fun `dismissing runs nothing, and a later confirm stays inert`() = runTest {
        val confirm = controller()
        var hidden = 0
        confirm.requestHide(7L) { hidden++ }
        advanceUntilIdle()
        assertEquals(GameConfirm.Hide(7L, "Gundam"), confirm.pending.value)

        confirm.dismiss()
        confirm.confirm(withSoundtrack = false)
        assertEquals(0, hidden)
        assertNull(confirm.pending.value)
    }

    @Test
    fun `a game that cannot be described opens no dialog and runs nothing`() = runTest {
        val confirm = controller(deleteFound = false)
        var ran = false
        confirm.requestDelete(7L) { ran = true }
        advanceUntilIdle()
        confirm.confirm(withSoundtrack = false)

        assertNull(confirm.pending.value)
        assertEquals(false, ran)
    }
}
