package com.nendo.argosy.libretro

import android.view.KeyEvent
import com.nendo.argosy.data.local.entity.HotkeyAction
import com.nendo.argosy.data.local.entity.HotkeyEntity
import com.nendo.argosy.data.repository.InputConfigRepository
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HotkeyManagerTest {

    private fun manager(keyCode: Int): HotkeyManager =
        HotkeyManager(mockk<InputConfigRepository>(relaxed = true)).apply {
            setHotkeys(
                listOf(
                    HotkeyEntity(
                        action = HotkeyAction.FAST_FORWARD,
                        buttonComboJson = "[$keyCode]"
                    )
                )
            )
        }

    @Test
    fun `platform button priority follows the event controller mapping`() {
        val keyCode = KeyEvent.KEYCODE_BUTTON_R2
        val manager = manager(keyCode)
        manager.setPlatformMappedButtons(
            mapOf(
                "gameplay-pad" to setOf(keyCode),
                "hotkey-pad" to emptySet()
            )
        )

        assertNull(manager.onKeyDown(keyCode, "gameplay-pad"))
        manager.onKeyUp(keyCode)
        assertEquals(HotkeyAction.FAST_FORWARD, manager.onKeyDown(keyCode, "hotkey-pad")?.action)
    }

    @Test
    fun `updated controller mapping replaces hotkey priority`() {
        val keyCode = KeyEvent.KEYCODE_BUTTON_R2
        val manager = manager(keyCode)
        manager.setPlatformMappedButtons(mapOf("pad" to emptySet()))

        assertEquals(HotkeyAction.FAST_FORWARD, manager.onKeyDown(keyCode, "pad")?.action)
        manager.onKeyUp(keyCode)

        manager.setPlatformMappedButtons(mapOf("pad" to setOf(keyCode)))

        assertNull(manager.onKeyDown(keyCode, "pad"))
    }

    @Test
    fun `any physical key can drive a hotkey except the ones the system owns`() {
        assertEquals(
            HotkeyAction.FAST_FORWARD,
            manager(KeyEvent.KEYCODE_BUTTON_MODE).onKeyDown(KeyEvent.KEYCODE_BUTTON_MODE, "pad")?.action
        )
        assertEquals(
            HotkeyAction.FAST_FORWARD,
            manager(KeyEvent.KEYCODE_BUTTON_7).onKeyDown(KeyEvent.KEYCODE_BUTTON_7, "pad")?.action
        )
        assertEquals(
            HotkeyAction.FAST_FORWARD,
            manager(KeyEvent.KEYCODE_F1).onKeyDown(KeyEvent.KEYCODE_F1, "pad")?.action
        )
        assertNull(manager(KeyEvent.KEYCODE_HOME).onKeyDown(KeyEvent.KEYCODE_HOME, "pad"))
    }

    @Test
    fun `only a single key bound to the menu counts as the menu toggle`() {
        val manager = HotkeyManager(mockk<InputConfigRepository>(relaxed = true)).apply {
            setHotkeys(
                listOf(
                    HotkeyEntity(
                        action = HotkeyAction.IN_GAME_MENU,
                        buttonComboJson = "[${KeyEvent.KEYCODE_BUTTON_MODE}]"
                    ),
                    HotkeyEntity(
                        action = HotkeyAction.FAST_FORWARD,
                        buttonComboJson = "[${KeyEvent.KEYCODE_BUTTON_R2}]"
                    ),
                    HotkeyEntity(
                        action = HotkeyAction.QUICK_SAVE,
                        buttonComboJson = "[${KeyEvent.KEYCODE_BUTTON_SELECT},${KeyEvent.KEYCODE_BUTTON_START}]"
                    )
                )
            )
        }

        assertTrue(manager.isMenuToggleKey(KeyEvent.KEYCODE_BUTTON_MODE, "pad"))
        assertFalse(manager.isMenuToggleKey(KeyEvent.KEYCODE_BUTTON_R2, "pad"))
        assertFalse(manager.isMenuToggleKey(KeyEvent.KEYCODE_BUTTON_SELECT, "pad"))

        manager.setPlatformMappedButtons(mapOf("pad" to setOf(KeyEvent.KEYCODE_BUTTON_MODE)))
        assertFalse(manager.isMenuToggleKey(KeyEvent.KEYCODE_BUTTON_MODE, "pad"))
    }

    @Test
    fun `a combo is in flight only while another of its members is down`() {
        val start = KeyEvent.KEYCODE_BUTTON_START
        val select = KeyEvent.KEYCODE_BUTTON_SELECT
        val manager = HotkeyManager(mockk<InputConfigRepository>(relaxed = true)).apply {
            setHotkeys(
                listOf(
                    HotkeyEntity(
                        action = HotkeyAction.IN_GAME_MENU,
                        buttonComboJson = "[$start,$select]"
                    ),
                    HotkeyEntity(
                        action = HotkeyAction.QUICK_SUSPEND,
                        buttonComboJson = "[$start,$select]",
                        holdMs = 1000
                    )
                )
            )
        }

        assertFalse(manager.isComboInFlight(start))
        assertNull(manager.onKeyDown(start, "pad"))
        assertFalse(manager.isComboInFlight(start))
        assertTrue(manager.isComboInFlight(select))

        assertNull(manager.onKeyDown(select, "pad"))
        assertTrue(manager.isComboInFlight(start))
        assertTrue(manager.isComboInFlight(select))

        manager.onKeyUp(select)
        manager.onKeyUp(start)
        assertFalse(manager.isComboInFlight(start))
        assertFalse(manager.isComboInFlight(select))
    }
}
