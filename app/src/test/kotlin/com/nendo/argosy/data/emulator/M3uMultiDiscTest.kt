package com.nendo.argosy.data.emulator

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class M3uMultiDiscTest {

    @get:Rule
    val tempDir = TemporaryFolder()

    private fun playlist(vararg discs: String): File {
        val dir = tempDir.newFolder()
        discs.forEach { File(dir, it).writeText("x") }
        return File(dir, "Game.m3u").apply { writeText(discs.joinToString("\n")) }
    }

    @Test
    fun `compressed disc images count as discs`() {
        assertTrue(M3uManager.isMultiDiscContainer(playlist("Game (Disc 1).cso", "Game (Disc 2).cso")))
        assertTrue(M3uManager.isMultiDiscContainer(playlist("Game (Disc 1).zso", "Game (Disc 2).zso")))
        assertTrue(M3uManager.isMultiDiscContainer(playlist("Game (Disc 1).rvz", "Game (Disc 2).rvz")))
        assertTrue(M3uManager.isMultiDiscContainer(playlist("Game (Disc 1).pbp", "Game (Disc 2).pbp")))
    }

    @Test
    fun `one disc is not a multi-disc set`() {
        assertFalse(M3uManager.isMultiDiscContainer(playlist("Game.cso")))
    }

    @Test
    fun `non-disc entries do not make a set`() {
        assertFalse(M3uManager.isMultiDiscContainer(playlist("Game.cso", "Game.sbi")))
    }
}
