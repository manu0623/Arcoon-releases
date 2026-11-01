package com.nendo.argosy.data.emulator

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Test

class CemuRegistryTest {

    private val cemuIds = listOf("cemu", "cemu_dualscreen")

    @Test
    fun `both cemu entries launch EmulationActivity by explicit component`() {
        cemuIds.forEach { id ->
            val def = requireNotNull(EmulatorRegistry.getById(id))
            val config = def.launchConfig as LaunchConfig.Custom

            assertEquals(id, "info.cemu.cemu", def.packageName)
            assertEquals(id, Intent.ACTION_VIEW, def.launchAction)
            assertEquals(id, "info.cemu.cemu.emulation.EmulationActivity", config.activityClass)
        }
    }

    @Test
    fun `both cemu entries keep the file uri launch defaults`() {
        cemuIds.forEach { id ->
            val def = requireNotNull(EmulatorRegistry.getById(id))
            val config = def.launchConfig

            assertEquals(id, LaunchConfig.FileUri.defaultIntentFlags(def), config.defaultIntentFlags(def))
            assertEquals(id, LaunchConfig.FileUri.defaultMimeType, config.defaultMimeType)
            assertEquals(id, LaunchConfig.FileUri.bindingDefaults(def), config.bindingDefaults(def))
            assertEquals(id, null, config.defaultDataBinding)
        }
    }
}
