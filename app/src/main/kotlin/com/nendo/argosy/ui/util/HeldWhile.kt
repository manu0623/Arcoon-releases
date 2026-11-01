package com.nendo.argosy.ui.util

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private class Pending<T>(val value: T)

fun <T> Flow<T>.heldWhile(hold: Flow<Boolean>): Flow<T> {
    val upstream = this
    return channelFlow {
        val gate = Mutex()
        var held = true
        var pending: Pending<T>? = null
        var upstreamDone = false
        lateinit var holdJob: Job

        suspend fun setHeld(isHeld: Boolean) = gate.withLock {
            held = isHeld
            val released = pending.takeUnless { isHeld }
            if (released != null) {
                pending = null
                send(released.value)
                if (upstreamDone) holdJob.cancel()
            }
        }

        holdJob = launch(start = CoroutineStart.UNDISPATCHED) {
            hold.collect { setHeld(it) }
            setHeld(false)
        }
        upstream.collect { value ->
            gate.withLock {
                if (held) {
                    pending = Pending(value)
                } else {
                    send(value)
                }
            }
        }
        gate.withLock {
            upstreamDone = true
            if (pending == null) holdJob.cancel()
        }
    }.buffer(Channel.RENDEZVOUS)
}
