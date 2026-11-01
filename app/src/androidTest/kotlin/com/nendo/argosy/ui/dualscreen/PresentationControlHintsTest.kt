package com.nendo.argosy.ui.dualscreen

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nendo.argosy.ui.components.InputButton
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PresentationControlHintsTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun forcedHoldHintAppearsAndCollapsesWhenForceIsRemoved() {
        val hints = mutableStateOf(listOf(CompanionHint(InputButton.A, "Hold to reorder", forced = true)))
        compose.setContent { MaterialTheme { PresentationControlHints(hints.value) } }

        compose.onNodeWithText("Hold to reorder").assertIsDisplayed()
        compose.runOnIdle { hints.value = hints.value.map { it.copy(forced = false) } }
        compose.onNodeWithText("Hold to reorder").assertIsNotDisplayed()
    }

    @Test
    fun activeReorderInstructionsRemainVisible() {
        val hints = listOf(
            CompanionHint(InputButton.DPAD_HORIZONTAL, "Move", forced = true),
            CompanionHint(InputButton.A, "Done", forced = true),
            CompanionHint(InputButton.B, "Undo", forced = true)
        )
        compose.setContent { MaterialTheme { PresentationControlHints(hints) } }

        compose.onNodeWithText("Move").assertIsDisplayed()
        compose.onNodeWithText("Done").assertIsDisplayed()
        compose.onNodeWithText("Undo").assertIsDisplayed()
    }

    @Test
    fun ordinaryHintsKeepExistingVisibilityRules() {
        val hints = mutableStateOf(listOf(CompanionHint(InputButton.B, "Back")))
        compose.setContent { MaterialTheme { PresentationControlHints(hints.value) } }

        compose.onNodeWithText("Back").assertIsNotDisplayed()
        compose.runOnIdle { hints.value = listOf(CompanionHint(InputButton.Y, "Favorite")) }
        compose.onNodeWithText("Favorite").assertIsDisplayed()
    }
}
