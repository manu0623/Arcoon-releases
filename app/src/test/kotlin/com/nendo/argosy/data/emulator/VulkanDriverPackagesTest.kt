package com.nendo.argosy.data.emulator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class VulkanDriverPackagesTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val meta = """
        {"schemaVersion": 1, "name": "Turnip", "description": "Mesa Turnip",
         "author": "x", "packageVersion": "1", "vendor": "Mesa",
         "driverVersion": "25.2.0", "minApi": 28, "libraryName": "libvulkan_freedreno.so"}
    """.trimIndent()

    private fun zip(name: String, entries: Map<String, String>): File {
        val file = File(temp.root, name)
        ZipOutputStream(file.outputStream()).use { out ->
            entries.forEach { (path, content) ->
                out.putNextEntry(ZipEntry(path))
                out.write(content.toByteArray())
                out.closeEntry()
            }
        }
        return file
    }

    private fun driversRoot(): File = File(temp.root, "vulkan_drivers")

    @Test
    fun `meta parses the adrenotools fields`() {
        val parsed = VulkanDriverPackages.parseMeta(meta)!!
        assertEquals("Turnip", parsed.name)
        assertEquals("libvulkan_freedreno.so", parsed.libraryName)
        assertEquals("25.2.0", parsed.driverVersion)
        assertEquals("Mesa Turnip", parsed.description)
    }

    @Test
    fun `meta without a usable library name is rejected`() {
        assertNull(VulkanDriverPackages.parseMeta("""{"name": "x"}"""))
        assertNull(VulkanDriverPackages.parseMeta("""{"libraryName": "../libevil.so"}"""))
        assertNull(VulkanDriverPackages.parseMeta("""{"libraryName": "lib/x.so"}"""))
        assertNull(VulkanDriverPackages.parseMeta("""{"libraryName": null}"""))
        assertNull(VulkanDriverPackages.parseMeta("not json"))
    }

    @Test
    fun `meta falls back to the library name and leaves absent fields null`() {
        val parsed = VulkanDriverPackages.parseMeta("""{"libraryName": "libvk.so", "driverVersion": null}""")!!
        assertEquals("libvk.so", parsed.name)
        assertNull(parsed.driverVersion)
        assertNull(parsed.description)
    }

    @Test
    fun `package ids drop the archive suffixes and unsafe characters`() {
        assertEquals("turnip_25.2", VulkanDriverPackages.idForPackage("turnip_25.2.adpkg.zip"))
        assertEquals("Turnip_v1", VulkanDriverPackages.idForPackage("Turnip v1.ZIP"))
        assertEquals("driver", VulkanDriverPackages.idForPackage("...zip"))
    }

    @Test
    fun `a root level package installs under its id`() {
        val pkg = zip("turnip.adpkg.zip", mapOf("meta.json" to meta, "libvulkan_freedreno.so" to "elf"))

        val installed = VulkanDriverPackages.extract(pkg, driversRoot())

        assertEquals("turnip", installed.id)
        assertEquals("Turnip", installed.name)
        assertTrue(File(driversRoot(), "turnip/libvulkan_freedreno.so").isFile)
        assertEquals(listOf("turnip"), VulkanDriverPackages.listInstalled(driversRoot()).map { it.id })
        assertFalse(driversRoot().listFiles()!!.any { it.name.startsWith(".staging") })
    }

    @Test
    fun `a package wrapped in one folder installs its contents`() {
        val pkg = zip("wrapped.zip", mapOf("turnip/meta.json" to meta, "turnip/libvulkan_freedreno.so" to "elf"))

        val installed = VulkanDriverPackages.extract(pkg, driversRoot())

        assertEquals("wrapped", installed.id)
        assertTrue(File(driversRoot(), "wrapped/meta.json").isFile)
    }

    @Test
    fun `reinstalling replaces the previous files`() {
        val first = zip("turnip.zip", mapOf("meta.json" to meta, "libvulkan_freedreno.so" to "old", "stale.txt" to "x"))
        VulkanDriverPackages.extract(first, driversRoot())
        val second = zip("turnip.zip", mapOf("meta.json" to meta, "libvulkan_freedreno.so" to "new"))
        VulkanDriverPackages.extract(second, driversRoot())

        assertEquals("new", File(driversRoot(), "turnip/libvulkan_freedreno.so").readText())
        assertFalse(File(driversRoot(), "turnip/stale.txt").exists())
    }

    @Test
    fun `an entry escaping the package aborts the install and writes nothing outside`() {
        val pkg = zip(
            "evil.zip",
            mapOf("meta.json" to meta, "libvulkan_freedreno.so" to "elf", "../../escaped.txt" to "pwned")
        )

        assertThrows(IOException::class.java) { VulkanDriverPackages.extract(pkg, driversRoot()) }

        assertFalse(File(temp.root, "escaped.txt").exists())
        assertTrue(VulkanDriverPackages.listInstalled(driversRoot()).isEmpty())
    }

    @Test
    fun `a package missing the library it names is rejected`() {
        val pkg = zip("broken.zip", mapOf("meta.json" to meta))

        assertThrows(IOException::class.java) { VulkanDriverPackages.extract(pkg, driversRoot()) }

        assertTrue(VulkanDriverPackages.listInstalled(driversRoot()).isEmpty())
    }

    @Test
    fun `a package without meta json is rejected`() {
        val pkg = zip("bare.zip", mapOf("libvulkan_freedreno.so" to "elf"))

        assertThrows(IOException::class.java) { VulkanDriverPackages.extract(pkg, driversRoot()) }
    }

    @Test
    fun `a package that expands past the size limit is rejected and leaves the installed driver`() {
        VulkanDriverPackages.extract(
            zip("turnip.zip", mapOf("meta.json" to meta, "libvulkan_freedreno.so" to "old")),
            driversRoot()
        )
        val oversized = zip("turnip.zip", mapOf("meta.json" to meta, "libvulkan_freedreno.so" to "x".repeat(4096)))

        assertThrows(IOException::class.java) {
            VulkanDriverPackages.extract(oversized, driversRoot(), maxUncompressedBytes = 1024)
        }

        assertEquals("old", File(driversRoot(), "turnip/libvulkan_freedreno.so").readText())
    }

    @Test
    fun `a driver moved aside by an interrupted reinstall is restored`() {
        File(driversRoot(), ".previous-turnip").apply { mkdirs() }.also {
            File(it, "meta.json").writeText(meta)
            File(it, "libvulkan_freedreno.so").writeText("kept")
        }

        assertThrows(IOException::class.java) {
            VulkanDriverPackages.extract(zip("turnip.zip", mapOf("meta.json" to meta)), driversRoot())
        }

        assertEquals(listOf("turnip"), VulkanDriverPackages.listInstalled(driversRoot()).map { it.id })
        assertEquals("kept", File(driversRoot(), "turnip/libvulkan_freedreno.so").readText())
        assertFalse(File(driversRoot(), ".previous-turnip").exists())
    }

    @Test
    fun `a reinstall leaves no aside copy behind`() {
        VulkanDriverPackages.extract(zip("turnip.zip", mapOf("meta.json" to meta, "libvulkan_freedreno.so" to "a")), driversRoot())
        VulkanDriverPackages.extract(zip("turnip.zip", mapOf("meta.json" to meta, "libvulkan_freedreno.so" to "b")), driversRoot())

        assertFalse(driversRoot().listFiles()!!.any { it.name.startsWith(".") })
    }

    @Test
    fun `installed listing skips folders that are not drivers`() {
        File(driversRoot(), "empty").mkdirs()
        File(driversRoot(), "no_library").apply { mkdirs() }.resolve("meta.json").writeText(meta)

        assertTrue(VulkanDriverPackages.listInstalled(driversRoot()).isEmpty())
        assertTrue(VulkanDriverPackages.listInstalled(File(temp.root, "absent")).isEmpty())
    }
}
