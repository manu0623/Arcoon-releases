package com.nendo.argosy.ui.screens.settings

import com.nendo.argosy.data.launcher.GameNativeLauncher
import com.nendo.argosy.ui.screens.settings.sections.SteamItem
import com.nendo.argosy.ui.screens.settings.sections.steamItemAtFocusIndex
import com.nendo.argosy.ui.screens.settings.sections.steamMaxFocusIndex
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsSteamFocusTest {
    private val installed = SteamSettingsState(
        gnInstalled = true,
        installedLaunchers = listOf(InstalledSteamLauncher(
            packageName = GameNativeLauncher.packageName,
            displayName = GameNativeLauncher.displayName
        )),
        notInstalledLaunchers = listOf(
            NotInstalledSteamLauncher("gamehub", "GameHub", false),
            NotInstalledSteamLauncher("gamehub_lite", "GameHub Lite", true)
        )
    )
    private val removed = installed.copy(
        gnInstalled = false,
        installedLaunchers = emptyList(),
        notInstalledLaunchers = installed.notInstalledLaunchers +
            NotInstalledSteamLauncher("gamenative", GameNativeLauncher.displayName, true)
    )

    @Test
    fun `removing only installed launcher keeps last row focus actionable`() {
        val before = SettingsUiState(
            currentSection = SettingsSection.STEAM_SETTINGS,
            focusedIndex = steamMaxFocusIndex(installed),
            isTouchMode = true,
            steam = installed
        )

        val after = merge(before, removed)

        assertEquals(before.copy(steam = removed, focusedIndex = steamMaxFocusIndex(removed)), after)
        val focused = steamItemAtFocusIndex(after.focusedIndex, after.steam)
        assertTrue(focused is SteamItem.NotInstalledLauncher)
        assertEquals("gamenative", (focused as SteamItem.NotInstalledLauncher).data.emulatorId)
    }

    @Test
    fun `availability refresh preserves valid Steam focus`() {
        val before = SettingsUiState(
            currentSection = SettingsSection.STEAM_SETTINGS,
            focusedIndex = 1,
            steam = installed
        )

        assertEquals(before.copy(steam = removed), merge(before, removed))
    }

    @Test
    fun `Steam state update preserves another section's focus`() {
        val before = SettingsUiState(
            currentSection = SettingsSection.PLATFORMS,
            focusedIndex = 42,
            steam = installed
        )

        assertEquals(before.copy(steam = removed), merge(before, removed))
    }

    private fun merge(before: SettingsUiState, steam: SteamSettingsState): SettingsUiState {
        val state = MutableStateFlow(before)
        val vm = mockk<SettingsViewModel>()
        every { vm._uiState } returns state

        routeUpdateSteamState(vm, steam)

        return state.value
    }
}
