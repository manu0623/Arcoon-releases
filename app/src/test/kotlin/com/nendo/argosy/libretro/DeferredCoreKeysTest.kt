package com.nendo.argosy.libretro

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DeferredCoreKeysTest {

    private val forwarded = mutableListOf<String>()
    private var comboInFlight = false

    private fun kotlinx.coroutines.CoroutineScope.keys() = DeferredCoreKeys<String>(
        scope = this,
        comboInFlight = { comboInFlight },
        forwardDown = { keyCode, event -> forwarded += "down:$keyCode:$event" },
        forwardUp = { keyCode, event -> forwarded += "up:$keyCode:$event" },
    )

    @Test
    fun `a tap inside the window reaches the core as a short press`() = runTest {
        val keys = keys()
        keys.hold(1, "d")
        advanceTimeBy(30)
        assertTrue(forwarded.isEmpty())

        assertTrue(keys.release(1, "u"))
        assertEquals(listOf("down:1:d"), forwarded)

        advanceTimeBy(DeferredCoreKeys.TAP_MS)
        runCurrent()
        assertEquals(listOf("down:1:d", "up:1:u"), forwarded)
    }

    @Test
    fun `a press that outlives the window is forwarded as held`() = runTest {
        val keys = keys()
        keys.hold(1, "d")
        advanceTimeBy(DeferredCoreKeys.COMBO_WINDOW_MS)
        runCurrent()
        assertEquals(listOf("down:1:d"), forwarded)
        assertFalse(keys.isHolding(1))

        assertFalse(keys.release(1, "u"))
        assertEquals(listOf("down:1:d"), forwarded)
    }

    @Test
    fun `a key whose combo is still in flight stays held back past the window`() = runTest {
        val keys = keys()
        comboInFlight = true
        keys.hold(1, "d")
        advanceTimeBy(DeferredCoreKeys.COMBO_WINDOW_MS * 3)
        runCurrent()
        assertTrue(forwarded.isEmpty())
        assertTrue(keys.isHolding(1))

        assertTrue(keys.release(1, "u"))
        advanceTimeBy(DeferredCoreKeys.TAP_MS)
        runCurrent()
        assertEquals(listOf("down:1:d", "up:1:u"), forwarded)
    }

    @Test
    fun `a completed hotkey drops the held keys without forwarding them`() = runTest {
        val keys = keys()
        keys.hold(1, "d1")
        keys.hold(2, "d2")
        keys.clear()
        advanceTimeBy(DeferredCoreKeys.COMBO_WINDOW_MS)
        runCurrent()
        assertTrue(forwarded.isEmpty())
        assertFalse(keys.release(1, "u1"))
        assertFalse(keys.release(2, "u2"))
    }
}
