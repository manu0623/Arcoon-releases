package com.nendo.argosy.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ScreenKeyTest {

    @Test
    fun `an external screen keeps its key across hotplugs that change its display id`() {
        assertEquals(
            ScreenCatalog.fallbackScreenKey(6, "DP Screen", 2560, 1440, builtIn = false),
            ScreenCatalog.fallbackScreenKey(7, "DP Screen", 2560, 1440, builtIn = false)
        )
    }

    @Test
    fun `an external key ignores orientation`() {
        assertEquals(
            ScreenCatalog.fallbackScreenKey(5, "DP Screen", 1440, 2560, builtIn = false),
            ScreenCatalog.fallbackScreenKey(5, "DP Screen", 2560, 1440, builtIn = false)
        )
    }

    @Test
    fun `built-in panels stay keyed by display id`() {
        assertEquals("display:0:1080x1920", ScreenCatalog.fallbackScreenKey(0, "Built-in Screen", 1920, 1080, builtIn = true))
        assertNotEquals(
            ScreenCatalog.fallbackScreenKey(0, "Built-in Screen", 1920, 1080, builtIn = true),
            ScreenCatalog.fallbackScreenKey(4, "Built-in Screen", 1920, 1080, builtIn = true)
        )
    }

    @Test
    fun `external screens of different sizes get different keys`() {
        assertNotEquals(
            ScreenCatalog.fallbackScreenKey(5, "DP Screen", 2560, 1440, builtIn = false),
            ScreenCatalog.fallbackScreenKey(5, "DP Screen", 1920, 1080, builtIn = false)
        )
    }
}
