package com.nendo.argosy.ui.screens.common

import android.app.Activity
import android.content.Context
import android.content.Intent
import com.nendo.argosy.data.emulator.EXTRA_ALREADY_LAUNCHED
import com.nendo.argosy.util.Logger

private const val TAG = "GameActivityStarter"

/**
 * Starts a resolved game request from [activityContext] while that activity is alive, and from
 * [appContext] as a new task when it is not. A request whose emulator the launch already started
 * is reported as started without a second start.
 */
class GameActivityStarter(
    private val appContext: Context,
    private val activityContext: () -> Context?
) {
    fun start(request: GameLaunchRequest): Result<Unit> {
        val intent = request.intent
        if (intent.getBooleanExtra(EXTRA_ALREADY_LAUNCHED, false)) return Result.success(Unit)
        val host = activityContext()?.takeIf { it.isLiveActivity() }
        val launchContext = host ?: appContext.also {
            Logger.info(TAG, "No live launcher activity, starting the game as a new task")
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            launchContext.startActivity(intent, request.options)
            Result.success(Unit)
        } catch (e: RuntimeException) {
            Logger.error(TAG, "Game start failed for ${intent.component}", e)
            Result.failure(e)
        }
    }

    private fun Context.isLiveActivity(): Boolean =
        this is Activity && !isFinishing && !isDestroyed
}
