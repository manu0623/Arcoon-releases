package com.nendo.argosy.data.remote.romm

import org.junit.Assert.assertEquals
import org.junit.Test

class RomMClearLogoUrlsTest {

    private fun rom(images: List<RomMLaunchboxImage>?) = RomMRom(
        id = 42L,
        platformId = 1L,
        platformSlug = "switch",
        name = "Luigi's Mansion 3",
        slug = "lm3",
        fileName = "lm3.nsp",
        filePath = "/roms/switch/lm3.nsp",
        igdbId = null,
        mobyId = null,
        summary = null,
        coverSmall = null,
        coverLarge = null,
        regions = null,
        languages = null,
        revision = null,
        crcHash = null,
        md5Hash = null,
        sha1Hash = null,
        launchboxMetadata = images?.let { RomMLaunchboxMetadata(it) }
    )

    @Test
    fun `only clear logo images are taken`() {
        val result = rom(
            listOf(
                RomMLaunchboxImage("https://cdn/logo.png", "Clear Logo"),
                RomMLaunchboxImage("https://cdn/fan.jpg", "Fanart - Background"),
                RomMLaunchboxImage("https://cdn/shot.jpg", "Screenshot - Gameplay")
            )
        ).clearLogoUrls

        assertEquals(listOf("https://cdn/logo.png"), result)
    }

    @Test
    fun `a local launchbox url the client cannot fetch is dropped`() {
        val result = rom(
            listOf(
                RomMLaunchboxImage("launchbox-file://Images/Switch/Clear Logo/lm3.png", "Clear Logo"),
                RomMLaunchboxImage("http://cdn/logo.png", "Clear Logo")
            )
        ).clearLogoUrls

        assertEquals(listOf("http://cdn/logo.png"), result)
    }

    @Test
    fun `no launchbox metadata means no logo`() {
        assertEquals(emptyList<String>(), rom(null).clearLogoUrls)
    }
}
