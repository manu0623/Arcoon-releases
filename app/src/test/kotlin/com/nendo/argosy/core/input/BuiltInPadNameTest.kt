package com.nendo.argosy.core.input

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BuiltInPadNameTest {

    @Test
    fun `a pad named for the device brand is built in`() {
        assertTrue(ControllerDetector.padNamedForDevice("Retroid Pocket Controller", "Retroid Pocket Nova"))
    }

    @Test
    fun `an external pad on the same device is not`() {
        assertFalse(ControllerDetector.padNamedForDevice("Xbox Wireless Controller", "Retroid Pocket Nova"))
        assertFalse(ControllerDetector.padNamedForDevice(null, "Retroid Pocket Nova"))
    }

    @Test
    fun `an AYN handheld's Odin Controller is built in`() {
        assertTrue(ControllerDetector.padNamedForDevice("Odin Controller", "AYN Thor", "AYN"))
    }

    @Test
    fun `an Odin Controller on another maker's device is not`() {
        assertFalse(ControllerDetector.padNamedForDevice("Odin Controller", "Retroid Pocket Nova", "Retroid"))
        assertFalse(ControllerDetector.padNamedForDevice("Xbox Wireless Controller", "AYN Thor", "AYN"))
    }

    @Test
    fun `AYN, Retroid and AYANEO pad vendors count as built in, in any controller mode`() {
        assertTrue(ControllerDetector.isHandheldVendor(0x2020))
        assertTrue(ControllerDetector.isHandheldVendor(0x2022))
        assertTrue(ControllerDetector.isHandheldVendor(0x4001))
    }

    @Test
    fun `external pad vendors do not`() {
        assertFalse(ControllerDetector.isHandheldVendor(0x045e))
        assertFalse(ControllerDetector.isHandheldVendor(0x054c))
        assertFalse(ControllerDetector.isHandheldVendor(0x057e))
        assertFalse(ControllerDetector.isHandheldVendor(0x2dc8))
    }

    @Test
    fun `a short or missing model never matches`() {
        assertFalse(ControllerDetector.padNamedForDevice("RP Controller", "RP"))
        assertFalse(ControllerDetector.padNamedForDevice("Controller", null))
    }
}
