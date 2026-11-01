package com.nendo.argosy.ui.screens.settings

import com.nendo.argosy.ui.input.InputResult
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsTouchFocusTest {

    @Test
    fun `tapping a row moves focus to it and enters touch mode`() {
        val tapped = SettingsUiState(focusedIndex = 0).touchFocused(index = 4, maxIndex = 9)

        assertEquals(4, tapped.focusedIndex)
        assertTrue(tapped.isTouchMode)
    }

    @Test
    fun `a tap on the last row lands on it`() {
        val tapped = SettingsUiState(focusedIndex = 2).touchFocused(index = 9, maxIndex = 9)

        assertEquals(9, tapped.focusedIndex)
    }

    @Test
    fun `a tap past the last row keeps focus and still enters touch mode`() {
        val above = SettingsUiState(focusedIndex = 2).touchFocused(index = 10, maxIndex = 9)
        val below = SettingsUiState(focusedIndex = 2).touchFocused(index = -1, maxIndex = 9)

        assertEquals(2, above.focusedIndex)
        assertTrue(above.isTouchMode)
        assertEquals(2, below.focusedIndex)
        assertTrue(below.isTouchMode)
    }

    @Test
    fun `routing a tap clamps to the section's own rows`() {
        val state = MutableStateFlow(SettingsUiState(currentSection = SettingsSection.MAIN, focusedIndex = 0))
        val vm = mockk<SettingsViewModel>()
        every { vm._uiState } returns state

        routeTouchFocus(vm, 1)
        assertEquals(1, state.value.focusedIndex)
        assertTrue(state.value.isTouchMode)

        routeTouchFocus(vm, Int.MAX_VALUE)
        assertEquals(1, state.value.focusedIndex)
    }

    @Test
    fun `back at the top page leaves settings`() {
        var left = 0
        val vm = relaxedViewModel(navigatesBack = false)

        assertEquals(InputResult.HANDLED, SettingsInputHandler(vm) { left++ }.onBack())
        assertEquals(1, left)
    }

    @Test
    fun `back inside a section goes up one level and stays in settings`() {
        var left = 0
        val vm = relaxedViewModel(navigatesBack = true)

        assertEquals(InputResult.HANDLED, SettingsInputHandler(vm) { left++ }.onBack())
        assertEquals(0, left)
        verify { vm.navigateBack() }
    }

    @Test
    fun `gamepad input ends touch mode`() {
        val vm = relaxedViewModel(navigatesBack = true)
        every { vm.moveFocus(any()) } returns true
        val handler = SettingsInputHandler(vm) {}

        handler.onDown()
        handler.onBack()

        verify(exactly = 2) { vm.exitTouchMode() }
    }

    private fun relaxedViewModel(navigatesBack: Boolean): SettingsViewModel {
        val vm = mockk<SettingsViewModel>(relaxed = true)
        every { vm.uiState } returns MutableStateFlow(SettingsUiState(currentSection = SettingsSection.MAIN))
        every { vm.navigateBack() } returns navigatesBack
        return vm
    }
}
