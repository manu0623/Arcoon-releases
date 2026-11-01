package com.nendo.argosy.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.R
import com.nendo.argosy.ui.theme.LocalUiScale
import com.nendo.argosy.ui.theme.Motion
import com.nendo.argosy.ui.theme.UiScaleConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CompanionAppBarTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun controllerSwapMovesBothIconsThroughIntermediatePositions() {
        val original = listOf("a", "b", "c")
        val session = mutableStateOf(DockReorderSession.lift(1, true, original, original, "a")!!)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            MaterialTheme {
                CompanionAppBar(
                    apps = session.value.order,
                    onAppClick = {},
                    reorder = session.value,
                    focusedIndex = session.value.targetIndex
                )
            }
        }
        val start = compose.onNodeWithContentDescription("a").fetchSemanticsNode().boundsInRoot.center.x
        val end = compose.onNodeWithContentDescription("c").fetchSemanticsNode().boundsInRoot.center.x
        compose.runOnIdle { session.value = session.value.target(2) }
        repeat(2) { compose.mainClock.advanceTimeByFrame() }
        compose.mainClock.advanceTimeBy(Motion.durationSlide / 3L)
        compose.waitForIdle()
        val held = compose.onNodeWithContentDescription("a").fetchSemanticsNode().boundsInRoot.center.x
        val displaced = compose.onNodeWithContentDescription("c").fetchSemanticsNode().boundsInRoot.center.x
        assertTrue(held > start && held < end)
        assertTrue(displaced > start && displaced < end)
        compose.mainClock.advanceTimeBy(Motion.durationSlide.toLong())
        compose.waitForIdle()
        assertEquals(end, compose.onNodeWithContentDescription("a").fetchSemanticsNode().boundsInRoot.center.x, 1f)
        assertEquals(start, compose.onNodeWithContentDescription("c").fetchSemanticsNode().boundsInRoot.center.x, 1f)
    }

    @Test
    fun touchHoldSurvivesRecompositionAndSwapsFromOriginalOrder() {
        touchSwap(LayoutDirection.Ltr)
    }

    @Test
    fun rtlTouchDragFollowsFingerAndSwapsTowardTheLeft() {
        touchSwap(LayoutDirection.Rtl)
    }

    @Test
    fun rtlTouchDragScrollsAtTheLeftEdgeToReachTheLastPin() {
        touchSwap(LayoutDirection.Rtl, overflow = true)
    }

    private fun touchSwap(direction: LayoutDirection, overflow: Boolean = false) {
        val original = if (overflow) listOf("a", "b", "c", "d", "e", "f") else listOf("a", "b", "c", "d")
        val apps = mutableStateOf(original)
        val session = mutableStateOf<DockReorderSession?>(null)
        var saved: List<String>? = null
        compose.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides direction) {
                MaterialTheme {
                    CompanionAppBar(
                        apps = apps.value,
                        onAppClick = {},
                        maximumWidth = if (overflow) 280.dp else 600.dp,
                        reorder = session.value,
                        onReorderStart = { packageName ->
                            session.value = DockReorderSession.lift(1, false, original, original, packageName)
                            session.value?.token
                        },
                        onReorderTarget = { _, target ->
                            session.value = session.value?.target(target)
                            apps.value = session.value!!.order
                        },
                        onReorderSave = { saved = apps.value; session.value = null },
                        onReorderCancel = { apps.value = original; session.value = null }
                    )
                }
            }
        }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dock = compose.onNodeWithContentDescription(context.getString(R.string.dock_accessibility_label))
        val origin = dock.fetchSemanticsNode().boundsInRoot.topLeft
        val start = compose.onNodeWithContentDescription("a").fetchSemanticsNode().boundsInRoot.center - origin
        val target = if (overflow) Offset(0f, start.y) else
            compose.onNodeWithContentDescription("c").fetchSemanticsNode().boundsInRoot.center - origin
        dock.performTouchInput { down(start) }
        compose.mainClock.advanceTimeBy(700)
        compose.waitForIdle()
        compose.runOnIdle { assertEquals("a", session.value?.heldPackage) }
        dock.performTouchInput { moveTo(target) }
        if (overflow) compose.waitUntil(5_000) { session.value?.targetIndex == original.lastIndex }
        else {
            compose.waitForIdle()
            assertEquals(target.x + origin.x,
                compose.onNodeWithContentDescription("a").fetchSemanticsNode().boundsInRoot.center.x, 1f)
        }
        dock.performTouchInput { up() }
        compose.runOnIdle {
            assertEquals(if (overflow) listOf("f", "b", "c", "d", "e", "a") else listOf("c", "b", "a", "d"), saved)
        }
    }

    @Test
    fun accessibleSwapKeepsOverflowSlotsInPlace() {
        accessibleSwap(LayoutDirection.Ltr)
    }

    @Test
    fun rtlAccessibilityActionsFollowPhysicalDirectionAndBoundaries() {
        accessibleSwap(LayoutDirection.Rtl)
    }

    private fun accessibleSwap(direction: LayoutDirection) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val original = listOf("a", "b", "c", "d", "e", "f")
        val apps = mutableStateOf(original)
        val session = mutableStateOf<DockReorderSession?>(null)
        var saved: List<String>? = null
        compose.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides direction) {
                MaterialTheme {
                    Box(Modifier.width(440.dp)) {
                        CompanionAppBar(
                            apps = apps.value,
                            onAppClick = {},
                            maximumWidth = 400.dp,
                            reorder = session.value,
                            onReorderStart = { packageName ->
                                session.value = DockReorderSession.lift(1, false, original, original, packageName)
                                session.value?.token
                            },
                            onReorderTarget = { _, target ->
                                session.value = session.value?.target(target)
                                apps.value = session.value!!.order
                            },
                            onReorderSave = {
                                saved = apps.value
                                session.value = null
                            },
                            onReorderCancel = {
                                apps.value = original
                                session.value = null
                            }
                        )
                    }
                }
            }
        }
        val originalLeft = compose.onNodeWithContentDescription("a").fetchSemanticsNode().boundsInRoot.left
        val dockLabel = context.getString(R.string.dock_accessibility_label)
        fun invokeAction(label: String) {
            val actions = compose.onNodeWithContentDescription(dockLabel).fetchSemanticsNode()
                .config[SemanticsActions.CustomActions]
            compose.runOnIdle { actions.first { it.label == label }.action() }
        }
        compose.onNodeWithContentDescription("a").performSemanticsAction(SemanticsActions.OnLongClick) { it() }
        val forward = context.getString(if (direction == LayoutDirection.Rtl)
            R.string.dock_reorder_move_left else R.string.dock_reorder_move_right)
        val backward = context.getString(if (direction == LayoutDirection.Rtl)
            R.string.dock_reorder_move_right else R.string.dock_reorder_move_left)
        fun assertBoundary(absent: String, present: String) {
            val labels = compose.onNodeWithContentDescription(dockLabel).fetchSemanticsNode()
                .config[SemanticsActions.CustomActions].map { it.label }
            assertTrue(absent !in labels)
            assertTrue(present in labels)
        }
        assertBoundary(backward, forward)
        repeat(2) {
            invokeAction(forward)
        }
        compose.onNodeWithContentDescription("c").assertIsDisplayed()
        val swappedLeft = compose.onNodeWithContentDescription("c").fetchSemanticsNode().boundsInRoot.left
        assertEquals(originalLeft, swappedLeft, 1f)
        compose.runOnIdle { assertEquals(listOf("c", "b", "a", "d", "e", "f"), apps.value) }
        repeat(3) { invokeAction(forward) }
        assertBoundary(forward, backward)
        compose.onNodeWithContentDescription("a").assertIsDisplayed()
        invokeAction(context.getString(R.string.reorder_hint_done))
        compose.runOnIdle { assertEquals(listOf("f", "b", "c", "d", "e", "a"), saved) }
    }

    @Test
    fun pinnedAppRemainsVisibleAndTouchableWhenNavigationIsNarrow() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val packageName = context.packageName
        val label = context.packageManager.getApplicationLabel(context.applicationInfo).toString()
        var launchedPackage: String? = null
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalUiScale provides UiScaleConfig(scale = 1.5f)) {
                    Box(Modifier.width(320.dp)) {
                        CompanionAppBar(
                            apps = listOf(packageName),
                            onAppClick = { launchedPackage = it },
                            maximumWidth = 104.dp,
                            modifier = Modifier.padding(horizontal = Dimens.spacingMd)
                        )
                    }
                }
            }
        }

        compose.waitUntil { compose.onAllNodesWithContentDescription(label).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription(label)
            .assertIsDisplayed()
            .performTouchInput { click() }
        compose.runOnIdle { assertEquals(packageName, launchedPackage) }
    }
}
