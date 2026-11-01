package com.nendo.argosy.ui.screens.gamedetail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MoreOptionsServerRemovedTest {

    @Test
    fun `a removable game offers remove from library just before hide`() {
        val options = buildMoreOptions(
            MoreOptionsContext(isRommGame = true, canRemoveFromLibrary = true, platformSlug = "saturn")
        )

        assertTrue(options.contains(MoreOptionAction.RemoveFromLibrary))
        assertEquals(
            options.indexOf(MoreOptionAction.ToggleHide) - 1,
            options.indexOf(MoreOptionAction.RemoveFromLibrary)
        )
    }
}
