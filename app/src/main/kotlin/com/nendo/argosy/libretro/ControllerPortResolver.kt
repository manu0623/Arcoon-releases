package com.nendo.argosy.libretro

import android.view.InputDevice
import com.nendo.argosy.core.input.ControllerDetector
import com.nendo.argosy.core.input.controllerIdOf
import com.nendo.argosy.core.input.isPhysicalGamepad
import com.nendo.argosy.data.local.entity.ControllerOrderEntity
import com.swordfish.libretrodroid.PortResolver

/**
 * Seats each pad on its session seat, its settings-assigned port, or the port its first input
 * claims, in that order. A built-in pad yields player one to an unseated external pad and takes
 * it back when it is the only pad and the saved player one is absent. Session seats never change
 * settings assignments.
 */
class ControllerPortResolver : PortResolver {
    private var controllerOrder: Map<String, Int> = emptyMap()
    private val claimedPorts = mutableMapOf<String, Int>()
    private var sessionSeats: Map<String, Int> = emptyMap()
    private var launchPadId: String? = null
    private var docked = false

    var onPortClaimed: ((controllerId: String, port: Int) -> Unit)? = null

    fun setControllerOrder(orders: List<ControllerOrderEntity>) {
        controllerOrder = orders.associate { it.controllerId to it.port }
        claimedPorts.keys.removeAll(controllerOrder.keys)
        reseatLaunchPad()
    }

    fun clearControllerOrder() {
        controllerOrder = emptyMap()
        claimedPorts.clear()
        reseatLaunchPad()
    }

    /**
     * Names the pad that launched this session and whether an external display was docked when it
     * started. Read once at session start.
     */
    fun setLaunchPad(controllerId: String?, isDocked: Boolean) {
        launchPadId = controllerId
        docked = isDocked
        reseatLaunchPad()
    }

    /**
     * Drops the claims of disconnected pads, and of built-in pads once no external pad remains.
     * Settings assignments are kept.
     */
    fun releaseDisconnected(connectedControllerIds: Set<String>) {
        claimedPorts.keys.retainAll(connectedControllerIds)
        val pads = connectedGamepads()
        if (pads.none { !ControllerDetector.isBuiltInPad(it) }) {
            pads.filter { ControllerDetector.isBuiltInPad(it) }
                .forEach { claimedPorts.remove(controllerIdOf(it)) }
        }
        reseatLaunchPad()
    }

    fun playerOneControllerId(): String? =
        sessionSeats.entries.firstOrNull { it.value == 0 }?.key
            ?: controllerOrder.entries.firstOrNull { it.value == 0 }?.key
            ?: claimedPorts.entries.firstOrNull { it.value == 0 }?.key

    override fun getPort(device: InputDevice): Int {
        if (device.id == android.view.KeyCharacterMap.VIRTUAL_KEYBOARD) return 0
        val controllerId = controllerIdOf(device)
        if (sessionSeats.isNotEmpty() && !ControllerDetector.isBuiltInPad(device)) reseatLaunchPad()
        seatOf(controllerId)?.let { return it }
        claimPort(controllerId, lowestPort = if (yieldsPlayerOne(device)) 1 else 0)
        return seatOf(controllerId) ?: 0
    }

    /**
     * The port this pad holds right now. Never seats the pad, so it never takes player one.
     */
    fun peekPort(device: InputDevice): Int? = seatOf(controllerIdOf(device))

    fun getPort(controllerId: String, fallbackControllerNumber: Int): Int {
        return seatOf(controllerId) ?: (fallbackControllerNumber - 1).coerceAtLeast(0)
    }

    fun hasCustomOrder(): Boolean = controllerOrder.isNotEmpty()

    private fun seatOf(controllerId: String): Int? =
        sessionSeats[controllerId] ?: controllerOrder[controllerId] ?: claimedPorts[controllerId]

    private fun claimPort(controllerId: String, lowestPort: Int) {
        val taken = controllerOrder.values.toSet() + claimedPorts.values.toSet() + sessionSeats.values.toSet()
        var port = lowestPort
        while (port in taken) port++
        claimedPorts[controllerId] = port
        reseatLaunchPad()
        onPortClaimed?.invoke(controllerId, seatOf(controllerId) ?: port)
    }

    private fun reseatLaunchPad() {
        val pads = connectedGamepads()
        val loneBuiltIn = pads.singleOrNull { ControllerDetector.isBuiltInPad(it) }
            ?.takeIf { pads.none { pad -> !ControllerDetector.isBuiltInPad(pad) } }
        val playerOneHeldByAbsentPad = controllerOrder.entries.firstOrNull { it.value == 0 }?.key
            ?.let { seated -> pads.none { controllerIdOf(it) == seated } } == true
        if (loneBuiltIn != null && playerOneHeldByAbsentPad) {
            sessionSeats = mapOf(controllerIdOf(loneBuiltIn) to 0)
            return
        }
        if (!docked || launchPadId == null) {
            sessionSeats = emptyMap()
            return
        }
        val connected = connectedGamepads().map { pad ->
            SeatedPad(controllerIdOf(pad), ControllerDetector.isBuiltInPad(pad))
        }
        sessionSeats = ControllerSeating.launchPadSeats(
            docked = docked,
            launchPadId = launchPadId,
            connected = connected,
            seats = claimedPorts + controllerOrder
        )
    }

    private fun yieldsPlayerOne(device: InputDevice): Boolean {
        if (!device.isPhysicalGamepad() || !ControllerDetector.isBuiltInPad(device)) return false
        return connectedGamepads().any { pad ->
            !ControllerDetector.isBuiltInPad(pad) && isUnseated(controllerIdOf(pad))
        }
    }

    private fun isUnseated(controllerId: String): Boolean = seatOf(controllerId) == null

    private fun connectedGamepads(): List<InputDevice> =
        InputDevice.getDeviceIds().toList()
            .mapNotNull { InputDevice.getDevice(it) }
            .filter { it.isPhysicalGamepad() }
}
