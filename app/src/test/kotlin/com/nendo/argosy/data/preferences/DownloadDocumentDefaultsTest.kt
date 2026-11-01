package com.nendo.argosy.data.preferences

import com.nendo.argosy.data.model.VariantCategory
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A manual and a walkthrough describe the game rather than being part of it. They come with
 * every download and are never a choice, so they must not reach the settings list a player
 * toggles.
 */
class DownloadDocumentDefaultsTest {

    @Test
    fun `a manual is never offered as a download choice`() {
        assertFalse(
            VariantCategory.MANUAL.key in DownloadDefaults.CONFIGURABLE_KEYS
        )
    }

    @Test
    fun `a walkthrough is never offered as a download choice`() {
        assertFalse(
            VariantCategory.WALKTHROUGH.key in DownloadDefaults.CONFIGURABLE_KEYS
        )
    }

    @Test
    fun `documents are included by default`() {
        val resolved = DownloadDefaults.resolve(global = emptyMap(), platformOverride = emptyMap())

        assertTrue(resolved[VariantCategory.MANUAL.key] == true)
        assertTrue(resolved[VariantCategory.WALKTHROUGH.key] == true)
    }

    @Test
    fun `a value stored before documents were pinned cannot switch one off`() {
        val resolved = DownloadDefaults.resolve(
            global = mapOf(VariantCategory.WALKTHROUGH.key to false),
            platformOverride = mapOf(VariantCategory.MANUAL.key to false)
        )

        assertTrue(resolved[VariantCategory.WALKTHROUGH.key] == true)
        assertTrue(resolved[VariantCategory.MANUAL.key] == true)
    }

    @Test
    fun `every other category still reaches the settings list`() {
        val expected = VariantCategory.entries
            .filter {
                it != VariantCategory.GAME &&
                    it != VariantCategory.UNKNOWN &&
                    it.key !in DownloadDefaults.DOCUMENT_KEYS
            }
            .map { it.key }

        assertTrue(DownloadDefaults.CONFIGURABLE_KEYS.containsAll(expected))
        assertTrue(DownloadDefaults.OTHER_KEY in DownloadDefaults.CONFIGURABLE_KEYS)
    }
}
