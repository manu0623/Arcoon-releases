package com.nendo.argosy.data.emulator.savepath

import com.nendo.argosy.data.emulator.SavePathRegistry
import com.nendo.argosy.data.storage.FileAccessLayer
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PpssppMemoryStickTest {

    private val fal: FileAccessLayer = mockk(relaxed = true)
    private val ppsspp = SavePathRegistry.getConfig("ppsspp")!!
    private val dolphin = SavePathRegistry.getConfig("dolphin")!!

    private fun ini(path: String, modifiedAt: Long) {
        every { fal.exists(path) } returns true
        every { fal.lastModified(path) } returns modifiedAt
    }

    @Test
    fun `a vr build config marks the stick as live`() {
        ini("$SD/PSP/SYSTEM/ppssppvr.ini", 100L)

        assertEquals("$SD/PSP/SAVEDATA", PpssppMemoryStick.liveSaveData(ppsspp, listOf("$INTERNAL/PSP/SAVEDATA", "$SD/PSP/SAVEDATA"), fal))
    }

    @Test
    fun `equal config times keep candidate order`() {
        ini("$INTERNAL/PSP/SYSTEM/ppsspp.ini", 100L)
        ini("$SD/PSP/SYSTEM/ppsspp.ini", 100L)

        assertEquals("$SD/PSP/SAVEDATA", PpssppMemoryStick.liveSaveData(ppsspp, listOf("$SD/PSP/SAVEDATA", "$INTERNAL/PSP/SAVEDATA"), fal))
    }

    @Test
    fun `a layout other than standalone ppsspp never has a live stick`() {
        ini("$SD/PSP/SYSTEM/ppsspp.ini", 100L)

        assertNull(PpssppMemoryStick.liveSaveData(dolphin, listOf("$SD/PSP/SAVEDATA"), fal))
    }

    private companion object {
        const val INTERNAL = "/storage/emulated/0"
        const val SD = "/storage/75D7-DC5F"
    }
}
