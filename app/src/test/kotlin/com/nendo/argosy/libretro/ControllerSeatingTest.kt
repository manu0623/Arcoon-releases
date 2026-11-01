package com.nendo.argosy.libretro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ControllerSeatingTest {

    private val builtIn = SeatedPad("builtin", isBuiltIn = true)
    private val padA = SeatedPad("a", isBuiltIn = false)
    private val padB = SeatedPad("b", isBuiltIn = false)

    @Test
    fun `three pads with one unseated prompt`() {
        assertTrue(ControllerSeating.shouldPromptForSeats(listOf(builtIn, padA, padB), setOf("builtin", "a")))
    }

    @Test
    fun `three pads all seated do not prompt`() {
        assertFalse(ControllerSeating.shouldPromptForSeats(listOf(builtIn, padA, padB), setOf("builtin", "a", "b")))
    }

    @Test
    fun `two external pads with one unseated prompt`() {
        assertTrue(ControllerSeating.shouldPromptForSeats(listOf(padA, padB), setOf("a")))
    }

    @Test
    fun `two external pads all seated do not prompt`() {
        assertFalse(ControllerSeating.shouldPromptForSeats(listOf(padA, padB), setOf("a", "b")))
    }

    @Test
    fun `two pads with a built-in pad do not prompt even unseated`() {
        assertFalse(ControllerSeating.shouldPromptForSeats(listOf(builtIn, padA), emptySet()))
    }

    @Test
    fun `one pad never prompts`() {
        assertFalse(ControllerSeating.shouldPromptForSeats(listOf(padA), emptySet()))
    }

    @Test
    fun `saved seats of pads not connected do not count as seated`() {
        assertTrue(ControllerSeating.shouldPromptForSeats(listOf(padA, padB), setOf("a", "gone")))
    }

    @Test
    fun `docked launch pad takes player one from the built-in pad`() {
        val seats = ControllerSeating.launchPadSeats(
            docked = true,
            launchPadId = "a",
            connected = listOf(builtIn, padA),
            seats = mapOf("builtin" to 0)
        )

        assertEquals(mapOf("a" to 0, "builtin" to 1), seats)
    }

    @Test
    fun `docked launch pad trades seats with the built-in pad`() {
        val seats = ControllerSeating.launchPadSeats(
            docked = true,
            launchPadId = "a",
            connected = listOf(builtIn, padA, padB),
            seats = mapOf("builtin" to 0, "b" to 1, "a" to 2)
        )

        assertEquals(mapOf("a" to 0, "builtin" to 2), seats)
    }

    @Test
    fun `undocked launch changes no seat`() {
        val seats = ControllerSeating.launchPadSeats(
            docked = false,
            launchPadId = "a",
            connected = listOf(builtIn, padA),
            seats = mapOf("builtin" to 0)
        )

        assertTrue(seats.isEmpty())
    }

    @Test
    fun `launch from the built-in pad changes no seat`() {
        val seats = ControllerSeating.launchPadSeats(
            docked = true,
            launchPadId = "builtin",
            connected = listOf(builtIn, padA),
            seats = mapOf("a" to 0, "builtin" to 1)
        )

        assertTrue(seats.isEmpty())
    }

    @Test
    fun `external pad on player one is left alone`() {
        val seats = ControllerSeating.launchPadSeats(
            docked = true,
            launchPadId = "a",
            connected = listOf(builtIn, padA, padB),
            seats = mapOf("b" to 0, "builtin" to 1)
        )

        assertTrue(seats.isEmpty())
    }

    @Test
    fun `launch pad that is not connected changes no seat`() {
        val seats = ControllerSeating.launchPadSeats(
            docked = true,
            launchPadId = "a",
            connected = listOf(builtIn),
            seats = mapOf("builtin" to 0)
        )

        assertTrue(seats.isEmpty())
    }
}
