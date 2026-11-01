package com.nendo.argosy.libretro.coreoptions.manifests

import com.nendo.argosy.libretro.coreoptions.CoreOptionDef
import com.nendo.argosy.libretro.coreoptions.CoreOptionManifest

object MelondsManifest : CoreOptionManifest {
    override val coreId = "melonds"
    override val options = listOf(
        // System
        CoreOptionDef(
            key = "melonds_console_mode",
            displayName = "Console Mode",
            values = listOf("DS", "DSi"),
            defaultValue = "DS",
            description = "Selects whether to emulate DS or DSi hardware"
        ),
        CoreOptionDef(
            key = "melonds_boot_directly",
            displayName = "Boot Game Directly",
            values = listOf("enabled", "disabled"),
            defaultValue = "enabled",
            description = "Skips the DS firmware menu and boots the game immediately"
        ),
        CoreOptionDef(
            key = "melonds_use_fw_settings",
            displayName = "Use Firmware Settings",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Uses name, birthday, and other settings from the firmware file"
        ),
        CoreOptionDef(
            key = "melonds_language",
            displayName = "Language",
            values = listOf("Japanese", "English", "French", "German", "Italian", "Spanish"),
            defaultValue = "English"
        ),
        CoreOptionDef(
            key = "melonds_randomize_mac_address",
            displayName = "Randomize MAC Address",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Generates a random MAC address on each boot for online features"
        ),
        CoreOptionDef(
            key = "melonds_dsi_sdcard",
            displayName = "Enable DSi SD Card",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Emulates an SD card inserted in the DSi for homebrew and DSiWare"
        ),
        // Video
        CoreOptionDef(
            key = "melonds_threaded_renderer",
            displayName = "Threaded Software Renderer",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Runs software rendering on a separate thread for better performance"
        ),
        // Audio
        CoreOptionDef(
            key = "melonds_mic_input",
            displayName = "Microphone Input",
            values = listOf("Blow Noise", "White Noise"),
            defaultValue = "Blow Noise",
            description = "Selects the simulated sound fed to the DS microphone when activated"
        ),
        CoreOptionDef(
            key = "melonds_audio_bitrate",
            displayName = "Audio Bitrate",
            values = listOf("Automatic", "10-bit", "16-bit"),
            defaultValue = "Automatic",
            description = "Sets the audio output bit depth, 10-bit matches original hardware"
        ),
        CoreOptionDef(
            key = "melonds_audio_interpolation",
            displayName = "Audio Interpolation",
            values = listOf("None", "Linear", "Cosine", "Cubic"),
            defaultValue = "None",
            description = "Sets the quality of audio sample interpolation"
        ),
        // Screen
        CoreOptionDef(
            key = "melonds_touch_mode",
            displayName = "Touch Mode",
            values = listOf("Mouse", "Touch", "Joystick", "disabled"),
            defaultValue = "Mouse",
            description = "Selects the input device used for the DS touch screen"
        ),
        CoreOptionDef(
            key = "melonds_swapscreen_mode",
            displayName = "Swap Screen Mode",
            values = listOf("Toggle", "Hold"),
            defaultValue = "Toggle",
            description = "Sets whether screen swap is toggled or only active while held"
        ),
        CoreOptionDef(
            key = "melonds_screen_layout",
            displayName = "Screen Layout",
            values = listOf(
                "Top/Bottom", "Bottom/Top", "Left/Right", "Right/Left",
                "Top Only", "Bottom Only", "Hybrid Top", "Hybrid Bottom"
            ),
            defaultValue = "Top/Bottom",
            description = "Sets the arrangement of the DS top and bottom screens"
        ),
        CoreOptionDef(
            key = "melonds_screen_gap",
            displayName = "Screen Gap",
            values = (0..126).map { it.toString() },
            defaultValue = "0",
            description = "Sets the pixel gap between the top and bottom screens"
        ),
        CoreOptionDef(
            key = "melonds_hybrid_small_screen",
            displayName = "Hybrid Small Screen Mode",
            values = listOf("Bottom", "Top", "Duplicate"),
            defaultValue = "Bottom",
            description = "Selects which screen appears as the small inset in hybrid layout"
        ),
        CoreOptionDef(
            key = "melonds_hybrid_ratio",
            displayName = "Hybrid Ratio (OpenGL Only)",
            values = listOf("2", "3"),
            defaultValue = "2",
            description = "Sets the size ratio between the large and small screen in hybrid layout",
            valueLabels = mapOf("2" to "2:1", "3" to "3:1")
        ),
        // CPU Emulation
        CoreOptionDef(
            key = "melonds_jit_enable",
            displayName = "JIT Enable (Restart)",
            values = listOf("enabled", "disabled"),
            defaultValue = "enabled",
            description = "Enables JIT dynamic recompilation for faster CPU emulation"
        ),
        CoreOptionDef(
            key = "melonds_jit_block_size",
            displayName = "JIT Block Size",
            values = (1..32).map { it.toString() },
            defaultValue = "32",
            description = "Sets the maximum number of instructions compiled per JIT block"
        ),
        CoreOptionDef(
            key = "melonds_jit_branch_optimisations",
            displayName = "JIT Branch Optimisations",
            values = listOf("enabled", "disabled"),
            defaultValue = "enabled",
            description = "Enables branch prediction optimizations in the JIT compiler"
        ),
        CoreOptionDef(
            key = "melonds_jit_literal_optimisations",
            displayName = "JIT Literal Optimisations",
            values = listOf("enabled", "disabled"),
            defaultValue = "enabled",
            description = "Enables literal pool optimizations in the JIT compiler"
        ),
        CoreOptionDef(
            key = "melonds_jit_fast_memory",
            displayName = "JIT Fast Memory",
            values = listOf("enabled", "disabled"),
            defaultValue = "enabled",
            description = "Skips memory access safety checks in JIT for faster emulation"
        ),
    )
}
