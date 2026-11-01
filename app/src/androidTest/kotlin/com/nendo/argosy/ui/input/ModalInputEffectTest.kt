package com.nendo.argosy.ui.input

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ModalInputEffectTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun replacingModalInputEndsEditingAndReturnsControlToTheView() {
        val dispatcher = InputDispatcher()
        val editing = mutableStateOf(true)
        val reorder = object : CapturingInputHandler {}
        val prompt = object : CapturingInputHandler {}
        var opened = 0
        dispatcher.subscribeView(object : InputHandler {
            override fun onConfirm(): InputResult {
                opened++
                return InputResult.HANDLED
            }
        })
        compose.setContent {
            CompositionLocalProvider(LocalInputDispatcher provides dispatcher) {
                ModalInputEffect(
                    active = editing.value,
                    handler = reorder,
                    onInputLost = { editing.value = false }
                )
            }
        }
        compose.runOnIdle {
            assertTrue(editing.value)
            assertTrue(dispatcher.hasModal(reorder))
            dispatcher.pushModal(prompt)
        }
        compose.runOnIdle {
            assertTrue(editing.value)
            dispatcher.removeModal(prompt)
        }
        compose.runOnIdle {
            assertTrue(editing.value)
            dispatcher.subscribeDrawer(prompt)
        }
        compose.runOnIdle {
            assertFalse(editing.value)
            assertFalse(dispatcher.hasModal(reorder))
            dispatcher.releaseDrawer(prompt)
            dispatcher.dispatch(GamepadInput(GamepadEvent.Confirm))
            assertEquals(1, opened)
        }
    }
}
