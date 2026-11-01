package com.nendo.argosy.data.preferences

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DisplayPreferencesDefaultsTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `an empty store reads back the constructor defaults`() = runTest {
        val file = tempFolder.newFile("settings.preferences_pb")
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        val store = PreferenceDataStoreFactory.create(scope = scope) { file }.apply {
            edit { it[booleanPreferencesKey("dual_screen_enabled")] = false }
        }

        val parsed = DisplayPreferencesRepository(store).preferences.first()

        assertEquals(DisplayPreferences(), parsed)
    }

    @Test
    fun `the aggregate defaults match the display defaults`() {
        val display = DisplayPreferences()
        val user = UserPreferences()

        assertEquals(display.backgroundBlur, user.backgroundBlur)
        assertEquals(display.backdropPreset, user.backdropPreset)
        assertEquals(display.backdropEdgeStyle, user.backdropEdgeStyle)
        assertEquals(display.backdropMotion, user.backdropMotion)
        assertEquals(display.boxArtBorderStyle, user.boxArtBorderStyle)
        assertEquals(display.glassBorderTint, user.glassBorderTint)
        assertEquals(display.boxArtOuterEffectThickness, user.boxArtOuterEffectThickness)
        assertEquals(display.boxArtInnerEffect, user.boxArtInnerEffect)
        assertEquals(display.boxArtInnerEffectThickness, user.boxArtInnerEffectThickness)
        assertEquals(AppPreferences().closeEmulatorOnSessionEnd, user.closeEmulatorOnSessionEnd)
    }
}
