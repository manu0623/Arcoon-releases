package com.nendo.argosy.ui.screens.home.delegates

import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SelectDoubleTapTest {

    private val window = 300L

    @Test
    fun `a lone press is a first tap that claims once`() {
        val taps = DoubleTapWindow(window)
        val press = taps.press(1_000) as DoubleTapPress.First
        assertTrue(taps.claim(press.token))
        assertTrue(taps.press(1_100) is DoubleTapPress.First)
    }

    @Test
    fun `a press inside the window is the second tap and cancels the first`() {
        val taps = DoubleTapWindow(window)
        val first = taps.press(1_000) as DoubleTapPress.First
        assertEquals(DoubleTapPress.Second, taps.press(1_299))
        assertFalse(taps.claim(first.token))
    }

    @Test
    fun `a press exactly at the window edge still counts as the second tap`() {
        val taps = DoubleTapWindow(window)
        taps.press(1_000)
        assertEquals(DoubleTapPress.Second, taps.press(1_300))
    }

    @Test
    fun `a press after the window arms a new first tap and the old one still claims`() {
        val taps = DoubleTapWindow(window)
        val first = taps.press(1_000) as DoubleTapPress.First
        val next = taps.press(1_301) as DoubleTapPress.First
        assertTrue(taps.claim(first.token))
        assertTrue(taps.claim(next.token))
    }

    @Test
    fun `a third quick press after a double tap starts over`() {
        val taps = DoubleTapWindow(window)
        taps.press(1_000)
        taps.press(1_100)
        assertTrue(taps.press(1_200) is DoubleTapPress.First)
    }

    @Test
    fun `reset cancels an armed first tap`() {
        val taps = DoubleTapWindow(window)
        val first = taps.press(1_000) as DoubleTapPress.First
        taps.reset()
        assertFalse(taps.claim(first.token))
        assertTrue(taps.press(1_050) is DoubleTapPress.First)
    }

    @Test
    fun `a single press runs its action only after the window`() = runTest {
        val scope = TestScope(testScheduler)
        val gesture = SelectDoubleTap(scope, window) { testScheduler.currentTime }
        var singles = 0
        var doubles = 0

        gesture.press(onSingle = { singles++ }, onDouble = { doubles++ })
        testScheduler.advanceTimeBy(window - 1)
        testScheduler.runCurrent()
        assertEquals(0, singles)

        testScheduler.advanceTimeBy(2)
        testScheduler.runCurrent()
        assertEquals(1, singles)
        assertEquals(0, doubles)
    }

    @Test
    fun `a second press inside the window runs the double action at once and drops the single`() = runTest {
        val scope = TestScope(testScheduler)
        val gesture = SelectDoubleTap(scope, window) { testScheduler.currentTime }
        var singles = 0
        var doubles = 0

        gesture.press(onSingle = { singles++ }, onDouble = { doubles++ })
        testScheduler.advanceTimeBy(150)
        gesture.press(onSingle = { singles++ }, onDouble = { doubles++ })
        assertEquals(1, doubles)

        testScheduler.advanceUntilIdle()
        assertEquals(0, singles)
    }

    @Test
    fun `two presses further apart than the window each run the single action`() = runTest {
        val scope = TestScope(testScheduler)
        val gesture = SelectDoubleTap(scope, window) { testScheduler.currentTime }
        var singles = 0
        var doubles = 0

        gesture.press(onSingle = { singles++ }, onDouble = { doubles++ })
        testScheduler.advanceTimeBy(window + 50)
        testScheduler.runCurrent()
        gesture.press(onSingle = { singles++ }, onDouble = { doubles++ })
        testScheduler.advanceUntilIdle()

        assertEquals(2, singles)
        assertEquals(0, doubles)
    }

    @Test
    fun `cancel drops a pending single action`() = runTest {
        val scope = TestScope(testScheduler)
        val gesture = SelectDoubleTap(scope, window) { testScheduler.currentTime }
        var singles = 0

        gesture.press(onSingle = { singles++ }, onDouble = {})
        gesture.cancel()
        testScheduler.advanceUntilIdle()

        assertEquals(0, singles)
    }
}
