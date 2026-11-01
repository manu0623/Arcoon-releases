package com.nendo.argosy.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopModeTest {

    class SamsungConfiguration(@JvmField val semDesktopModeEnabled: Int) {
        companion object {
            @JvmField val SEM_DESKTOP_MODE_ENABLED: Int = 1
        }
    }

    class ConfigurationWithoutConstant(@JvmField val semDesktopModeEnabled: Int)

    class ConfigurationWithoutModeField {
        companion object {
            @JvmField val SEM_DESKTOP_MODE_ENABLED: Int = 1
        }
    }

    class PlainConfiguration

    @Test
    fun `mode equal to the enabled constant reads as active`() {
        assertTrue(
            DesktopMode.isActive(SamsungConfiguration::class.java, SamsungConfiguration(1))
        )
    }

    @Test
    fun `mode different from the enabled constant reads as inactive`() {
        assertFalse(
            DesktopMode.isActive(SamsungConfiguration::class.java, SamsungConfiguration(0))
        )
        assertFalse(
            DesktopMode.isActive(SamsungConfiguration::class.java, SamsungConfiguration(2))
        )
    }

    @Test
    fun `a missing enabled constant reads as inactive`() {
        assertFalse(
            DesktopMode.isActive(
                ConfigurationWithoutConstant::class.java,
                ConfigurationWithoutConstant(1)
            )
        )
    }

    @Test
    fun `a missing mode field reads as inactive`() {
        assertFalse(
            DesktopMode.isActive(
                ConfigurationWithoutModeField::class.java,
                ConfigurationWithoutModeField()
            )
        )
    }

    @Test
    fun `a configuration with neither field reads as inactive`() {
        assertFalse(DesktopMode.isActive(PlainConfiguration::class.java, PlainConfiguration()))
    }

    @Test
    fun `an object that is not the named class reads as inactive`() {
        assertFalse(DesktopMode.isActive(SamsungConfiguration::class.java, PlainConfiguration()))
    }

    @Test
    fun `the desktop mode system feature reads as supported without the samsung fields`() {
        assertTrue(DesktopMode.isSupported(hasFeature = true, configurationClass = PlainConfiguration::class.java))
    }

    @Test
    fun `the samsung enabled constant reads as supported while desktop mode is off`() {
        assertTrue(
            DesktopMode.isSupported(hasFeature = false, configurationClass = ConfigurationWithoutModeField::class.java)
        )
        assertTrue(
            DesktopMode.isSupported(hasFeature = false, configurationClass = SamsungConfiguration::class.java)
        )
    }

    @Test
    fun `neither the feature nor the constant reads as unsupported`() {
        assertFalse(DesktopMode.isSupported(hasFeature = false, configurationClass = PlainConfiguration::class.java))
        assertFalse(
            DesktopMode.isSupported(hasFeature = false, configurationClass = ConfigurationWithoutConstant::class.java)
        )
    }
}
