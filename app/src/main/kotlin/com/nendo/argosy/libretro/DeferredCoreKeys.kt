package com.nendo.argosy.libretro

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Holds a combo member back from the core only for as long as its partners could still arrive.
 * A press that outlives the window reaches the core as a held key; a press released inside it
 * reaches the core as a short tap; a press that completes a hotkey never reaches the core.
 */
class DeferredCoreKeys<E>(
    private val scope: CoroutineScope,
    private val comboInFlight: (keyCode: Int) -> Boolean,
    private val forwardDown: (keyCode: Int, event: E) -> Unit,
    private val forwardUp: (keyCode: Int, event: E) -> Unit,
) {
    private class Held<E>(val down: E, val window: Job)

    private val held = mutableMapOf<Int, Held<E>>()

    fun isHolding(keyCode: Int): Boolean = keyCode in held

    fun hold(keyCode: Int, down: E) {
        held.remove(keyCode)?.window?.cancel()
        val window = scope.launch {
            delay(COMBO_WINDOW_MS)
            if (comboInFlight(keyCode)) return@launch
            if (held.remove(keyCode) == null) return@launch
            forwardDown(keyCode, down)
        }
        held[keyCode] = Held(down, window)
    }

    fun release(keyCode: Int, up: E): Boolean {
        val entry = held.remove(keyCode) ?: return false
        entry.window.cancel()
        forwardDown(keyCode, entry.down)
        scope.launch {
            delay(TAP_MS)
            forwardUp(keyCode, up)
        }
        return true
    }

    fun clear() {
        held.values.forEach { it.window.cancel() }
        held.clear()
    }

    companion object {
        const val COMBO_WINDOW_MS = 100L
        const val TAP_MS = 50L
    }
}
