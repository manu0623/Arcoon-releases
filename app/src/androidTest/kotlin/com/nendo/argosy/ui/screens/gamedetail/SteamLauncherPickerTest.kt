package com.nendo.argosy.ui.screens.gamedetail

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nendo.argosy.data.launcher.DroidDeckLauncher
import com.nendo.argosy.data.launcher.GameNativeLauncher
import com.nendo.argosy.data.launcher.SteamLauncher
import com.nendo.argosy.ui.screens.gamedetail.modals.SteamLauncherPickerModal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SteamLauncherPickerTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun automatic_choice_explains_effective_launcher_and_can_be_unpinned() {
        var selection: SteamLauncher? = GameNativeLauncher
        compose.setContent {
            MaterialTheme {
                SteamLauncherPickerModal(
                    availableLaunchers = listOf(DroidDeckLauncher),
                    currentLauncherName = "DroidDeck",
                    currentLauncherPackage = DroidDeckLauncher.packageName,
                    isAutomatic = true,
                    focusIndex = 0,
                    onSelectLauncher = { selection = it },
                    onDismiss = {}
                )
            }
        }

        compose.onNodeWithText("Uses DroidDeck").assertIsDisplayed()
        compose.onNodeWithText("Auto").performClick()
        compose.runOnIdle { assertNull(selection) }
        compose.onNodeWithText("DroidDeck", useUnmergedTree = true).performClick()
        compose.runOnIdle { assertEquals(DroidDeckLauncher, selection) }
    }

    @Test
    fun manually_selected_launcher_does_not_show_an_automatic_target() {
        compose.setContent {
            MaterialTheme {
                SteamLauncherPickerModal(
                    availableLaunchers = listOf(DroidDeckLauncher),
                    currentLauncherName = "DroidDeck",
                    currentLauncherPackage = DroidDeckLauncher.packageName,
                    isAutomatic = false,
                    focusIndex = 1,
                    onSelectLauncher = {},
                    onDismiss = {}
                )
            }
        }

        compose.onNodeWithText("DroidDeck").assertIsDisplayed()
        compose.onNodeWithText("Uses DroidDeck").assertDoesNotExist()
    }
}
