package com.nendo.argosy.data.emulator

import com.nendo.argosy.data.repository.SaveSyncApiClient
import com.nendo.argosy.data.sync.platform.SigilSaveHandler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EdenDuoRegistryTest {

    private val pkg = "dev.igawa6.edenduo"

    @Test
    fun `the package resolves to its own emulator entry`() {
        val def = requireNotNull(EmulatorRegistry.getByPackage(pkg))

        assertEquals("eden_duo", def.id)
        assertTrue("switch" in def.supportedPlatforms)
        assertEquals("eden", EmulatorRegistry.familyBaseIdFor(def.id))
    }

    @Test
    fun `the package owns a save config templated on its own data directory`() {
        val config = requireNotNull(SavePathRegistry.getConfigByPackage(pkg))

        assertEquals("eden_duo", config.emulatorId)
        assertTrue(config.usesPackageTemplate)
        assertEquals(
            SavePathRegistry.getConfig("eden")?.defaultPaths,
            config.defaultPaths
        )
        assertEquals("eden_duo", SavePathRegistry.canonicalConfigId("eden_duo", pkg))
    }

    @Test
    fun `save sync collects it with the Eden layout`() {
        assertEquals("eden", SigilSaveHandler.layoutFor("eden_duo", "switch"))
        assertTrue("eden_duo" in SaveSyncApiClient.SWITCH_EMULATOR_IDS)
    }

    @Test
    fun `firmware and keys install into it when it is the only Eden build`() {
        assertTrue(pkg in BiosPathRegistry.EDEN_PACKAGES)
    }
}
