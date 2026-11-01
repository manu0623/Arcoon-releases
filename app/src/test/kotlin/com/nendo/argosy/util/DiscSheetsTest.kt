package com.nendo.argosy.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DiscSheetsTest {

    @get:Rule
    val tempDir = TemporaryFolder()

    private val psxExtensions = setOf("bin", "iso", "img", "chd", "pbp", "cue", "ecm", "mdf", "mds", "toc", "zip", "7z")

    private fun touch(dir: File, name: String, text: String = "x") = File(dir, name).apply { writeText(text) }

    private fun names(files: List<File>) = files.map { it.name }.toSet()

    @Test
    fun `a cue's companions are the tracks it lists plus same-stem roms`() {
        val dir = tempDir.newFolder("psx")
        val cue = touch(
            dir,
            "Game (USA).cue",
            "FILE \"Game (USA) (Track 1).bin\" BINARY\n  TRACK 01 MODE2/2352\nFILE \"Game (USA) (Track 2).bin\" BINARY\n"
        )
        touch(dir, "Game (USA) (Track 1).bin")
        touch(dir, "Game (USA) (Track 2).bin")
        touch(dir, "Game (USA).chd")

        assertEquals(
            setOf("Game (USA) (Track 1).bin", "Game (USA) (Track 2).bin", "Game (USA).chd"),
            names(DiscSheets.companions(cue, psxExtensions))
        )
    }

    @Test
    fun `a gdi's companions are the tracks it lists, quoted or bare`() {
        val dir = tempDir.newFolder("dc")
        val gdi = touch(
            dir,
            "Game (USA).gdi",
            "3\r\n" +
                "1 0 4 2352 \"Game (USA) (Track 1).bin\" 0\r\n" +
                "2 450 0 2352 \"Game (USA) (Track 2).bin\" 0\r\n" +
                "3 45000 4 2352 track03.bin 0\r\n"
        )
        touch(dir, "Game (USA) (Track 1).bin")
        touch(dir, "Game (USA) (Track 2).bin")
        touch(dir, "track03.bin")
        touch(dir, "Game (USA).A1.bin")

        assertEquals(
            setOf("Game (USA) (Track 1).bin", "Game (USA) (Track 2).bin", "track03.bin"),
            names(DiscSheets.companions(gdi, setOf("gdi", "cdi", "chd", "bin", "cue", "zip", "7z")))
        )
    }

    @Test
    fun `saves and other games beside the sheet are never companions`() {
        val dir = tempDir.newFolder("psx")
        val cue = touch(dir, "Game.cue", "FILE \"Game.bin\" BINARY\n")
        touch(dir, "Game.bin")
        touch(dir, "Game.srm")
        touch(dir, "Game.sav")
        touch(dir, "Other Game.bin")

        assertEquals(setOf("Game.bin"), names(DiscSheets.companions(cue, psxExtensions)))
    }

    @Test
    fun `a track path climbing out of the sheet's folder is ignored`() {
        val root = tempDir.newFolder("Games")
        val dir = File(root, "psx").apply { mkdirs() }
        touch(root, "victim.bin")
        val cue = touch(dir, "Game.cue", "FILE \"../victim.bin\" BINARY\n")

        assertTrue(DiscSheets.companions(cue, psxExtensions).isEmpty())
    }

    @Test
    fun `a listed track that is missing is skipped`() {
        val dir = tempDir.newFolder("psx")
        val cue = touch(dir, "Game.cue", "FILE \"Gone.bin\" BINARY\n")

        assertTrue(DiscSheets.companions(cue, psxExtensions).isEmpty())
    }

    @Test
    fun `a non-cue sheet takes only its same-stem roms`() {
        val dir = tempDir.newFolder("psx")
        val mds = touch(dir, "Game.mds")
        touch(dir, "Game.mdf")
        touch(dir, "Game.sub")

        assertEquals(setOf("Game.mdf"), names(DiscSheets.companions(mds, psxExtensions)))
    }

    private val saturnExtensions = setOf("iso", "bin", "cue", "chd", "ccd", "toc", "mds", "zip", "7z")

    @Test
    fun `a ccd takes its img and sub even where the platform lists neither`() {
        val dir = tempDir.newFolder("saturn")
        val ccd = touch(dir, "Game.ccd")
        touch(dir, "Game.img")
        touch(dir, "Game.sub")
        touch(dir, "Other.sub")
        touch(dir, "Game.srm")

        assertEquals(setOf("Game.img", "Game.sub"), names(DiscSheets.companions(ccd, saturnExtensions)))
    }

    @Test
    fun `an mds takes its mdf, a cue its sbi`() {
        val dir = tempDir.newFolder("saturn")
        val mds = touch(dir, "Game.mds")
        touch(dir, "Game.mdf")
        assertEquals(setOf("Game.mdf"), names(DiscSheets.companions(mds, saturnExtensions)))

        val cue = touch(dir, "Disc.cue", "FILE \"Disc.bin\" BINARY\n")
        touch(dir, "Disc.bin")
        touch(dir, "Disc.sbi")
        assertEquals(setOf("Disc.bin", "Disc.sbi"), names(DiscSheets.companions(cue, psxExtensions)))
    }

    @Test
    fun `a toc's companions are the files it lists`() {
        val dir = tempDir.newFolder("saturn")
        val toc = touch(
            dir,
            "Game.toc",
            "CD_ROM\nTRACK MODE1_RAW\nDATAFILE \"data.raw\"\nTRACK AUDIO\nFILE \"audio.raw\" 0 03:00:00\n" +
                "TRACK AUDIO\nAUDIOFILE \"audio.raw\" 03:00:00 01:00:00\n"
        )
        touch(dir, "data.raw")
        touch(dir, "audio.raw")
        touch(dir, "unrelated.raw")

        assertEquals(setOf("data.raw", "audio.raw"), names(DiscSheets.companions(toc, saturnExtensions)))
    }

    @Test
    fun `only sheet extensions count as loaders`() {
        assertTrue(DiscSheets.isLoader("Game.CUE"))
        assertTrue(DiscSheets.isLoader("Game.gdi"))
        assertFalse(DiscSheets.isLoader("Game.bin"))
        assertFalse(DiscSheets.isLoader("cue"))
    }
}
