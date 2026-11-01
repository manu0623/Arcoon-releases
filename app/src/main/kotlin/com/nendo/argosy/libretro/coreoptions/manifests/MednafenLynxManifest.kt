package com.nendo.argosy.libretro.coreoptions.manifests

import com.nendo.argosy.libretro.coreoptions.CoreOptionDef
import com.nendo.argosy.libretro.coreoptions.CoreOptionManifest

object MednafenLynxManifest : CoreOptionManifest {
    override val coreId = "mednafen_lynx"
    override val options = listOf(
        CoreOptionDef(
            key = "lynx_rot_screen",
            displayName = "Auto-Rotate Screen",
            values = listOf("auto", "manual", "0", "90", "180", "270"),
            defaultValue = "auto",
            description = "Automatically rotates the display to match the game's orientation",
            valueLabels = mapOf(
                "auto" to "Auto", "manual" to "Manual",
                "0" to "0 deg", "90" to "90 deg", "180" to "180 deg", "270" to "270 deg"
            )
        ),
        CoreOptionDef(
            key = "lynx_pix_format",
            displayName = "Color Format (Restart Required)",
            values = listOf("16", "32"),
            defaultValue = "16",
            description = "Sets the color depth in bits per pixel",
            valueLabels = mapOf("16" to "16-bit", "32" to "32-bit")
        ),
        CoreOptionDef(
            key = "lynx_force_60hz",
            displayName = "Force 60Hz",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Forces 60Hz output instead of the Lynx's native 75Hz refresh rate"
        ),
    )
}
