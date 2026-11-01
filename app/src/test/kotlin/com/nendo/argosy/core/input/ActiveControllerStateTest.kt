package com.nendo.argosy.core.input

import android.view.InputDevice
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.MotionEvent
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ActiveControllerStateTest {

    private val state = ActiveControllerState()
    private val padA = pad(deviceId = 11, descriptor = "a")
    private val padB = pad(deviceId = 12, descriptor = "b")

    @Test
    fun `a pad key press makes that pad active`() {
        state.noteInput(key(padA))

        assertEquals(ActiveController(11, controllerIdOf(padA)), state.active.value)
    }

    @Test
    fun `the most recent pad wins`() {
        state.noteInput(key(padA))
        state.noteInput(key(padB))

        assertEquals(12, state.active.value?.deviceId)
    }

    @Test
    fun `a key release does not switch`() {
        state.noteInput(key(padA))
        state.noteInput(key(padB, action = KeyEvent.ACTION_UP))

        assertEquals(11, state.active.value?.deviceId)
    }

    @Test
    fun `a deflected stick or trigger makes that pad active`() {
        state.noteInput(motion(padA, MotionEvent.AXIS_X to 0.9f))
        assertEquals(11, state.active.value?.deviceId)

        state.noteInput(motion(padB, MotionEvent.AXIS_RTRIGGER to 0.6f))
        assertEquals(12, state.active.value?.deviceId)
    }

    @Test
    fun `stick drift below the deflection threshold does not switch`() {
        state.noteInput(key(padA))
        state.noteInput(motion(padB, MotionEvent.AXIS_X to 0.2f, MotionEvent.AXIS_Y to -0.3f))

        assertEquals(11, state.active.value?.deviceId)
    }

    @Test
    fun `input without a device, from a keyboard, or synthesized is ignored`() {
        val keyboard: InputDevice = mockk {
            every { id } returns 20
            every { sources } returns InputDevice.SOURCE_KEYBOARD
            every { isVirtual } returns false
            every { name } returns "keyboard"
        }
        state.noteInput(key(device = null, deviceId = 30))
        state.noteInput(key(keyboard))
        state.noteInput(key(padA, deviceId = KeyCharacterMap.VIRTUAL_KEYBOARD))

        assertNull(state.active.value)
    }

    @Test
    fun `a held controller ignores input until released`() {
        state.noteInput(key(padA))
        state.setHeld(true)
        state.noteInput(key(padB))
        assertEquals(11, state.active.value?.deviceId)

        state.setHeld(false)
        state.noteInput(key(padB))
        assertEquals(12, state.active.value?.deviceId)
    }

    @Test
    fun `disconnecting the active pad falls back to the first remaining pad`() {
        state.noteInput(key(padB))

        state.onPadsChanged(listOf(padA.toActiveController()))

        assertEquals(11, state.active.value?.deviceId)
    }

    @Test
    fun `disconnecting the last pad clears the active controller`() {
        state.noteInput(key(padA))

        state.onPadsChanged(emptyList())

        assertNull(state.active.value)
    }

    @Test
    fun `a device change that keeps the active pad leaves it active`() {
        state.noteInput(key(padB))

        state.onPadsChanged(listOf(padA.toActiveController(), padB.toActiveController()))

        assertEquals(12, state.active.value?.deviceId)
    }

    @Test
    fun `a pad connecting before any input leaves no active controller`() {
        state.onPadsChanged(listOf(padA.toActiveController()))

        assertNull(state.active.value)
    }

    private fun pad(deviceId: Int, descriptor: String): InputDevice = mockk {
        every { id } returns deviceId
        every { sources } returns InputDevice.SOURCE_GAMEPAD
        every { isVirtual } returns false
        every { name } returns "pad $descriptor"
        every { vendorId } returns 1
        every { productId } returns 2
        every { this@mockk.descriptor } returns descriptor
    }

    private fun key(
        device: InputDevice?,
        action: Int = KeyEvent.ACTION_DOWN,
        deviceId: Int = device?.id ?: 0
    ): KeyEvent = mockk {
        every { this@mockk.action } returns action
        every { this@mockk.deviceId } returns deviceId
        every { this@mockk.device } returns device
    }

    private fun motion(device: InputDevice, vararg axes: Pair<Int, Float>): MotionEvent {
        val values = axes.toMap()
        return mockk {
            every { deviceId } returns device.id
            every { this@mockk.device } returns device
            every { getAxisValue(any()) } answers { values[firstArg<Int>()] ?: 0f }
        }
    }
}
