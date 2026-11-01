package com.nendo.argosy.data.iconpack

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppFilterTest {

    private val drawableIds = mapOf(
        "ic_browser" to 11,
        "ic_browser_alt" to 12,
        "ic_camera" to 21,
        "ic_relative" to 31
    )

    private fun idOf(name: String): Int = drawableIds[name] ?: 0

    private fun entry(component: String?, drawable: String?) = AppFilterEntry(component, drawable)

    @Test
    fun `a well-formed entry resolves its drawable for the launch component`() {
        val filter = AppFilter.fromEntries(
            listOf(entry("ComponentInfo{org.example.browser/org.example.browser.Main}", "ic_browser"))
        )

        assertEquals(11, filter.resolve("org.example.browser", "org.example.browser.Main", ::idOf))
    }

    @Test
    fun `a class written relative to its package resolves from either form`() {
        val filter = AppFilter.fromEntries(
            listOf(entry("ComponentInfo{org.example.rel/.ui.Home}", "ic_relative"))
        )

        assertEquals(31, filter.resolve("org.example.rel", "org.example.rel.ui.Home", ::idOf))
        assertEquals(31, filter.resolve("org.example.rel", ".ui.Home", ::idOf))
    }

    @Test
    fun `surrounding whitespace in component and drawable is ignored`() {
        val filter = AppFilter.fromEntries(
            listOf(entry("  ComponentInfo{ org.example.cam / org.example.cam.Shoot }  ", " ic_camera "))
        )

        assertEquals(21, filter.resolve("org.example.cam", "org.example.cam.Shoot", ::idOf))
    }

    @Test
    fun `malformed entries are dropped without affecting valid ones`() {
        val filter = AppFilter.fromEntries(
            listOf(
                entry(null, "ic_browser"),
                entry("ComponentInfo{org.example.a/org.example.a.Main}", null),
                entry("ComponentInfo{org.example.b/org.example.b.Main}", "   "),
                entry("org.example.c/org.example.c.Main", "ic_browser"),
                entry("ComponentInfo{org.example.d}", "ic_browser"),
                entry("ComponentInfo{/org.example.e.Main}", "ic_browser"),
                entry("ComponentInfo{org.example.f/}", "ic_browser"),
                entry("ComponentInfo{org.example.g/.}", "ic_browser"),
                entry("ComponentInfo{org.example.h/org.example.h.Main", "ic_browser"),
                entry("ComponentInfo{org.example.cam/org.example.cam.Shoot}", "ic_camera")
            )
        )

        listOf("a", "b", "c", "d", "e", "f", "g", "h").forEach { suffix ->
            val pkg = "org.example.$suffix"
            assertNull(pkg, filter.resolve(pkg, "$pkg.Main", ::idOf))
        }
        assertEquals(21, filter.resolve("org.example.cam", "org.example.cam.Shoot", ::idOf))
    }

    @Test
    fun `an appfilter with only malformed entries is empty`() {
        val filter = AppFilter.fromEntries(listOf(entry("not a component", "ic_browser")))

        assertTrue(filter.isEmpty)
    }

    @Test
    fun `a drawable the pack does not contain resolves to nothing`() {
        val filter = AppFilter.fromEntries(
            listOf(entry("ComponentInfo{org.example.browser/org.example.browser.Main}", "ic_missing"))
        )

        assertNull(filter.resolve("org.example.browser", "org.example.browser.Main", ::idOf))
    }

    @Test
    fun `a missing drawable for the launch component falls back to the package entry`() {
        val filter = AppFilter.fromEntries(
            listOf(
                entry("ComponentInfo{org.example.browser/org.example.browser.Old}", "ic_browser"),
                entry("ComponentInfo{org.example.browser/org.example.browser.Main}", "ic_missing")
            )
        )

        assertEquals(11, filter.resolve("org.example.browser", "org.example.browser.Main", ::idOf))
    }

    @Test
    fun `an unmapped launch activity falls back to the first entry for its package`() {
        val filter = AppFilter.fromEntries(
            listOf(
                entry("ComponentInfo{org.example.browser/org.example.browser.Legacy}", "ic_browser"),
                entry("ComponentInfo{org.example.browser/org.example.browser.Other}", "ic_browser_alt")
            )
        )

        assertEquals(11, filter.resolve("org.example.browser", "org.example.browser.NewMain", ::idOf))
        assertEquals(11, filter.resolve("org.example.browser", null, ::idOf))
    }

    @Test
    fun `the exact component wins over the package fallback whichever comes first`() {
        val exactFirst = AppFilter.fromEntries(
            listOf(
                entry("ComponentInfo{org.example.browser/org.example.browser.Main}", "ic_browser_alt"),
                entry("ComponentInfo{org.example.browser/org.example.browser.Legacy}", "ic_browser")
            )
        )
        val exactSecond = AppFilter.fromEntries(
            listOf(
                entry("ComponentInfo{org.example.browser/org.example.browser.Legacy}", "ic_browser"),
                entry("ComponentInfo{org.example.browser/org.example.browser.Main}", "ic_browser_alt")
            )
        )

        assertEquals(12, exactFirst.resolve("org.example.browser", "org.example.browser.Main", ::idOf))
        assertEquals(12, exactSecond.resolve("org.example.browser", "org.example.browser.Main", ::idOf))
    }

    @Test
    fun `a duplicated component keeps its first drawable`() {
        val filter = AppFilter.fromEntries(
            listOf(
                entry("ComponentInfo{org.example.browser/org.example.browser.Main}", "ic_browser"),
                entry("ComponentInfo{org.example.browser/org.example.browser.Main}", "ic_browser_alt")
            )
        )

        assertEquals(11, filter.resolve("org.example.browser", "org.example.browser.Main", ::idOf))
    }

    @Test
    fun `an app the pack does not cover keeps its own icon`() {
        val filter = AppFilter.fromEntries(
            listOf(entry("ComponentInfo{org.example.browser/org.example.browser.Main}", "ic_browser"))
        )

        assertNull(filter.resolve("org.example.unknown", "org.example.unknown.Main", ::idOf))
        assertNull(AppFilter.EMPTY.resolve("org.example.browser", "org.example.browser.Main", ::idOf))
    }
}
