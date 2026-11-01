package com.nendo.argosy.ui.navigation

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NavBarVisibilityTest {

    private val pastTimeout = NavBarVisibility.AUTO_HIDE_MS + 1

    private fun TestScope.visibleAfterTimeout(quickNavigation: Boolean, autoHide: Boolean): Boolean {
        val visibility = NavBarVisibility(backgroundScope)
        visibility.show(autoHide = autoHide)
        runCurrent()
        advanceTimeBy(pastTimeout)
        runCurrent()
        return NavBarVisibility.isVisible(quickNavigation, autoHide, visibility.raised.value)
    }

    @Test
    fun `auto-hide on shows the bar until the timeout`() = runTest {
        val visibility = NavBarVisibility(backgroundScope)
        visibility.show(autoHide = true)
        runCurrent()
        assertTrue(NavBarVisibility.isVisible(quickNavigation = true, autoHide = true, raised = visibility.raised.value))

        advanceTimeBy(pastTimeout)
        runCurrent()
        assertFalse(NavBarVisibility.isVisible(quickNavigation = true, autoHide = true, raised = visibility.raised.value))
    }

    @Test
    fun `auto-hide off keeps the bar visible past the timeout`() = runTest {
        assertTrue(visibleAfterTimeout(quickNavigation = true, autoHide = false))
    }

    @Test
    fun `auto-hide off keeps the bar visible through a hide`() = runTest {
        val visibility = NavBarVisibility(backgroundScope)
        visibility.show(autoHide = false)
        visibility.hide()
        runCurrent()
        assertTrue(NavBarVisibility.isVisible(quickNavigation = true, autoHide = false, raised = visibility.raised.value))
    }

    @Test
    fun `auto-hide on lowers the bar at once on hide`() = runTest {
        val visibility = NavBarVisibility(backgroundScope)
        visibility.show(autoHide = true)
        visibility.hide()
        runCurrent()
        assertFalse(NavBarVisibility.isVisible(quickNavigation = true, autoHide = true, raised = visibility.raised.value))
    }

    @Test
    fun `quick navigation off hides the bar regardless of auto-hide`() = runTest {
        assertFalse(visibleAfterTimeout(quickNavigation = false, autoHide = false))

        val visibility = NavBarVisibility(backgroundScope)
        visibility.show(autoHide = true)
        runCurrent()
        assertFalse(NavBarVisibility.isVisible(quickNavigation = false, autoHide = true, raised = visibility.raised.value))
        assertFalse(NavBarVisibility.isAlwaysVisible(quickNavigation = false, autoHide = false))
    }

    private fun edgeClaims(
        navBarAllowed: Boolean = true,
        barVisible: Boolean = false,
        destinationCount: Int = 3,
        pressOnFooterHint: Boolean = false
    ): Boolean = NavBarVisibility.revealClaimsPress(navBarAllowed, barVisible, destinationCount, pressOnFooterHint)

    @Test
    fun `a press that raises a hidden bar belongs to the reveal`() {
        assertTrue(edgeClaims())
        assertTrue(NavBarVisibility.revealRaisesBar(navBarAllowed = true, pressOnFooterHint = false))
    }

    @Test
    fun `a press beside a bar already up passes through to the content`() {
        assertFalse(edgeClaims(barVisible = true))
    }

    @Test
    fun `a press where no bar can appear passes through to the content`() {
        assertFalse(edgeClaims(navBarAllowed = false))
        assertFalse(edgeClaims(destinationCount = 1))
        assertFalse(edgeClaims(destinationCount = 0))
        assertFalse(NavBarVisibility.revealRaisesBar(navBarAllowed = false, pressOnFooterHint = false))
    }

    @Test
    fun `a press on a footer hint under a hidden bar reaches the hint and leaves the bar down`() {
        assertFalse(edgeClaims(pressOnFooterHint = true))
        assertFalse(NavBarVisibility.revealRaisesBar(navBarAllowed = true, pressOnFooterHint = true))
    }

    @Test
    fun `a press on a footer hint beside a bar already up reaches the hint`() {
        assertFalse(edgeClaims(barVisible = true, pressOnFooterHint = true))
        assertFalse(NavBarVisibility.revealRaisesBar(navBarAllowed = true, pressOnFooterHint = true))
    }

    private fun claimsPress(visibility: NavBarVisibility, autoHide: Boolean): Boolean =
        edgeClaims(
            barVisible = NavBarVisibility.isVisible(quickNavigation = true, autoHide = autoHide, raised = visibility.raised.value)
        )

    @Test
    fun `a press on a raised bar reaches the bar until auto-hide lowers it`() = runTest {
        val visibility = NavBarVisibility(backgroundScope)
        assertTrue(claimsPress(visibility, autoHide = true))

        visibility.show(autoHide = true)
        runCurrent()
        assertFalse(claimsPress(visibility, autoHide = true))

        advanceTimeBy(pastTimeout)
        runCurrent()
        assertTrue(claimsPress(visibility, autoHide = true))
    }

    @Test
    fun `each press on a raised bar restarts the auto-hide timer`() = runTest {
        val visibility = NavBarVisibility(backgroundScope)
        val nearTimeout = NavBarVisibility.AUTO_HIDE_MS - 1_000
        visibility.show(autoHide = true)
        runCurrent()
        advanceTimeBy(nearTimeout)
        visibility.show(autoHide = true)
        runCurrent()
        advanceTimeBy(nearTimeout)
        runCurrent()

        assertFalse(claimsPress(visibility, autoHide = true))
    }

    @Test
    fun `a press on an always-visible bar always reaches the bar`() = runTest {
        val visibility = NavBarVisibility(backgroundScope)
        visibility.show(autoHide = false)
        advanceTimeBy(pastTimeout)
        runCurrent()

        assertFalse(claimsPress(visibility, autoHide = false))
    }

    @Test
    fun `only auto-hide off with quick navigation on is always visible`() {
        assertTrue(NavBarVisibility.isAlwaysVisible(quickNavigation = true, autoHide = false))
        assertFalse(NavBarVisibility.isAlwaysVisible(quickNavigation = true, autoHide = true))
    }
}
