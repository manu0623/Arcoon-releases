package com.nendo.argosy.data.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class FrontendSyncMarkersTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun dir(vararg files: Pair<String, String>): File {
        val root = tempFolder.newFolder()
        files.forEach { (name, content) -> File(root, name).writeText(content) }
        return root
    }

    private fun read(root: File) =
        FrontendSyncMarkers.read(root, GameNativeSyncFolder.DROIDDECK)!!.sortedBy { it.first }

    @Test
    fun `a droiddeck marker yields its steam app id and title`() {
        val root = dir("Portal 2.steam" to "620")

        assertEquals(listOf(620 to "Portal 2"), read(root))
    }

    @Test
    fun `surrounding whitespace in a marker is ignored`() {
        val root = dir("Half-Life.steam" to "  70\n")

        assertEquals(listOf(70 to "Half-Life"), read(root))
    }

    @Test
    fun `an added exe id past the 32-bit range is skipped`() {
        val root = dir(
            "My Emulator.steam" to "9223372036854775807",
            "Other Exe.steam" to "17293822569102704640",
            "Portal.steam" to "400"
        )

        assertEquals(listOf(400 to "Portal"), read(root))
    }

    @Test
    fun `a marker over 128 bytes is skipped even when its trimmed content is an id`() {
        val root = dir(
            "Padded.steam" to "620" + " ".repeat(200),
            "Portal.steam" to "400"
        )

        assertEquals(listOf(400 to "Portal"), read(root))
    }

    @Test
    fun `garbage content is skipped`() {
        val root = dir(
            "Empty.steam" to "",
            "Zero.steam" to "0",
            "Leading Zero.steam" to "0620",
            "Negative.steam" to "-620",
            "Signed.steam" to "+620",
            "Words.steam" to "app 620",
            "Two Lines.steam" to "620\n400",
            "Portal.steam" to "400"
        )

        assertEquals(listOf(400 to "Portal"), read(root))
    }

    @Test
    fun `the inventory json and the images folder are ignored`() {
        val root = dir(
            ".droiddeck-sync.json" to "{\"games\":[620]}",
            "Portal.steam" to "400"
        )
        val images = File(root, "images").apply { mkdirs() }
        File(images, "Portal.steam").writeText("400")
        File(images, "Portal.png").writeBytes(byteArrayOf(1, 2, 3))
        File(root, "Folder.steam").mkdirs()

        assertEquals(listOf(400 to "Portal"), read(root))
    }

    @Test
    fun `disambiguated titles each keep their own id`() {
        val root = dir(
            "Portal (400).steam" to "400",
            "Portal (620).steam" to "620"
        )

        assertEquals(listOf(400 to "Portal (400)", 620 to "Portal (620)"), read(root))
    }

    @Test
    fun `an unlistable folder reads as null`() {
        val missing = File(tempFolder.root, "missing")

        assertNull(FrontendSyncMarkers.read(missing, GameNativeSyncFolder.DROIDDECK))
    }

    @Test
    fun `parseId accepts the largest 32-bit id and rejects one past it`() {
        assertEquals(Int.MAX_VALUE, FrontendSyncMarkers.parseId(Int.MAX_VALUE.toString()))
        assertNull(FrontendSyncMarkers.parseId((Int.MAX_VALUE.toLong() + 1).toString()))
    }
}
