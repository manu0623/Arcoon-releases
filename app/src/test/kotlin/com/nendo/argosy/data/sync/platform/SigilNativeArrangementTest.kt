package com.nendo.argosy.data.sync.platform

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SigilNativeArrangementTest {

    private val folderCardZip = mapOf(
        "BASLUS-20642SYS/BASLUS-20642SYS" to byteArrayOf(1, 2),
        "BASLUS-20642SYS/icon.sys" to byteArrayOf(3),
        "BASLUS-20642RD0/BASLUS-20642RD0" to byteArrayOf(4)
    )

    private fun arrangements(layout: String, options: Map<String, String>, files: Map<String, ByteArray>) =
        SigilSaveHandler.nativeArrangements(layout, listOf("memcards"), options, files)

    @Test
    fun `a PCSX2 folder card save from a standalone emulator is laid out on a formatted card under memcards`() {
        val first = arrangements("pcsx2_standalone", emptyMap(), folderCardZip).first()

        assertEquals(
            setOf(
                "memcards/Mcd001.ps2/_pcsx2_superblock",
                "memcards/Mcd001.ps2/BASLUS-20642SYS/BASLUS-20642SYS",
                "memcards/Mcd001.ps2/BASLUS-20642SYS/icon.sys",
                "memcards/Mcd001.ps2/BASLUS-20642RD0/BASLUS-20642RD0"
            ),
            first.files.keys
        )
        assertTrue(Ps2FolderCardSuperblock.isFormatted(first.files["memcards/Mcd001.ps2/_pcsx2_superblock"]))
        assertArrayEquals(byteArrayOf(1, 2), first.files["memcards/Mcd001.ps2/BASLUS-20642SYS/BASLUS-20642SYS"])
        assertEquals(mapOf("Slot1_Filename" to "Mcd001.ps2"), first.options)
        assertEquals("pcsx2_standalone", first.layout)
    }

    @Test
    fun `the card takes the name of the memory card the source emulator has selected`() {
        val first = arrangements("pcsx2_standalone", mapOf("Slot1_Filename" to "Norrath.ps2"), folderCardZip).first()

        assertTrue(first.files.keys.all { it.startsWith("memcards/Norrath.ps2/") })
        assertEquals("Norrath.ps2", first.options["Slot1_Filename"])
    }

    @Test
    fun `an archive rooted at the card is re-rooted at the card collect reads and keeps a formatted superblock`() {
        val superblock = Ps2FolderCardSuperblock.formatted8Mb().also { it[0x100] = 0x11 }
        val cardRooted = mapOf(
            "Shared.ps2/_pcsx2_superblock" to superblock,
            "Shared.ps2/BASLUS-20642SYS/icon.sys" to byteArrayOf(3)
        )

        val first = arrangements("pcsx2_standalone", emptyMap(), cardRooted).first()

        assertEquals(
            setOf("memcards/Mcd001.ps2/_pcsx2_superblock", "memcards/Mcd001.ps2/BASLUS-20642SYS/icon.sys"),
            first.files.keys
        )
        assertArrayEquals(superblock, first.files["memcards/Mcd001.ps2/_pcsx2_superblock"])
    }

    @Test
    fun `folder-card saves cached under the built-in core are collected as the standalone folder card they were written as`() {
        val first = arrangements("pcsx2", emptyMap(), folderCardZip).first()

        assertEquals("pcsx2_standalone", first.layout)
        assertEquals(
            setOf(
                "memcards/Mcd001.ps2/_pcsx2_superblock",
                "memcards/Mcd001.ps2/BASLUS-20642SYS/BASLUS-20642SYS",
                "memcards/Mcd001.ps2/BASLUS-20642SYS/icon.sys",
                "memcards/Mcd001.ps2/BASLUS-20642RD0/BASLUS-20642RD0"
            ),
            first.files.keys
        )
        assertTrue(Ps2FolderCardSuperblock.isFormatted(first.files["memcards/Mcd001.ps2/_pcsx2_superblock"]))
        assertEquals(mapOf("Slot1_Filename" to "Mcd001.ps2"), first.options)
    }

    @Test
    fun `no arrangement lays a folder card out under the built-in layout, which keeps file cards only`() {
        for (options in listOf(emptyMap<String, String>(), mapOf("pcsx2_shared_memory_cards" to "disabled"))) {
            val all = arrangements("pcsx2", options, folderCardZip)

            assertTrue(all.none { it.layout == "pcsx2" && it.files.keys.any { key -> key.substringBefore('/').endsWith(".ps2") } })
            assertEquals("pcsx2_standalone", all.first().layout)
            assertEquals(listOf(folderCardZip.keys, folderCardZip.keys.map { "memcards/$it" }.toSet()), all.drop(1).map { it.files.keys })
            assertTrue(all.drop(1).all { it.layout == "pcsx2" && it.options == options })
        }
    }

    @Test
    fun `the files are still tried as they are, at the root and under each subfolder`() {
        val all = arrangements("pcsx2_standalone", emptyMap(), folderCardZip)

        assertEquals(3, all.size)
        assertEquals(folderCardZip.keys, all[1].files.keys)
        assertEquals(folderCardZip.keys.map { "memcards/$it" }.toSet(), all[2].files.keys)
        assertEquals(emptyMap<String, String>(), all[1].options)
        assertTrue(all.all { it.layout == "pcsx2_standalone" })
    }

    @Test
    fun `a built-in file card is collected as the file card it is, under the built-in layout`() {
        val fileCard = mapOf("Mcd001.ps2" to ByteArray(16))

        val all = arrangements("pcsx2", emptyMap(), fileCard)

        assertEquals(listOf(setOf("Mcd001.ps2"), setOf("memcards/Mcd001.ps2")), all.map { it.files.keys })
        assertEquals("pcsx2", all.first().layout)
    }

    @Test
    fun `a single file card or a layout without folder cards gets no card arrangement`() {
        val fileCard = mapOf("Mcd001.ps2" to ByteArray(16))
        assertEquals(2, arrangements("pcsx2_standalone", emptyMap(), fileCard).size)

        val psp = mapOf("ULUS10336DATA00/DATA.BIN" to byteArrayOf(1))
        val pspArrangements = SigilSaveHandler.nativeArrangements("ppsspp_standalone", listOf("PSP/SAVEDATA"), emptyMap(), psp)
        assertEquals(listOf(psp.keys, setOf("PSP/SAVEDATA/ULUS10336DATA00/DATA.BIN")), pspArrangements.map { it.files.keys })
    }
}
