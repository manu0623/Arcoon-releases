package com.nendo.argosy.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.nendo.argosy.ui.theme.generated.ColorTokens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadableAccentTest {

    private fun contrast(a: Color, b: Color): Float {
        val la = a.luminance() + 0.05f
        val lb = b.luminance() + 0.05f
        return maxOf(la, lb) / minOf(la, lb)
    }

    @Test
    fun `a dark blue accent is lifted in dark mode`() {
        val blue = Color.hsl(230f, 0.7f, 0.5f)

        val adjusted = readableAccent(blue, isDarkTheme = true)

        assertTrue(adjusted.luminance() > blue.luminance())
        assertTrue(
            contrast(adjusted, ColorTokens.Scheme.Dark.surface) > contrast(blue, ColorTokens.Scheme.Dark.surface)
        )
    }

    @Test
    fun `a dark mode lift never passes seventy percent lightness`() {
        val violet = Color.hsl(260f, 0.7f, 0.5f)
        val cap = Color.hsl(260f, 0.7f, 0.7f)

        val adjusted = readableAccent(violet, isDarkTheme = true)

        assertTrue(adjusted.luminance() <= cap.luminance() + 0.001f)
    }

    @Test
    fun `a yellow that already reads in dark mode is unchanged`() {
        val yellow = Color.hsl(55f, 0.7f, 0.5f)

        assertEquals(yellow, readableAccent(yellow, isDarkTheme = true))
    }

    @Test
    fun `a light accent is darkened in light mode, not lifted`() {
        val cyan = Color.hsl(185f, 0.7f, 0.5f)

        val adjusted = readableAccent(cyan, isDarkTheme = false)

        assertTrue(adjusted.luminance() < cyan.luminance())
        assertTrue(adjusted.luminance() >= Color.hsl(185f, 0.7f, 0.4f).luminance() - 0.001f)
    }

    @Test
    fun `a dark accent that already reads in light mode is unchanged`() {
        val blue = Color.hsl(230f, 0.7f, 0.5f)

        assertEquals(blue, readableAccent(blue, isDarkTheme = false))
    }
}
