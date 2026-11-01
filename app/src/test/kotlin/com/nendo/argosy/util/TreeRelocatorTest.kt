package com.nendo.argosy.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class TreeRelocatorTest {

    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun `a folder that cannot be listed is reported as left behind`() {
        val source = temp.newFolder("library")
        val locked = File(source, "locked").apply { mkdirs() }
        File(locked, "track.mp3").writeText("a")
        locked.setReadable(false)
        try {
            val result = TreeRelocator.relocate(source, File(temp.root, "moved"))

            assertTrue(locked.absolutePath in result.failed)
        } finally {
            locked.setReadable(true)
        }
    }

    @Test
    fun `a destination inside the source is never walked into`() {
        val source = temp.newFolder("music")
        File(source, "a/track.mp3").apply { parentFile!!.mkdirs(); writeText("a") }
        val destination = File(source, "moved")

        val result = TreeRelocator.relocate(source, destination)

        assertEquals(listOf(File(destination, "a/track.mp3").absolutePath), result.moved)
        assertTrue(result.failed.isEmpty())
        assertEquals("a", File(destination, "a/track.mp3").readText())
    }

    @Test
    fun `a name already taken at the destination is renamed, not overwritten`() {
        val source = temp.newFolder("src")
        val destination = temp.newFolder("dst")
        File(source, "track.mp3").writeText("new")
        File(destination, "track.mp3").writeText("mine")

        TreeRelocator.relocate(source, destination)

        assertEquals("new", File(destination, "track.mp3").readText())
        assertEquals("mine", File(destination, "track (1).mp3").readText())
    }
}
