package com.nendo.argosy.ui.screens.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsEntrySectionTest {

    @Test
    fun `route template placeholder is not a section`() {
        assertNull(settingsEntrySection("{section}"))
        assertNull(settingsEntrySection(" {action} "))
        assertFalse(settingsEntryCovered("{section}"))
    }

    @Test
    fun `absent section opens the main list uncovered`() {
        assertNull(settingsEntrySection(null))
        assertNull(settingsEntrySection("   "))
        assertNull(settingsEntrySection("not_a_section"))
        assertFalse(settingsEntryCovered(null))
        assertFalse(settingsEntryCovered("main"))
    }

    @Test
    fun `real sections resolve case insensitively and deep entries are covered`() {
        assertEquals(SettingsSection.ROMM, settingsEntrySection("romm"))
        assertEquals(SettingsSection.PLATFORM_DETAIL, settingsEntrySection("platform_detail"))
        assertTrue(settingsEntryCovered("ROMM"))
    }
}
