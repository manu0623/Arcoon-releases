package com.nendo.argosy.util

import android.content.Context
import android.content.res.Configuration

/**
 * Samsung DeX detection through the fields Samsung documents on [Configuration]: DeX is active
 * when the instance field `semDesktopModeEnabled` equals the static `SEM_DESKTOP_MODE_ENABLED`.
 * Any device without those fields reads as inactive.
 */
object DesktopMode {
    private const val ENABLED_CONSTANT = "SEM_DESKTOP_MODE_ENABLED"
    private const val MODE_FIELD = "semDesktopModeEnabled"
    private const val DESKTOP_MODE_FEATURE = "com.sec.feature.desktopmode"

    fun isSupported(context: Context): Boolean = isSupported(
        hasFeature = context.packageManager.hasSystemFeature(DESKTOP_MODE_FEATURE),
        configurationClass = Configuration::class.java
    )

    internal fun isSupported(hasFeature: Boolean, configurationClass: Class<*>): Boolean =
        hasFeature || readInt { configurationClass.getField(ENABLED_CONSTANT).getInt(null) } != null

    fun isActive(context: Context): Boolean = isActive(context.resources.configuration)

    fun isActive(configuration: Configuration): Boolean =
        isActive(configuration.javaClass, configuration)

    internal fun isActive(configurationClass: Class<*>, configuration: Any): Boolean {
        val enabled = readInt { configurationClass.getField(ENABLED_CONSTANT).getInt(null) }
            ?: return false
        val mode = readInt { configurationClass.getField(MODE_FIELD).getInt(configuration) }
            ?: return false
        return enabled == mode
    }

    private inline fun readInt(read: () -> Int): Int? = try {
        read()
    } catch (_: ReflectiveOperationException) {
        null
    } catch (_: RuntimeException) {
        null
    }
}
