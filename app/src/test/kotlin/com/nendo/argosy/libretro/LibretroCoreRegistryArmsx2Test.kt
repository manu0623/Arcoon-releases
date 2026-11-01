package com.nendo.argosy.libretro

import com.nendo.argosy.libretro.coreoptions.CoreOptionManifestRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LibretroCoreRegistryArmsx2Test {

    private fun armsx2(is64Bit: Boolean) =
        LibretroCoreRegistry.coresFor(is64BitProcess = is64Bit).firstOrNull { it.coreId == "armsx2" }

    @Test
    fun `a 64-bit process offers ARMSX2 as the ps2 default`() {
        val core = armsx2(is64Bit = true)

        assertNotNull(core)
        assertEquals("armsx2_libretro_android.so", core!!.fileName)
        assertEquals("ARMSX2", core.displayName)
        assertEquals(setOf("ps2"), core.platforms)
        assertTrue(core.isDefault)
        assertTrue(core.requiresBios.isEmpty())
        assertEquals(31_000_000L, core.estimatedSizeBytes)
        assertEquals(
            listOf("armsx2"),
            LibretroCoreRegistry.coresFor(is64BitProcess = true).filter { "ps2" in it.platforms }.map { it.coreId }
        )
    }

    @Test
    fun `a 32-bit process never offers ARMSX2`() {
        assertNull(armsx2(is64Bit = false))
        assertTrue(LibretroCoreRegistry.coresFor(is64BitProcess = false).none { "ps2" in it.platforms })
    }

    @Test
    fun `only ARMSX2 is held back from a 32-bit process`() {
        val held = LibretroCoreRegistry.coresFor(true) - LibretroCoreRegistry.coresFor(false).toSet()
        assertEquals(listOf("armsx2"), held.map { it.coreId })
    }

    @Test
    fun `ARMSX2 serialize is treated as crash-prone and its resume is not torn down without an observed crash`() {
        assertFalse(LibretroCoreRegistry.isHardwareRendered("armsx2"))
        assertTrue(LibretroCoreRegistry.serializeMayCrash("armsx2"))
        assertFalse(LibretroCoreRegistry.coreStatesAllowed("armsx2", hwCoreSaveStatesEnabled = false))
    }

    @Test
    fun `the ARMSX2 manifest carries the upstream keys, values and defaults`() {
        val options = CoreOptionManifestRegistry.getManifest("armsx2")!!.options.associateBy { it.key }
        val expected = mapOf(
            "armsx2_renderer" to (listOf("Vulkan", "OpenGL", "Software") to "Vulkan"),
            "armsx2_upscale" to (listOf("1x", "2x", "3x", "4x") to "1x"),
            "armsx2_aspect_ratio" to (listOf("Auto 4:3/3:2", "4:3", "16:9", "Stretch") to "Auto 4:3/3:2"),
            "armsx2_deinterlacing" to (
                listOf(
                    "Automatic", "Off", "Weave TFF", "Weave BFF", "Bob TFF", "Bob BFF",
                    "Blend TFF", "Blend BFF", "Adaptive TFF", "Adaptive BFF"
                ) to "Automatic"
                ),
            "armsx2_no_interlacing_patches" to (listOf("disabled", "enabled") to "disabled"),
            "armsx2_widescreen_patches" to (listOf("disabled", "enabled") to "disabled"),
            "armsx2_blending_accuracy" to (listOf("Minimum", "Basic", "Medium", "High", "Full", "Maximum") to "Basic"),
            "armsx2_dithering" to (listOf("Unscaled", "Off", "Scaled") to "Unscaled"),
            "armsx2_trilinear_filtering" to (
                listOf("Automatic", "Off", "Trilinear (PS2)", "Trilinear (Forced)") to "Automatic"
                ),
            "armsx2_mipmapping" to (listOf("enabled", "disabled") to "enabled"),
            "armsx2_fxaa" to (listOf("disabled", "enabled") to "disabled"),
            "armsx2_texture_filtering" to (
                listOf("Nearest", "Bilinear (Forced)", "Bilinear (PS2)", "Bilinear (Forced excluding sprites)") to
                    "Bilinear (PS2)"
                ),
            "armsx2_anisotropic_filtering" to (listOf("0", "2", "4", "8", "16") to "0"),
            "armsx2_sw_threads" to (listOf("0", "1", "2", "3", "4") to "2"),
            "armsx2_show_fps" to (listOf("disabled", "enabled") to "disabled"),
            "armsx2_ee_cycle_rate" to (listOf("50%", "60%", "75%", "100%", "130%", "180%", "300%") to "100%"),
            "armsx2_ee_cycle_skip" to (listOf("disabled", "mild", "moderate", "maximum") to "disabled"),
            "armsx2_hw_download_mode" to (
                listOf("Accurate", "Disable Readbacks", "Unsynchronized", "Disabled") to "Accurate"
                ),
            "armsx2_mtvu" to (listOf("enabled", "disabled") to "enabled"),
            "armsx2_instant_vu1" to (listOf("enabled", "disabled") to "enabled"),
            "armsx2_fast_boot" to (listOf("enabled", "disabled") to "enabled")
        )

        assertEquals(expected.keys, options.keys)
        expected.forEach { (key, valuesAndDefault) ->
            val def = options.getValue(key)
            assertEquals(key, valuesAndDefault.first, def.values)
            assertEquals(key, valuesAndDefault.second, def.defaultValue)
        }
    }

    @Test
    fun `only the renderer differs from the core default so a no-Vulkan device can fall back`() {
        val options = CoreOptionManifestRegistry.getManifest("armsx2")!!.options
        val overridden = options.filter { it.defaultValue != it.coreDefault }

        assertEquals(listOf("armsx2_renderer"), overridden.map { it.key })
        assertEquals("OpenGL", overridden.single().coreDefault)
    }
}
