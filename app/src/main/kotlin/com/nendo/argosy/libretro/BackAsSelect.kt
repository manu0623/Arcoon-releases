package com.nendo.argosy.libretro

import android.view.InputDevice
import android.view.KeyEvent
import com.nendo.argosy.data.local.entity.SelectAsBackMode

/**
 * Whether a Back press plays as Select in game: never with [SelectAsBackMode.OFF], from any
 * gamepad with [SelectAsBackMode.ON], and with [SelectAsBackMode.AUTO] only from a gamepad that
 * reports no Select button of its own.
 */
fun backPlaysAsSelect(mode: SelectAsBackMode, fromGamepad: Boolean, deviceHasSelect: Boolean): Boolean =
    when (mode) {
        SelectAsBackMode.OFF -> false
        SelectAsBackMode.ON -> fromGamepad
        SelectAsBackMode.AUTO -> fromGamepad && !deviceHasSelect
    }

/**
 * [event] as a Select press when it is a Back press that [backPlaysAsSelect] hands to the game,
 * else [event] unchanged.
 */
class BackAsSelectTranslator(private val mode: () -> SelectAsBackMode) {
    private val hasSelectByDevice = mutableMapOf<Int, Boolean>()

    fun translate(event: KeyEvent): KeyEvent {
        if (event.keyCode != KeyEvent.KEYCODE_BACK) return event
        val device = event.device ?: return event
        val fromGamepad = !device.isVirtual &&
            (device.sources and (InputDevice.SOURCE_GAMEPAD or InputDevice.SOURCE_JOYSTICK)) != 0
        val hasSelect = hasSelectByDevice.getOrPut(device.id) {
            device.hasKeys(KeyEvent.KEYCODE_BUTTON_SELECT).firstOrNull() == true
        }
        if (!backPlaysAsSelect(mode(), fromGamepad, hasSelect)) return event
        return KeyEvent(
            event.downTime, event.eventTime, event.action, KeyEvent.KEYCODE_BUTTON_SELECT,
            event.repeatCount, event.metaState, event.deviceId, event.scanCode, event.flags, event.source
        )
    }
}
