package com.nendo.argosy.ui.components.launch

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class LaunchBreathColorTest {

    private val red = Color(0xFFD03020)
    private val blue = Color(0xFF2040D0)
    private val orange = Color(0xFFE08020)
    private val teal = Color(0xFF20A0A0)
    private val darkGrey = Color(0xFF404040)
    private val lightGrey = Color(0xFFC0C0C0)

    @Test
    fun redIsWarmerThanBlue() {
        assertEquals(red, warmerOf(red, blue))
        assertEquals(red, warmerOf(blue, red))
    }

    @Test
    fun orangeIsWarmerThanTeal() {
        assertEquals(orange, warmerOf(orange, teal))
        assertEquals(orange, warmerOf(teal, orange))
    }

    @Test
    fun identicalColoursReturnThatColour() {
        assertEquals(orange, warmerOf(orange, orange))
    }

    @Test
    fun greyscalePairTakesTheLighter() {
        assertEquals(lightGrey, warmerOf(darkGrey, lightGrey))
        assertEquals(lightGrey, warmerOf(lightGrey, darkGrey))
    }
}
