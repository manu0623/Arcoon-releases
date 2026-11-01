package com.nendo.argosy.data.emulator

import com.nendo.argosy.data.platform.PlatformDefinitions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ForkEmulatorRegistryTest {

    @Test
    fun `Vita3K+ is detected by its own package and launches the shared Emulator activity`() {
        val plus = EmulatorRegistry.getByPackage("org.vita3kplus.emulator")!!
        assertEquals("vita3k-plus", plus.id)
        assertEquals("org.vita3k.emulator.Emulator", (plus.launchConfig as LaunchConfig.Vita3K).activityClass)
        assertTrue(EmulatorRegistry.getRecommendedEmulators()["vita"]!!.contains("vita3k-plus"))
        assertTrue(
            SavePathRegistry.getConfigByPackage("org.vita3kplus.emulator")!!
                .defaultPaths.single().contains("Android/data/org.vita3kplus.emulator/")
        )
    }

    @Test
    fun `XenDroid+ is detected by its own package and launches like XenDroid`() {
        val plus = EmulatorRegistry.getByPackage("xendroid.compose.fork")!!
        val original = EmulatorRegistry.getById("xendroid")!!
        assertEquals("xendroid-plus", plus.id)
        assertEquals(original.launchAction, plus.launchAction)
        assertEquals(original.launchConfig, plus.launchConfig)
        assertTrue(EmulatorRegistry.getRecommendedEmulators()["xbox360"]!!.contains("xendroid-plus"))
        assertTrue(
            SavePathRegistry.getConfigByPackage("xendroid.compose.fork")!!
                .defaultPaths.single().contains("Android/data/xendroid.compose.fork/")
        )
    }

    @Test
    fun `PS3 roms include chd images`() {
        assertTrue("chd" in PlatformDefinitions.getBySlug("ps3")!!.extensions)
    }
}
