package com.nendo.argosy.libretro.coreoptions.manifests

import com.nendo.argosy.libretro.coreoptions.CoreOptionDef
import com.nendo.argosy.libretro.coreoptions.CoreOptionManifest

object Armsx2Manifest : CoreOptionManifest {
    override val coreId = "armsx2"
    override val options = listOf(
        CoreOptionDef(
            key = "armsx2_renderer",
            displayName = "Renderer",
            values = listOf("Vulkan", "OpenGL", "Software"),
            defaultValue = "Vulkan",
            coreDefault = "OpenGL",
            description = "Graphics API the core draws with. Takes effect after the game is relaunched"
        ),
        CoreOptionDef(
            key = "armsx2_upscale",
            displayName = "Internal Resolution",
            values = listOf("1x", "2x", "3x", "4x"),
            defaultValue = "1x",
            description = "Renders above the native PS2 resolution. Costs performance quickly"
        ),
        CoreOptionDef(
            key = "armsx2_aspect_ratio",
            displayName = "Aspect Ratio",
            values = listOf("Auto 4:3/3:2", "4:3", "16:9", "Stretch"),
            defaultValue = "Auto 4:3/3:2"
        ),
        CoreOptionDef(
            key = "armsx2_deinterlacing",
            displayName = "Deinterlacing",
            values = listOf(
                "Automatic", "Off", "Weave TFF", "Weave BFF", "Bob TFF", "Bob BFF",
                "Blend TFF", "Blend BFF", "Adaptive TFF", "Adaptive BFF"
            ),
            defaultValue = "Automatic"
        ),
        CoreOptionDef(
            key = "armsx2_no_interlacing_patches",
            displayName = "No-Interlacing Patches",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled"
        ),
        CoreOptionDef(
            key = "armsx2_widescreen_patches",
            displayName = "Widescreen Patches",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled"
        ),
        CoreOptionDef(
            key = "armsx2_blending_accuracy",
            displayName = "Blending Accuracy",
            values = listOf("Minimum", "Basic", "Medium", "High", "Full", "Maximum"),
            defaultValue = "Basic",
            description = "Higher levels fix blending effects at a large cost to speed"
        ),
        CoreOptionDef(
            key = "armsx2_dithering",
            displayName = "Dithering",
            values = listOf("Unscaled", "Off", "Scaled"),
            defaultValue = "Unscaled"
        ),
        CoreOptionDef(
            key = "armsx2_trilinear_filtering",
            displayName = "Trilinear Filtering",
            values = listOf("Automatic", "Off", "Trilinear (PS2)", "Trilinear (Forced)"),
            defaultValue = "Automatic"
        ),
        CoreOptionDef(
            key = "armsx2_mipmapping",
            displayName = "Mipmapping",
            values = listOf("enabled", "disabled"),
            defaultValue = "enabled"
        ),
        CoreOptionDef(
            key = "armsx2_fxaa",
            displayName = "FXAA",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled"
        ),
        CoreOptionDef(
            key = "armsx2_texture_filtering",
            displayName = "Texture Filtering",
            values = listOf(
                "Nearest", "Bilinear (Forced)", "Bilinear (PS2)", "Bilinear (Forced excluding sprites)"
            ),
            defaultValue = "Bilinear (PS2)"
        ),
        CoreOptionDef(
            key = "armsx2_anisotropic_filtering",
            displayName = "Anisotropic Filtering",
            values = listOf("0", "2", "4", "8", "16"),
            defaultValue = "0",
            valueLabels = mapOf("0" to "Off", "2" to "2x", "4" to "4x", "8" to "8x", "16" to "16x")
        ),
        CoreOptionDef(
            key = "armsx2_sw_threads",
            displayName = "Software Renderer Threads",
            values = listOf("0", "1", "2", "3", "4"),
            defaultValue = "2",
            description = "Worker threads for the Software renderer"
        ),
        CoreOptionDef(
            key = "armsx2_show_fps",
            displayName = "Show FPS",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled"
        ),
        CoreOptionDef(
            key = "armsx2_ee_cycle_rate",
            displayName = "EE Cycle Rate",
            values = listOf("50%", "60%", "75%", "100%", "130%", "180%", "300%"),
            defaultValue = "100%",
            description = "Emulated Emotion Engine clock. Lower values are faster but can break games"
        ),
        CoreOptionDef(
            key = "armsx2_ee_cycle_skip",
            displayName = "EE Cycle Skip",
            values = listOf("disabled", "mild", "moderate", "maximum"),
            defaultValue = "disabled"
        ),
        CoreOptionDef(
            key = "armsx2_hw_download_mode",
            displayName = "Hardware Download Mode",
            values = listOf("Accurate", "Disable Readbacks", "Unsynchronized", "Disabled"),
            defaultValue = "Accurate"
        ),
        CoreOptionDef(
            key = "armsx2_mtvu",
            displayName = "MTVU (Multi-Threaded VU1)",
            values = listOf("enabled", "disabled"),
            defaultValue = "enabled"
        ),
        CoreOptionDef(
            key = "armsx2_instant_vu1",
            displayName = "Instant VU1",
            values = listOf("enabled", "disabled"),
            defaultValue = "enabled"
        ),
        CoreOptionDef(
            key = "armsx2_fast_boot",
            displayName = "Fast Boot",
            values = listOf("enabled", "disabled"),
            defaultValue = "enabled",
            description = "Skips the PS2 BIOS intro"
        )
    )
}
