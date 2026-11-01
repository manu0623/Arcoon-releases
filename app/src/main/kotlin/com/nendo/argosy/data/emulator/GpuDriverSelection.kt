package com.nendo.argosy.data.emulator

object GpuDriverToken {
    const val SYSTEM = "system"
    private const val CUSTOM_PREFIX = "custom:"

    fun custom(driverId: String): String = CUSTOM_PREFIX + driverId

    fun customIdOf(token: String?): String? =
        token?.takeIf { it.startsWith(CUSTOM_PREFIX) }
            ?.substring(CUSTOM_PREFIX.length)
            ?.takeIf { it.isNotBlank() }
}

/**
 * The driver a launch asked for and the custom driver it will load. [customDriverId] is null when
 * the system driver runs, including when [requested] names a custom driver that is not installed
 * or that this device cannot load.
 */
data class GpuDriverChoice(
    val requested: String,
    val customDriverId: String?
) {
    val fellBackToSystem: Boolean
        get() = GpuDriverToken.customIdOf(requested) != null && customDriverId == null
}

object GpuDriverPrecedence {

    fun select(game: String?, platform: String?, global: String): String =
        game?.takeIf { it.isNotBlank() }
            ?: platform?.takeIf { it.isNotBlank() }
            ?: global.takeIf { it.isNotBlank() }
            ?: GpuDriverToken.SYSTEM

    fun resolve(
        requested: String,
        installedIds: Set<String>,
        customDriversSupported: Boolean
    ): GpuDriverChoice {
        val customId = GpuDriverToken.customIdOf(requested)
            ?.takeIf { customDriversSupported && it in installedIds }
        return GpuDriverChoice(requested, customId)
    }
}
