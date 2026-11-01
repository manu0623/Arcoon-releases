package com.nendo.argosy.data.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ServerRomLayoutTest {

    @Test
    fun `a file beside the base rom has no relative dir`() {
        val paths = listOf("roms/gc/Baten Kaitos", "roms/gc/Baten Kaitos")

        assertNull(ServerRomLayout.relativeDir("roms/gc/Baten Kaitos", paths))
    }

    @Test
    fun `a nested file keeps its own subdirectory`() {
        val paths = listOf("roms/switch/Game", "roms/switch/Game/update")

        assertEquals("update", ServerRomLayout.relativeDir("roms/switch/Game/update", paths))
    }

    @Test
    fun `subdirectories survive when no file sits at the rom root`() {
        val paths = listOf("roms/switch/Game/dlc", "roms/switch/Game/update1")

        assertEquals("dlc", ServerRomLayout.relativeDir("roms/switch/Game/dlc", paths))
        assertEquals("update1", ServerRomLayout.relativeDir("roms/switch/Game/update1", paths))
    }

    @Test
    fun `a shorter sibling directory does not become the root`() {
        val paths = listOf("roms/ps3/Game/PS3_GAME", "roms/ps3/Game/USRDIR/x")

        assertEquals("PS3_GAME", ServerRomLayout.relativeDir("roms/ps3/Game/PS3_GAME", paths))
        assertEquals("USRDIR/x", ServerRomLayout.relativeDir("roms/ps3/Game/USRDIR/x", paths))
    }

    @Test
    fun `a base file left flat beside the rom folder does not nest the folder inside itself`() {
        val paths = listOf("roms/nes", "roms/nes/720 Degrees (USA)/soundtrack")

        assertEquals(
            "soundtrack",
            ServerRomLayout.relativeDir(
                "roms/nes/720 Degrees (USA)/soundtrack",
                paths,
                romFolderNames = listOf("720 Degrees (USA).nes", "720 Degrees (USA)")
            )
        )
        assertNull(
            ServerRomLayout.relativeDir(
                "roms/nes",
                paths,
                romFolderNames = listOf("720 Degrees (USA).nes", "720 Degrees (USA)")
            )
        )
    }

    @Test
    fun `parent segments cannot climb above the root`() {
        val paths = listOf("roms/gc/Game", "roms/gc/Game/../../etc/x")

        assertEquals("etc/x", ServerRomLayout.relativeDir("roms/gc/Game/../../etc/x", paths))
        assertEquals(
            "x",
            ServerRomLayout.relativeDir(
                "roms/nes/Game/../../x",
                emptyList(),
                romFolderNames = listOf("Game")
            )
        )
    }

    @Test
    fun `a path made only of parent segments has no relative dir`() {
        val paths = listOf("../..", "../..")

        assertNull(ServerRomLayout.relativeDir("../..", paths))
        assertNull(ServerRomLayout.relativeDir("./.", listOf("./.")))
    }

    @Test
    fun `a deeper tree keeps every segment below the root`() {
        val paths = listOf("roms/ps3/Game", "roms/ps3/Game/PS3_GAME/USRDIR")

        assertEquals(
            "PS3_GAME/USRDIR",
            ServerRomLayout.relativeDir("roms/ps3/Game/PS3_GAME/USRDIR", paths)
        )
    }
}
