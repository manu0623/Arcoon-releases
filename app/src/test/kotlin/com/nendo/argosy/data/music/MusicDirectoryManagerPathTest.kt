package com.nendo.argosy.data.music

import android.content.Context
import com.nendo.argosy.data.preferences.StoragePreferencesRepository
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class MusicDirectoryManagerPathTest {

    private val manager = MusicDirectoryManager(
        mockk<Context>(relaxed = true),
        mockk<StoragePreferencesRepository>(relaxed = true)
    )
    private val musicDir = File("/music")

    private fun staysInside(file: File) =
        file.toPath().normalize().startsWith(musicDir.toPath())

    @Test
    fun `parent folder names from the server stay inside the music folder`() {
        val target = manager.targetFileIn(musicDir, "..", "..", null, null, "x.mp3")

        assertTrue(staysInside(target))
        assertEquals("/music/track/track/x.mp3", target.path)
    }

    @Test
    fun `an extension carrying separators stays inside the game folder`() {
        val target = manager.targetFileIn(musicDir, "Wii", "Wii Shop", 1, "Shop", "a./../../../evil")

        assertTrue(staysInside(target))
        assertEquals(File("/music/Wii/Wii Shop"), target.parentFile)
    }

    @Test
    fun `ordinary names are unchanged`() {
        val target = manager.targetFileIn(musicDir, "Wii", "Wii Shop", 5, "Shop Channel", "shop.mp3")

        assertEquals("/music/Wii/Wii Shop/05 - Shop Channel.mp3", target.path)
    }
}
