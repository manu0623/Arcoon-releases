package com.nendo.argosy.data.emulator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GpuDriverPrecedenceTest {

    private val game = GpuDriverToken.custom("game")
    private val platform = GpuDriverToken.custom("platform")
    private val global = GpuDriverToken.custom("global")

    @Test
    fun `game wins over platform and global`() {
        assertEquals(game, GpuDriverPrecedence.select(game, platform, global))
        assertEquals(game, GpuDriverPrecedence.select(game, null, global))
    }

    @Test
    fun `platform wins over global when the game inherits`() {
        assertEquals(platform, GpuDriverPrecedence.select(null, platform, global))
    }

    @Test
    fun `global applies when neither game nor platform overrides`() {
        assertEquals(global, GpuDriverPrecedence.select(null, null, global))
    }

    @Test
    fun `an explicit system choice at a narrower scope still overrides a custom one above it`() {
        assertEquals(GpuDriverToken.SYSTEM, GpuDriverPrecedence.select(GpuDriverToken.SYSTEM, platform, global))
        assertEquals(GpuDriverToken.SYSTEM, GpuDriverPrecedence.select(null, GpuDriverToken.SYSTEM, global))
        assertEquals(platform, GpuDriverPrecedence.select(null, platform, GpuDriverToken.SYSTEM))
        assertEquals(game, GpuDriverPrecedence.select(game, GpuDriverToken.SYSTEM, GpuDriverToken.SYSTEM))
    }

    @Test
    fun `blank values inherit like missing ones`() {
        assertEquals(platform, GpuDriverPrecedence.select("", platform, global))
        assertEquals(global, GpuDriverPrecedence.select(" ", "", global))
        assertEquals(GpuDriverToken.SYSTEM, GpuDriverPrecedence.select(null, null, ""))
    }

    @Test
    fun `an installed custom driver loads on a supported device`() {
        val choice = GpuDriverPrecedence.resolve(platform, setOf("platform"), customDriversSupported = true)
        assertEquals("platform", choice.customDriverId)
        assertFalse(choice.fellBackToSystem)
    }

    @Test
    fun `a missing custom driver falls back to the system driver`() {
        val choice = GpuDriverPrecedence.resolve(platform, setOf("other"), customDriversSupported = true)
        assertNull(choice.customDriverId)
        assertTrue(choice.fellBackToSystem)
        assertEquals(platform, choice.requested)
    }

    @Test
    fun `a device that cannot load custom drivers always runs the system driver`() {
        val choice = GpuDriverPrecedence.resolve(platform, setOf("platform"), customDriversSupported = false)
        assertNull(choice.customDriverId)
        assertTrue(choice.fellBackToSystem)
    }

    @Test
    fun `the system token never reports a fallback`() {
        val choice = GpuDriverPrecedence.resolve(GpuDriverToken.SYSTEM, emptySet(), customDriversSupported = true)
        assertNull(choice.customDriverId)
        assertFalse(choice.fellBackToSystem)
    }

    @Test
    fun `custom tokens round trip and malformed ones carry no id`() {
        assertEquals("turnip_25", GpuDriverToken.customIdOf(GpuDriverToken.custom("turnip_25")))
        assertNull(GpuDriverToken.customIdOf(GpuDriverToken.SYSTEM))
        assertNull(GpuDriverToken.customIdOf("custom:"))
        assertNull(GpuDriverToken.customIdOf(null))
        assertNull(GpuDriverToken.customIdOf("turnip_25"))
    }
}
