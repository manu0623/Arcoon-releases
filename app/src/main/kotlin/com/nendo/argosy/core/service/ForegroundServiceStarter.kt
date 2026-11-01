package com.nendo.argosy.core.service

import android.content.Context
import android.content.Intent
import com.nendo.argosy.util.Logger

private const val TAG = "ForegroundServiceStarter"

/**
 * Starts a foreground service without letting Android's background-start rule take the caller down
 * with it. Callers sit inside a `collect` on a long-lived scope, where an uncaught
 * `ForegroundServiceStartNotAllowedException` ends the collector and no service starts again for
 * the life of the process. Returns whether the service was asked to start.
 */
fun Context.startForegroundServiceSafely(intent: Intent): Boolean = try {
    startForegroundService(intent)
    true
} catch (e: Exception) {
    Logger.warn(TAG, "start refused for ${intent.component?.shortClassName}: ${e.message}")
    false
}

/**
 * Starts a background service under the same protection. Android refuses a background start from a
 * process that is not itself foreground, and the throw reaches whatever scope made the call.
 * Returns whether the service was asked to start.
 */
fun Context.startServiceSafely(intent: Intent): Boolean = try {
    startService(intent)
    true
} catch (e: Exception) {
    Logger.warn(TAG, "background start refused for ${intent.component?.shortClassName}: ${e.message}")
    false
}
