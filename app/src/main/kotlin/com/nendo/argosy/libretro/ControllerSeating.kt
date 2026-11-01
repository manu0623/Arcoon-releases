package com.nendo.argosy.libretro

data class SeatedPad(
    val controllerId: String,
    val isBuiltIn: Boolean
)

object ControllerSeating {
    const val PORT_COUNT = 4

    /**
     * Session seats for the pad that pressed Play while an external display is docked. When that
     * pad is not built in and a built-in pad holds player one, the launching pad takes player one
     * and the built-in pad moves to the lowest free port above it. Empty when the rule does not
     * apply. [seats] are the seats in force before the rule, keyed by controller id.
     */
    fun launchPadSeats(
        docked: Boolean,
        launchPadId: String?,
        connected: List<SeatedPad>,
        seats: Map<String, Int>
    ): Map<String, Int> {
        if (!docked || launchPadId == null) return emptyMap()
        val launchPad = connected.firstOrNull { it.controllerId == launchPadId } ?: return emptyMap()
        if (launchPad.isBuiltIn) return emptyMap()
        val playerOne = seats.entries.firstOrNull { it.value == 0 }?.key ?: return emptyMap()
        if (playerOne == launchPadId) return emptyMap()
        val builtInIds = connected.filter { it.isBuiltIn }.map { it.controllerId }.toSet()
        if (playerOne !in builtInIds) return emptyMap()
        val taken = seats.filterKeys { it != launchPadId && it != playerOne }.values.toSet()
        var port = 1
        while (port in taken) port++
        return mapOf(launchPadId to 0, playerOne to port)
    }

    /**
     * Whether a session should ask every pad for a seat before play starts: more than two pads, or
     * two with neither built in, and at least one of them without a saved seat.
     */
    fun shouldPromptForSeats(connected: List<SeatedPad>, savedSeatIds: Set<String>): Boolean {
        val crowded = connected.size > 2 || (connected.size == 2 && connected.none { it.isBuiltIn })
        if (!crowded) return false
        return connected.any { it.controllerId !in savedSeatIds }
    }
}
