package com.nendo.argosy.data.preferences

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Rows written before the roles were named carry the positional tokens, and must keep landing on
 * the screen they always did.
 */
class EmulatorDisplayTargetTest {

    @Test
    fun `an unset target is the default`() {
        assertEquals(EmulatorDisplayTarget.DEFAULT, EmulatorDisplayTarget.fromString(null))
    }

    @Test
    fun `a stored top target is the default`() {
        assertEquals(EmulatorDisplayTarget.DEFAULT, EmulatorDisplayTarget.fromString("TOP"))
    }

    @Test
    fun `a stored bottom target is the primary screen`() {
        assertEquals(EmulatorDisplayTarget.PRIMARY, EmulatorDisplayTarget.fromString("BOTTOM"))
    }

    @Test
    fun `a stored hero target is the presentation screen`() {
        assertEquals(EmulatorDisplayTarget.PRESENTATION, EmulatorDisplayTarget.fromString("HERO"))
    }

    @Test
    fun `a stored library target is the primary screen`() {
        assertEquals(EmulatorDisplayTarget.PRIMARY, EmulatorDisplayTarget.fromString("LIBRARY"))
    }

    @Test
    fun `every current token round-trips through its stored name`() {
        EmulatorDisplayTarget.entries.forEach { target ->
            assertEquals(target, EmulatorDisplayTarget.fromString(target.name))
        }
    }

    @Test
    fun `an unreadable token is the default`() {
        assertEquals(EmulatorDisplayTarget.DEFAULT, EmulatorDisplayTarget.fromString("SIDECAR"))
    }
}
