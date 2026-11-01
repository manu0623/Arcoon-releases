package com.nendo.argosy.data.storage

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ArgosyFileProviderPathTest {

    private val storage = Files.createTempDirectory("storage").toFile()
    private val cache = Files.createTempDirectory("cache").toFile()
    private val roots = mapOf("storage" to storage, "cache" to cache)

    @Test
    fun `a storage uri resolves under the storage root, not the filesystem root`() {
        val rom = File(storage, "emulated/0/Games/ps2/Champions of Norrath (USA).chd")

        val resolved = ArgosyFileProvider.resolveProviderPath(
            "/storage/emulated/0/Games/ps2/Champions of Norrath (USA).chd",
            roots
        )

        assertEquals(rom.canonicalFile, resolved)
    }

    @Test
    fun `each root name maps to its own directory`() {
        assertEquals(File(cache, "art/a.png").canonicalFile, ArgosyFileProvider.resolveProviderPath("/cache/art/a.png", roots))
    }

    @Test
    fun `an unknown root or a bare root name resolves to nothing`() {
        assertNull(ArgosyFileProvider.resolveProviderPath("/root/etc/hosts", roots))
        assertNull(ArgosyFileProvider.resolveProviderPath("/storage", roots))
    }

    @Test
    fun `a path that climbs out of its root is refused`() {
        assertNull(ArgosyFileProvider.resolveProviderPath("/storage/../cache/secret", roots))
    }
}
