package com.nendo.argosy.hardware

enum class LidSleepVerdict {
    SLEEP,
    IDLE,
    EXTERNAL_DISPLAY,
    RATE_LIMITED
}

data class LidSleepState(
    val enabled: Boolean,
    val lidClosed: Boolean,
    val interactive: Boolean,
    val externalDisplayInUse: Boolean,
    val attemptsInWindow: Int
)

object LidSleepDecision {

    const val WAKE_GRACE_MS = 1000L
    const val ATTEMPT_WINDOW_MS = 60_000L
    const val MAX_ATTEMPTS_PER_WINDOW = 3

    private const val SW_LID_BIT = 1
    private val HALL_SWITCH_DEVICE = Regex("""^\s*Device \d+: hall_switch\s*$""")
    private val NEXT_DEVICE = Regex("""^\s*Device \d+: """)
    private val SWITCH_VALUES = Regex("""^\s*SwitchValues:\s*(\d+)\s*$""")

    /**
     * Whether the kernel lid switch reports the lid closed, read from the `hall_switch` device in a
     * `dumpsys input` dump. Null when the dump has no such device or no switch state for it.
     */
    fun parseLidClosed(dumpsysInput: List<String>): Boolean? {
        val start = dumpsysInput.indexOfFirst { HALL_SWITCH_DEVICE.matches(it) }
        if (start < 0) return null
        for (line in dumpsysInput.drop(start + 1)) {
            if (NEXT_DEVICE.containsMatchIn(line)) return null
            val values = SWITCH_VALUES.find(line)?.groupValues?.get(1)?.toIntOrNull() ?: continue
            return values and SW_LID_BIT != 0
        }
        return null
    }

    fun decide(state: LidSleepState): LidSleepVerdict = when {
        !state.enabled || !state.lidClosed || !state.interactive -> LidSleepVerdict.IDLE
        state.externalDisplayInUse -> LidSleepVerdict.EXTERNAL_DISPLAY
        state.attemptsInWindow >= MAX_ATTEMPTS_PER_WINDOW -> LidSleepVerdict.RATE_LIMITED
        else -> LidSleepVerdict.SLEEP
    }
}
