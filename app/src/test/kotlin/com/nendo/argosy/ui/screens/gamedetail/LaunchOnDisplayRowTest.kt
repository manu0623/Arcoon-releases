package com.nendo.argosy.ui.screens.gamedetail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LaunchOnDisplayRowTest {

    @Test
    fun `a downloaded game with several screens can be sent to one of them`() {
        val options = buildMoreOptions(
            MoreOptionsContext(isDownloaded = true, launchDisplayCount = 3)
        )

        assertTrue(options.contains(MoreOptionAction.LaunchOnDisplay))
    }

    @Test
    fun `the row leads the menu`() {
        val options = buildMoreOptions(
            MoreOptionsContext(isDownloaded = true, canManageSaves = true, launchDisplayCount = 3)
        )

        assertEquals(MoreOptionAction.LaunchOnDisplay, options.first())
    }

    @Test
    fun `a game that is not downloaded has nowhere to be sent`() {
        val options = buildMoreOptions(
            MoreOptionsContext(isDownloaded = false, launchDisplayCount = 3)
        )

        assertFalse(options.contains(MoreOptionAction.LaunchOnDisplay))
    }

    @Test
    fun `a single screen is not a choice`() {
        val options = buildMoreOptions(
            MoreOptionsContext(isDownloaded = true, launchDisplayCount = 1)
        )

        assertFalse(options.contains(MoreOptionAction.LaunchOnDisplay))
    }
}
