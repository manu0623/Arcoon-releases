package com.nendo.argosy.ui.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GpuDriverBlockTest {

    @Test
    fun `global context only checks the device`() {
        assertNull(gpuDriverBlock(null, null, customDriversSupported = true))
        assertEquals(GpuDriverBlock.NO_CUSTOM_DRIVERS, gpuDriverBlock(null, null, customDriversSupported = false))
    }

    @Test
    fun `a core without Vulkan blocks before anything else`() {
        assertEquals(GpuDriverBlock.CORE_UNSUPPORTED, gpuDriverBlock(false, false, customDriversSupported = false))
        assertEquals(GpuDriverBlock.CORE_UNSUPPORTED, gpuDriverBlock(false, null, customDriversSupported = true))
    }

    @Test
    fun `a Vulkan capable core blocks until its backend is Vulkan`() {
        assertEquals(GpuDriverBlock.BACKEND_NOT_VULKAN, gpuDriverBlock(true, false, customDriversSupported = true))
        assertEquals(GpuDriverBlock.BACKEND_NOT_VULKAN, gpuDriverBlock(true, false, customDriversSupported = false))
    }

    @Test
    fun `picker lists values, then the downloaded packages to install, and nothing else`() {
        assertEquals(
            listOf(
                GpuDriverPickerEntry.Value(null),
                GpuDriverPickerEntry.Value("system"),
                GpuDriverPickerEntry.Install("turnip.adpkg.zip")
            ),
            gpuDriverPickerEntries(listOf(null, "system"), listOf("turnip.adpkg.zip"))
        )
        assertEquals(
            listOf(GpuDriverPickerEntry.Value("system")),
            gpuDriverPickerEntries(listOf("system"), emptyList())
        )
    }

    @Test
    fun `a core that only runs the system driver blocks whatever the backend and device`() {
        listOf(true to true, true to false).forEach { (supportsVulkan, isVulkan) ->
            listOf(true, false).forEach { deviceSupports ->
                assertEquals(
                    GpuDriverBlock.CORE_SYSTEM_DRIVER_ONLY,
                    gpuDriverBlock(supportsVulkan, isVulkan, deviceSupports, coreAcceptsCustomDriver = false)
                )
            }
        }
        assertEquals(
            GpuDriverBlock.CORE_UNSUPPORTED,
            gpuDriverBlock(false, null, customDriversSupported = true, coreAcceptsCustomDriver = false)
        )
    }

    @Test
    fun `a Vulkan backend is open on a device that loads custom drivers`() {
        assertNull(gpuDriverBlock(true, true, customDriversSupported = true))
        assertEquals(GpuDriverBlock.NO_CUSTOM_DRIVERS, gpuDriverBlock(true, true, customDriversSupported = false))
    }
}
