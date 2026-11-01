package com.nendo.argosy.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StorageAccessTest {

    private val pie = 28
    private val android11 = 30

    @Test
    fun `before Android 11 storage is granted only with both legacy permissions`() {
        assertFalse(StorageAccess.isGranted(pie, { true }) { false })
        assertFalse(
            StorageAccess.isGranted(pie, { true }) { it == android.Manifest.permission.READ_EXTERNAL_STORAGE }
        )
        assertTrue(StorageAccess.isGranted(pie, { false }) { true })
    }

    @Test
    fun `from Android 11 storage follows All files access alone`() {
        assertTrue(StorageAccess.isGranted(android11, { true }) { false })
        assertFalse(StorageAccess.isGranted(android11, { false }) { true })
    }
}
