package com.nendo.argosy.ui.dualscreen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

private class Surface(val name: String)

class DisplayHostRegistryTest {

    private val registry = DisplayHostRegistry<Surface>()

    @Test
    fun `two displays each keep their own surface`() {
        val lower = Surface("lower")
        val monitor = Surface("monitor")

        registry.register(4, lower)
        registry.register(7, monitor)

        assertSame(lower, registry.hostFor(4))
        assertSame(monitor, registry.hostFor(7))
    }

    @Test
    fun `registering a second surface for one display replaces the first`() {
        val old = Surface("old")
        val new = Surface("new")

        registry.register(4, old)
        registry.register(4, new)

        assertSame(new, registry.hostFor(4))
        assertEquals(1, registry.all().size)
    }

    @Test
    fun `replacing a surface answers the one it displaced`() {
        val old = Surface("old")
        val new = Surface("new")

        assertNull(registry.register(4, old))
        assertSame(old, registry.register(4, new))
    }

    @Test
    fun `registering the same surface again displaces nothing`() {
        val host = Surface("host")
        registry.register(4, host)

        assertNull(registry.register(4, host))
    }

    @Test
    fun `a surface on another display is never displaced`() {
        registry.register(4, Surface("lower"))

        assertNull(registry.register(7, Surface("monitor")))
    }

    @Test
    fun `a replaced surface tearing down late does not unregister its replacement`() {
        val old = Surface("old")
        val new = Surface("new")
        registry.register(4, old)
        registry.register(4, new)

        assertFalse(registry.unregister(4, old))

        assertSame(new, registry.hostFor(4))
    }

    @Test
    fun `the surface still registered clears itself`() {
        val host = Surface("host")
        registry.register(4, host)

        assertTrue(registry.unregister(4, host))

        assertNull(registry.hostFor(4))
    }

    @Test
    fun `a broadcast reaches every registered surface`() {
        val lower = Surface("lower")
        val monitor = Surface("monitor")
        registry.register(4, lower)
        registry.register(7, monitor)

        assertEquals(listOf(lower, monitor), registry.all())
    }

    @Test
    fun `an unheld display answers nothing rather than the nearest surface`() {
        registry.register(4, Surface("lower"))

        assertNull(registry.hostFor(7))
    }

    @Test
    fun `a role resolving to no display addresses nothing`() {
        registry.register(4, Surface("lower"))

        assertNull(registry.hostFor(null))
    }

    @Test
    fun `unregistering the last surface empties the registry`() {
        val host = Surface("host")
        registry.register(4, host)
        registry.unregister(4, host)

        assertTrue(registry.isEmpty)
        assertEquals(emptySet<Int>(), registry.displayIds())
    }
}
