package com.nendo.argosy.core.input

import android.view.InputDevice
import android.view.MotionEvent
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ControllerTouchpadTest {

    private val devices = mutableMapOf<Int, InputDevice>()

    @Before
    fun setUp() {
        mockkStatic(InputDevice::class)
        every { InputDevice.getDeviceIds() } answers { devices.keys.toIntArray() }
        every { InputDevice.getDevice(any()) } answers { devices[firstArg()] }
    }

    @After
    fun tearDown() {
        unmockkStatic(InputDevice::class)
    }

    private fun device(
        id: Int,
        vendor: Int,
        product: Int,
        sources: Int,
        name: String = "Wireless Controller"
    ): InputDevice {
        val device: InputDevice = mockk {
            every { this@mockk.id } returns id
            every { vendorId } returns vendor
            every { productId } returns product
            every { this@mockk.sources } returns sources
            every { isVirtual } returns false
            every { this@mockk.name } returns name
            every { supportsSource(any()) } answers { sources and firstArg<Int>() == firstArg<Int>() }
        }
        devices[id] = device
        return device
    }

    @Test
    fun `the touchpad of a connected controller is recognised by the controller's ids`() {
        device(10, 0x054C, 0x09CC, InputDevice.SOURCE_GAMEPAD or InputDevice.SOURCE_JOYSTICK)
        val touchpad = device(
            11, 0x054C, 0x09CC, InputDevice.SOURCE_MOUSE or InputDevice.SOURCE_TOUCHPAD,
            "Wireless Controller Touchpad"
        )

        assertTrue(touchpad.isControllerTouchpad())
    }

    @Test
    fun `a touchpad named after a connected controller counts when the pad is re-registered under other ids`() {
        device(
            38, 0x2020, 0x0111, InputDevice.SOURCE_GAMEPAD or InputDevice.SOURCE_JOYSTICK,
            "DualSense Wireless Controller"
        )
        val touchpad = device(
            37, 0x054C, 0x0CE6, InputDevice.SOURCE_MOUSE, "DualSense Wireless Controller Touchpad"
        )

        assertTrue(touchpad.isControllerTouchpad())
    }

    @Test
    fun `a touchpad merged with the motion sensors under the controller's own name counts`() {
        device(
            29, 0x2020, 0x0111, InputDevice.SOURCE_GAMEPAD or InputDevice.SOURCE_JOYSTICK,
            "DualSense Wireless Controller"
        )
        val touchpad = device(
            28, 0x054C, 0x0CE6, InputDevice.SOURCE_MOUSE or InputDevice.SOURCE_SENSOR,
            "DualSense Wireless Controller"
        )

        assertTrue(touchpad.isControllerTouchpad())
    }

    @Test
    fun `a mouse whose name only resembles a controller's is not a controller touchpad`() {
        device(38, 0x2020, 0x0111, InputDevice.SOURCE_GAMEPAD or InputDevice.SOURCE_JOYSTICK, "Wireless Controller")
        val mouse = device(40, 0x046D, 0xC077, InputDevice.SOURCE_MOUSE, "Wireless ControllerMouse")

        assertFalse(mouse.isControllerTouchpad())
    }

    @Test
    fun `a mouse from another vendor is not a controller touchpad`() {
        device(10, 0x054C, 0x09CC, InputDevice.SOURCE_GAMEPAD or InputDevice.SOURCE_JOYSTICK)
        val mouse = device(12, 0x046D, 0xC077, InputDevice.SOURCE_MOUSE, "USB Optical Mouse")

        assertFalse(mouse.isControllerTouchpad())
    }

    @Test
    fun `a touchpad without its controller connected is not a controller touchpad`() {
        val touchpad = device(11, 0x054C, 0x09CC, InputDevice.SOURCE_MOUSE, "Wireless Controller Touchpad")

        assertFalse(touchpad.isControllerTouchpad())
    }

    @Test
    fun `a gamepad that reports pointer input itself counts`() {
        val pad = device(13, 0x2DC8, 0x6001, InputDevice.SOURCE_GAMEPAD or InputDevice.SOURCE_MOUSE)

        assertTrue(pad.isControllerTouchpad())
    }

    private fun event(source: Int, from: InputDevice?): MotionEvent = mockk {
        every { isFromSource(any()) } answers { source and firstArg<Int>() == firstArg<Int>() }
        every { device } returns from
    }

    @Test
    fun `mouse, relative mouse and touchpad events from a controller's touchpad are controller pointer events`() {
        device(10, 0x054C, 0x09CC, InputDevice.SOURCE_GAMEPAD or InputDevice.SOURCE_JOYSTICK)
        val touchpad = device(
            11, 0x054C, 0x09CC, InputDevice.SOURCE_MOUSE or InputDevice.SOURCE_TOUCHPAD,
            "Wireless Controller Touchpad"
        )

        assertTrue(isControllerTouchpadEvent(event(InputDevice.SOURCE_MOUSE, touchpad)))
        assertTrue(isControllerTouchpadEvent(event(InputDevice.SOURCE_MOUSE_RELATIVE, touchpad)))
        assertTrue(isControllerTouchpadEvent(event(InputDevice.SOURCE_TOUCHPAD, touchpad)))
    }

    @Test
    fun `joystick and touchscreen events from a controller that also reports pointer input are not pointer events`() {
        val pad = device(
            14, 0x054C, 0x0CE6,
            InputDevice.SOURCE_GAMEPAD or InputDevice.SOURCE_JOYSTICK or InputDevice.SOURCE_MOUSE,
            "DualSense Wireless Controller"
        )

        assertFalse(isControllerTouchpadEvent(event(InputDevice.SOURCE_JOYSTICK, pad)))
        assertFalse(isControllerTouchpadEvent(event(InputDevice.SOURCE_GAMEPAD, pad)))
        assertFalse(isControllerTouchpadEvent(event(InputDevice.SOURCE_TOUCHSCREEN, pad)))
        assertTrue(isControllerTouchpadEvent(event(InputDevice.SOURCE_MOUSE, pad)))
    }

    @Test
    fun `pointer events from an ordinary mouse or from no device are not controller pointer events`() {
        device(10, 0x054C, 0x09CC, InputDevice.SOURCE_GAMEPAD or InputDevice.SOURCE_JOYSTICK)
        val mouse = device(12, 0x046D, 0xC077, InputDevice.SOURCE_MOUSE, "USB Optical Mouse")

        assertFalse(isControllerTouchpadEvent(event(InputDevice.SOURCE_MOUSE, mouse)))
        assertFalse(isControllerTouchpadEvent(event(InputDevice.SOURCE_MOUSE, null)))
    }

    @Test
    fun `a gamepad with no pointer source is not a touchpad`() {
        val pad = device(14, 0x054C, 0x09CC, InputDevice.SOURCE_GAMEPAD or InputDevice.SOURCE_JOYSTICK)

        assertFalse(pad.isControllerTouchpad())
    }
}
