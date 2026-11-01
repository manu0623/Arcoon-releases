package com.nendo.argosy.data.sync.platform

import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test

class DreamcastSaveHandlerTest {

    private val handler = DreamcastSaveHandler(mockk(relaxed = true))

    @Test
    fun `the built-in core names port A1 after the product number`() {
        assertEquals(listOf("T13301N.A1"), handler.saveFileBaseNames("T13301N"))
        assertEquals(listOf("HDR_0038.A1"), handler.saveFileBaseNames("HDR 0038"))
    }

    @Test
    fun `standalone Flycast names port A1 the way getVmuPath does`() {
        assertEquals(listOf("T13301N_vmu_save_A1"), handler.standalone.saveFileBaseNames("T13301N"))
        assertEquals(listOf("MK_51035__vmu_save_A1"), handler.standalone.saveFileBaseNames("MK 51035\""))
        assertEquals(emptyList<String>(), handler.standalone.saveFileBaseNames(" "))
    }
}
