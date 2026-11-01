package com.nendo.argosy.libretro.coreoptions.manifests

import com.nendo.argosy.libretro.coreoptions.CoreOptionDef
import com.nendo.argosy.libretro.coreoptions.CoreOptionManifest

object HandyManifest : CoreOptionManifest {
    override val coreId = "handy"
    override val options = listOf(
        CoreOptionDef(
            key = "handy_refresh_rate",
            displayName = "Video Refresh Rate",
            values = listOf("50", "60", "75", "100", "120"),
            defaultValue = "60",
            description = "Sets the target video refresh rate in Hz",
            valueLabels = mapOf(
                "50" to "50 Hz", "60" to "60 Hz", "75" to "75 Hz",
                "100" to "100 Hz", "120" to "120 Hz"
            )
        ),
        CoreOptionDef(
            key = "handy_rot",
            displayName = "Display Rotation",
            values = listOf("Auto", "None", "270", "180", "90"),
            defaultValue = "Auto",
            description = "Rotates the display to match the game's intended orientation",
            valueLabels = mapOf("270" to "270 deg", "180" to "180 deg", "90" to "90 deg")
        ),
        CoreOptionDef(
            key = "handy_gfx_colors",
            displayName = "Color Depth (Restart Required)",
            values = listOf("16bit", "24bit"),
            defaultValue = "16bit",
            description = "Sets the color depth used for rendering",
            valueLabels = mapOf("16bit" to "16-bit", "24bit" to "24-bit")
        ),
        CoreOptionDef(
            key = "handy_lcd_ghosting",
            displayName = "LCD Ghosting Filter",
            values = listOf("disabled", "2frames", "3frames", "4frames"),
            defaultValue = "disabled",
            description = "Simulates the motion blur of the original Lynx LCD screen",
            valueLabels = mapOf(
                "disabled" to "Off", "2frames" to "2 frames", "3frames" to "3 frames", "4frames" to "4 frames"
            )
        ),
        CoreOptionDef(
            key = "handy_overclock",
            displayName = "CPU Overclock Multiplier",
            values = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "20", "30", "40", "50"),
            defaultValue = "1",
            description = "Multiplies the emulated CPU speed to reduce slowdown",
            valueLabels = mapOf(
                "1" to "1x (native)", "2" to "2x", "3" to "3x", "4" to "4x",
                "5" to "5x", "6" to "6x", "7" to "7x", "8" to "8x", "9" to "9x",
                "10" to "10x", "20" to "20x", "30" to "30x", "40" to "40x", "50" to "50x"
            )
        ),
        CoreOptionDef(
            key = "handy_frameskip",
            displayName = "Frameskip",
            values = listOf("disabled", "auto", "manual"),
            defaultValue = "disabled",
            description = "Skips rendering some frames to improve performance"
        ),
        CoreOptionDef(
            key = "handy_frameskip_threshold",
            displayName = "Frameskip Threshold (%)",
            values = listOf("15", "18", "21", "24", "27", "30", "33", "36", "39", "42", "45", "48", "51", "54", "57", "60"),
            defaultValue = "33",
            description = "Sets the audio buffer occupancy below which frames are skipped",
            valueLabels = mapOf(
                "15" to "15%", "18" to "18%", "21" to "21%", "24" to "24%",
                "27" to "27%", "30" to "30%", "33" to "33%", "36" to "36%",
                "39" to "39%", "42" to "42%", "45" to "45%", "48" to "48%",
                "51" to "51%", "54" to "54%", "57" to "57%", "60" to "60%"
            )
        ),
    )
}
