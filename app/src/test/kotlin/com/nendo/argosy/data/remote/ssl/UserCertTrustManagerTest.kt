package com.nendo.argosy.data.remote.ssl

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class UserCertTrustManagerTest {

    private fun pinnedDomainsInConfig(): Set<String> {
        val roots = listOf(File("src/main/res/xml"), File("app/src/main/res/xml"))
        val config = roots.map { File(it, "network_security_config.xml") }.first { it.isFile }
        return Regex("<domain[^>]*>([^<]+)</domain>")
            .findAll(config.readText())
            .map { it.groupValues[1].trim() }
            .toSet()
    }

    @Test
    fun `the imported-cert deny list matches the network security config`() {
        assertEquals(pinnedDomainsInConfig(), UserCertTrustManager.SYSTEM_TRUST_DOMAINS)
    }

    @Test
    fun `a self-hosted server accepts an imported certificate`() {
        assertTrue(UserCertTrustManager.acceptsImportedCertFor("romm.home.lan"))
        assertTrue(UserCertTrustManager.acceptsImportedCertFor("192.168.1.20"))
    }

    @Test
    fun `pinned hosts and their subdomains refuse an imported certificate`() {
        assertFalse(UserCertTrustManager.acceptsImportedCertFor("buildbot.libretro.com"))
        assertFalse(UserCertTrustManager.acceptsImportedCertFor("objects.githubusercontent.com"))
        assertFalse(UserCertTrustManager.acceptsImportedCertFor("API.GitHub.com"))
        assertFalse(UserCertTrustManager.acceptsImportedCertFor("retroachievements.org"))
    }

    @Test
    fun `a host that only ends with a pinned name is not pinned`() {
        assertTrue(UserCertTrustManager.acceptsImportedCertFor("notgithub.com"))
    }

    @Test
    fun `an unknown host refuses an imported certificate`() {
        assertFalse(UserCertTrustManager.acceptsImportedCertFor(null))
        assertFalse(UserCertTrustManager.acceptsImportedCertFor(""))
    }
}
