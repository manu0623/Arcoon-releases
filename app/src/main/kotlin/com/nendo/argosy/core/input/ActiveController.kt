package com.nendo.argosy.core.input

import android.view.InputDevice
import android.view.InputEvent
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.MotionEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs

data class ActiveController(
    val deviceId: Int,
    val controllerId: String
)

fun InputDevice.toActiveController(): ActiveController = ActiveController(id, controllerIdOf(this))

private const val ACTIVATING_DEFLECTION = 0.5f

private val ACTIVATING_AXES = intArrayOf(
    MotionEvent.AXIS_X,
    MotionEvent.AXIS_Y,
    MotionEvent.AXIS_Z,
    MotionEvent.AXIS_RZ,
    MotionEvent.AXIS_HAT_X,
    MotionEvent.AXIS_HAT_Y,
    MotionEvent.AXIS_LTRIGGER,
    MotionEvent.AXIS_RTRIGGER,
    MotionEvent.AXIS_BRAKE,
    MotionEvent.AXIS_GAS
)

/**
 * The pad that last sent input. A key press or an axis pushed past [ACTIVATING_DEFLECTION] from a
 * device that acts as a gamepad takes the slot; events without a device, from the virtual keyboard,
 * or from a non-pad device never do. While held, input is ignored and only a disconnect moves it.
 */
class ActiveControllerState {
    private val _active = MutableStateFlow<ActiveController?>(null)
    val active: StateFlow<ActiveController?> = _active.asStateFlow()

    @Volatile
    private var held = false

    fun setHeld(held: Boolean) {
        this.held = held
    }

    fun noteInput(event: InputEvent) {
        if (held) return
        if (event is KeyEvent && event.action != KeyEvent.ACTION_DOWN) return
        val deviceId = event.deviceId
        if (deviceId == KeyCharacterMap.VIRTUAL_KEYBOARD) return
        if (_active.value?.deviceId == deviceId) return
        if (event is MotionEvent && !event.isDeflected()) return
        val device = event.device ?: return
        if (!device.actsAsGamepad()) return
        _active.value = device.toActiveController()
    }

    fun onPadsChanged(connected: List<ActiveController>) {
        val current = _active.value ?: return
        if (connected.any { it.deviceId == current.deviceId }) return
        _active.value = connected.firstOrNull()
    }

    private fun MotionEvent.isDeflected(): Boolean =
        ACTIVATING_AXES.any { abs(getAxisValue(it)) >= ACTIVATING_DEFLECTION }
}
