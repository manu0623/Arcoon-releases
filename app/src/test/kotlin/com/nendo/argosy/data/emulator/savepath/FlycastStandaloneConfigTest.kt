package com.nendo.argosy.data.emulator.savepath

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FlycastStandaloneConfigTest {

    private val data = "/storage/emulated/0/Android/data/com.flycast.emulator/files/data"

    @Test
    fun `an empty config keeps per-game VMUs in the data folder`() {
        val config = FlycastStandaloneConfig.parse("", data)

        assertEquals(FlycastStandaloneConfig(perGameVmu = true, vmuDir = data), config)
        assertEquals(mapOf("PerGameVmu" to "yes"), config.sigilOptions)
    }

    @Test
    fun `PerGameVmu is on only for the values Flycast reads as true`() {
        listOf("yes", "TRUE", "on", "1").forEach {
            assertEquals(it, true, FlycastStandaloneConfig.parse("[config]\nPerGameVmu = $it\n", data).perGameVmu)
        }
        listOf("no", "false", "0", "").forEach {
            assertEquals(it, false, FlycastStandaloneConfig.parse("[config]\nPerGameVmu = $it\n", data).perGameVmu)
        }
    }

    @Test
    fun `a key outside the config section is ignored`() {
        val config = FlycastStandaloneConfig.parse("[input]\nPerGameVmu = no\n[config]\nrend.Resolution = 960\n", data)

        assertEquals(true, config.perGameVmu)
    }

    @Test
    fun `a VMU path moves the VMU folder`() {
        val config = FlycastStandaloneConfig.parse("[config]\r\nDreamcast.VMUPath = \"/storage/emulated/0/Flycast/vmu/\"\r\n", data)

        assertEquals("/storage/emulated/0/Flycast/vmu", config.vmuDir)
    }

    @Test
    fun `a tree uri resolves to the folder it points at`() {
        assertEquals(
            "/storage/emulated/0/Flycast/VMU",
            FlycastStandaloneConfig.pathOf("content://com.android.externalstorage.documents/tree/primary%3AFlycast%2FVMU")
        )
        assertEquals(
            "/storage/546B-6466/Games/dc",
            FlycastStandaloneConfig.pathOf("content://com.android.externalstorage.documents/tree/546B-6466%3AGames%2Fdc")
        )
        assertEquals(
            "/storage/emulated/0",
            FlycastStandaloneConfig.pathOf("content://com.android.externalstorage.documents/tree/primary%3A")
        )
    }

    @Test
    fun `a uri with no file path leaves no VMU folder`() {
        val config = FlycastStandaloneConfig.parse(
            "[config]\nDreamcast.VMUPath = content://com.android.providers.downloads.documents/tree/downloads\n",
            data
        )

        assertNull(config.vmuDir)
    }
}
