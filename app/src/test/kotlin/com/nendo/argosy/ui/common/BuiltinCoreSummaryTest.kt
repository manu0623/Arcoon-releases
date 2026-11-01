package com.nendo.argosy.ui.common

import com.nendo.argosy.data.emulator.BuiltinCoreResolver
import com.nendo.argosy.data.repository.CoreVersionRepository
import com.nendo.argosy.ui.screens.settings.sections.formatCoreBuildStamp
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneOffset
import java.util.Locale

class BuiltinCoreSummaryTest {

    private val stamp = "Sun, 11 Oct 2026 03:09:01 GMT"
    private val joined: (String, String) -> String = { coreName, build -> "$coreName|$build" }

    @Test
    fun `a downloaded core shows its name and formatted build`() {
        val subtitle = BuiltinCoreSummary("mGBA", stamp).subtitle(ZoneOffset.UTC, Locale.US, joined)

        assertEquals("mGBA|" + formatCoreBuildStamp(stamp, ZoneOffset.UTC, Locale.US), subtitle)
    }

    @Test
    fun `a core with no recorded build shows its name alone`() {
        assertEquals("mGBA", BuiltinCoreSummary("mGBA", null).subtitle(ZoneOffset.UTC, Locale.US, joined))
        assertEquals("mGBA", BuiltinCoreSummary("mGBA", " ").subtitle(ZoneOffset.UTC, Locale.US, joined))
    }

    @Test
    fun `a build stamp that is not a date shows the name alone`() {
        assertEquals("mGBA", BuiltinCoreSummary("mGBA", "52428800").subtitle(ZoneOffset.UTC, Locale.US, joined))
    }

    @Test
    fun `the core name comes from the libretro registry`() {
        assertEquals(BuiltinCoreSummary("mGBA", stamp), builtinCoreSummaryOf("mgba", stamp))
    }

    @Test
    fun `the loader reports the resolved core and its installed build`() = runBlocking {
        val resolver = mockk<BuiltinCoreResolver>()
        val versions = mockk<CoreVersionRepository>()
        coEvery { resolver.resolveCoreId(9L, 2L, "gba") } returns "mgba"
        coEvery { versions.installedBuild("mgba") } returns stamp

        val summary = BuiltinCoreSummaryLoader(resolver, versions).load(9L, 2L, "gba")

        assertEquals(BuiltinCoreSummary("mGBA", stamp), summary)
    }

    @Test
    fun `the loader reports a core that was never downloaded without a build`() = runBlocking {
        val resolver = mockk<BuiltinCoreResolver>()
        val versions = mockk<CoreVersionRepository>()
        coEvery { resolver.resolveCoreId(null, 2L, "gba") } returns "mgba"
        coEvery { versions.installedBuild("mgba") } returns null

        val summary = BuiltinCoreSummaryLoader(resolver, versions).load(null, 2L, "gba")

        assertEquals(BuiltinCoreSummary("mGBA", null), summary)
    }

    @Test
    fun `the loader reports nothing when no core resolves`() = runBlocking {
        val resolver = mockk<BuiltinCoreResolver>()
        coEvery { resolver.resolveCoreId(null, 2L, "gba") } returns null

        assertNull(BuiltinCoreSummaryLoader(resolver, mockk()).load(null, 2L, "gba"))
    }
}
