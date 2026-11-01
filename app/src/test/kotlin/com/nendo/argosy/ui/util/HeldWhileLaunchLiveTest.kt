package com.nendo.argosy.ui.util

import com.nendo.argosy.data.emulator.LaunchProgressTracker
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HeldWhileLaunchLiveTest {

    @Test
    fun `a launch begun with no step yet holds the row`() = runTest {
        val tracker = LaunchProgressTracker(backgroundScope)

        tracker.begin(1L, "Game")!!

        assertNull(tracker.progress.value)
        assertTrue(tracker.launchLive.value)
    }

    @Test
    fun `nothing launching lets the row move`() = runTest {
        assertFalse(LaunchProgressTracker(backgroundScope).launchLive.value)
    }

    @Test
    fun `the row settles once when the launch is over`() = runTest {
        val tracker = LaunchProgressTracker(backgroundScope)
        val recents = MutableStateFlow(listOf("b", "a"))
        val out = mutableListOf<List<String>>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            recents.heldWhile(tracker.launchLive).collect { out += it }
        }
        runCurrent()

        val ticket = tracker.begin(1L, "Game")!!
        runCurrent()
        recents.value = listOf("a", "b")
        runCurrent()
        assertEquals(listOf(listOf("b", "a")), out)

        tracker.finish(ticket, launched = false)
        runCurrent()
        assertEquals(listOf(listOf("b", "a"), listOf("a", "b")), out)

        recents.value = listOf("c", "a", "b")
        runCurrent()
        assertEquals(listOf(listOf("b", "a"), listOf("a", "b"), listOf("c", "a", "b")), out)
    }
}
