package com.nendo.argosy.data.local.entity

import com.nendo.argosy.data.model.GameSource
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameEntityServerRemovedTest {

    @Test
    fun `a RomM row with a negative id is removed from RomM`() {
        assertTrue(isServerRemovedRomm(GameSource.ROMM_REMOTE, -9L))
        assertTrue(isServerRemovedRomm(GameSource.ROMM_SYNCED, -9L))
    }

    @Test
    fun `a RomM row with a live or missing id is not removed from RomM`() {
        assertFalse(isServerRemovedRomm(GameSource.ROMM_REMOTE, 42L))
        assertFalse(isServerRemovedRomm(GameSource.ROMM_SYNCED, null))
    }

    @Test
    fun `a negative id under any other source is never removed from RomM`() {
        GameSource.entries
            .filterNot { it == GameSource.ROMM_REMOTE || it == GameSource.ROMM_SYNCED }
            .forEach { assertFalse(it.name, isServerRemovedRomm(it, -9L)) }
    }
}
