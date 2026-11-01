package com.nendo.argosy.ui.screens.settings.components

import com.nendo.argosy.core.input.ActiveController
import com.nendo.argosy.data.repository.ControllerInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MappingControllerTargetTest {

    private val padA = controller(deviceId = 1, controllerId = "1:1:a")
    private val padB = controller(deviceId = 2, controllerId = "1:1:b")
    private val controllers = listOf(padA, padB)

    @Test
    fun `switches to the active controller while not recording`() {
        val target = mappingControllerToLoad(
            editing = padA,
            loadedControllerId = padA.controllerId,
            recording = false,
            active = padB.active(),
            controllers = controllers
        )

        assertEquals(padB, target)
    }

    @Test
    fun `stays frozen on the edited controller while recording`() {
        val target = mappingControllerToLoad(
            editing = padA,
            loadedControllerId = padA.controllerId,
            recording = true,
            active = padB.active(),
            controllers = controllers
        )

        assertNull(target)
    }

    @Test
    fun `keeps a loaded controller that is already active`() {
        val target = mappingControllerToLoad(
            editing = padA,
            loadedControllerId = padA.controllerId,
            recording = false,
            active = padA.active(),
            controllers = controllers
        )

        assertNull(target)
    }

    @Test
    fun `loads the edited controller when its mapping was never loaded`() {
        val target = mappingControllerToLoad(
            editing = padA,
            loadedControllerId = null,
            recording = false,
            active = padA.active(),
            controllers = controllers
        )

        assertEquals(padA, target)
    }

    @Test
    fun `leaves the picker alone until a pad has sent input`() {
        val target = mappingControllerToLoad(
            editing = null,
            loadedControllerId = null,
            recording = false,
            active = null,
            controllers = controllers
        )

        assertNull(target)
    }

    @Test
    fun `leaves the picker for the active controller once it resolves`() {
        val target = mappingControllerToLoad(
            editing = null,
            loadedControllerId = null,
            recording = false,
            active = padB.active(),
            controllers = controllers
        )

        assertEquals(padB, target)
    }

    @Test
    fun `two identical pads share one mapping and do not switch`() {
        val twin = controller(deviceId = 3, controllerId = padA.controllerId)
        val target = mappingControllerToLoad(
            editing = padA,
            loadedControllerId = padA.controllerId,
            recording = false,
            active = twin.active(),
            controllers = listOf(padA, twin)
        )

        assertNull(target)
    }

    @Test
    fun `resolves by device first and by controller id when the device id changed`() {
        val twin = controller(deviceId = 3, controllerId = padA.controllerId)
        assertEquals(twin, twin.active().resolveIn(listOf(padA, twin)))

        val reconnected = ActiveController(deviceId = 9, controllerId = padB.controllerId)
        assertEquals(padB, reconnected.resolveIn(controllers))

        assertNull(ActiveController(deviceId = 9, controllerId = "gone").resolveIn(controllers))
    }

    private fun ControllerInfo.active() = ActiveController(deviceId, controllerId)

    private fun controller(deviceId: Int, controllerId: String) = ControllerInfo(
        deviceId = deviceId,
        controllerId = controllerId,
        name = "Pad $deviceId",
        vendorId = 1,
        productId = 1,
        detectedLayout = null
    )
}
