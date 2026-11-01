package com.nendo.argosy.ui.screens.home.delegates

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

sealed interface DoubleTapPress {
    data class First(val token: Long) : DoubleTapPress
    data object Second : DoubleTapPress
}

/**
 * Double-tap detection over [windowMs]. A press within [windowMs] of an armed first press is the
 * second tap and cancels the first; any other press arms a new first tap. [claim] answers true
 * once for a first tap no second tap cancelled.
 */
class DoubleTapWindow(private val windowMs: Long) {
    private var armedToken: Long? = null
    private var armedAtMs = 0L
    private var lastToken = 0L
    private val cancelled = mutableSetOf<Long>()

    fun press(nowMs: Long): DoubleTapPress {
        val armed = armedToken
        if (armed != null && nowMs - armedAtMs <= windowMs) {
            armedToken = null
            cancelled += armed
            return DoubleTapPress.Second
        }
        lastToken += 1
        armedToken = lastToken
        armedAtMs = nowMs
        return DoubleTapPress.First(lastToken)
    }

    fun claim(token: Long): Boolean {
        if (armedToken == token) armedToken = null
        return !cancelled.remove(token)
    }

    fun reset() {
        armedToken?.let { cancelled += it }
        armedToken = null
    }
}

/**
 * Runs a press's single action [windowMs] after it, unless a second press lands inside the window,
 * which runs the double action at once and drops the single one.
 */
class SelectDoubleTap(
    private val scope: CoroutineScope,
    private val windowMs: Long,
    private val clock: () -> Long
) {
    private val window = DoubleTapWindow(windowMs)
    private var pending: Job? = null

    fun press(onSingle: () -> Unit, onDouble: () -> Unit) {
        when (val press = window.press(clock())) {
            DoubleTapPress.Second -> {
                pending?.cancel()
                pending = null
                onDouble()
            }
            is DoubleTapPress.First -> {
                pending = scope.launch {
                    delay(windowMs)
                    if (window.claim(press.token)) onSingle()
                }
            }
        }
    }

    fun cancel() {
        pending?.cancel()
        pending = null
        window.reset()
    }
}
