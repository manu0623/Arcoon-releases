package com.nendo.argosy.ui.util

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean

@OptIn(ExperimentalCoroutinesApi::class)
class HeldWhileTest {
    private val upstream = MutableSharedFlow<Int>(extraBufferCapacity = 16)
    private val hold = MutableStateFlow(false)

    private fun TestScope.collect(
        dispatcher: CoroutineDispatcher = UnconfinedTestDispatcher(testScheduler)
    ): List<Int> {
        val out = mutableListOf<Int>()
        backgroundScope.launch(dispatcher) { upstream.heldWhile(hold).collect { out += it } }
        runCurrent()
        return out
    }

    private suspend fun TestScope.send(vararg values: Int) {
        values.forEach { upstream.emit(it) }
        runCurrent()
    }

    private fun TestScope.setHold(isHeld: Boolean) {
        hold.value = isHeld
        runCurrent()
    }

    @Test
    fun `values pass straight through while nothing holds`() = runTest {
        val out = collect()

        send(1, 2, 3)

        assertEquals(listOf(1, 2, 3), out)
    }

    @Test
    fun `values are kept back while held and only the latest arrives on release`() = runTest {
        val out = collect()
        send(1)

        setHold(true)
        send(2, 3, 4)
        assertEquals(listOf(1), out)

        setHold(false)
        assertEquals(listOf(1, 4), out)

        send(5)
        assertEquals(listOf(1, 4, 5), out)
    }

    @Test
    fun `holding and releasing behaves the same on a standard dispatcher`() = runTest {
        val out = collect(StandardTestDispatcher(testScheduler))
        send(1)

        setHold(true)
        send(2, 3)
        assertEquals(listOf(1), out)

        setHold(false)
        send(4)
        assertEquals(listOf(1, 3, 4), out)
    }

    @Test
    fun `a release with nothing kept back emits nothing`() = runTest {
        val out = collect()
        send(1)

        setHold(true)
        setHold(false)

        assertEquals(listOf(1), out)
    }

    @Test
    fun `a value arriving in the same turn the hold lifts is emitted once`() = runTest {
        val out = collect(StandardTestDispatcher(testScheduler))
        setHold(true)

        upstream.tryEmit(7)
        hold.value = false
        runCurrent()

        assertEquals(listOf(7), out)
    }

    @Test
    fun `a hold lifting in the same turn a value arrives emits it once`() = runTest {
        val out = collect(StandardTestDispatcher(testScheduler))
        setHold(true)

        hold.value = false
        upstream.tryEmit(7)
        runCurrent()

        assertEquals(listOf(7), out)
    }

    @Test
    fun `toggling the hold with no new values emits nothing extra`() = runTest {
        val out = collect()
        send(1)

        setHold(true)
        setHold(false)
        setHold(true)
        setHold(false)

        assertEquals(listOf(1), out)
    }

    @Test
    fun `a value released once is not released again by a later hold`() = runTest {
        val out = collect()
        send(1)

        setHold(true)
        send(2)
        setHold(false)
        send(3)
        setHold(true)
        setHold(false)

        assertEquals(listOf(1, 2, 3), out)
    }

    @Test
    fun `a slow collector keeps upstream to its pace instead of queueing values`() = runTest {
        var produced = 0
        val lead = mutableListOf<Int>()

        flow {
            repeat(100) {
                produced++
                emit(it)
            }
        }.heldWhile(MutableStateFlow(false)).collect { value ->
            lead += produced - value
            delay(10)
        }

        assertEquals(100, lead.size)
        assertTrue(lead.all { it <= 2 })
    }

    @Test
    fun `values wait until the hold is known and the latest then arrives once`() = runTest {
        val lateHold = flow {
            delay(100)
            emit(false)
            awaitCancellation()
        }
        val result = async { flowOf(1, 2).heldWhile(lateHold).toList() }

        advanceTimeBy(99)
        runCurrent()
        assertTrue(result.isActive)

        advanceUntilIdle()
        assertEquals(listOf(2), result.await())
    }

    @Test
    fun `a finished upstream completes the flow once nothing is kept back`() = runTest {
        assertEquals(listOf(1, 2, 3), flowOf(1, 2, 3).heldWhile(MutableStateFlow(false)).toList())
    }

    @Test
    fun `under real threads nothing is duplicated or reordered and the last value arrives`() = runTest {
        val out = withContext(Dispatchers.Default) {
            val gate = MutableStateFlow(false)
            val finished = AtomicBoolean(false)
            launch {
                while (!finished.get()) {
                    gate.value = !gate.value
                    yield()
                }
                gate.value = false
            }
            flow {
                repeat(2_000) {
                    emit(it)
                    yield()
                }
                finished.set(true)
            }.heldWhile(gate).toList()
        }

        assertTrue(out.zipWithNext().all { (a, b) -> a < b })
        assertEquals(1_999, out.last())
    }
}
