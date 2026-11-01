package com.nendo.argosy.hardware

import android.view.Display

enum class CompanionStopCause {
    FINISHING,
    RECREATING,
    DISPLAY_GONE,
    DEVICE_ASLEEP,
    DISPLAY_OFF,
    GAME_MOVE,
    GAME_SESSION,
    FOREIGN_APP
}

object ForeignAppPolicy {

    fun stopCause(
        displayId: Int,
        finishing: Boolean,
        recreating: Boolean,
        displayPresent: Boolean,
        displayOn: Boolean,
        deviceInteractive: Boolean,
        gameMoving: Boolean,
        sessionActive: Boolean,
        gameBoundForThisDisplay: Boolean
    ): CompanionStopCause = when {
        finishing -> CompanionStopCause.FINISHING
        recreating -> CompanionStopCause.RECREATING
        displayId == Display.DEFAULT_DISPLAY || !displayPresent -> CompanionStopCause.DISPLAY_GONE
        !deviceInteractive -> CompanionStopCause.DEVICE_ASLEEP
        !displayOn -> CompanionStopCause.DISPLAY_OFF
        gameMoving -> CompanionStopCause.GAME_MOVE
        sessionActive || gameBoundForThisDisplay -> CompanionStopCause.GAME_SESSION
        else -> CompanionStopCause.FOREIGN_APP
    }

    fun displayChangeReleases(
        displayId: Int,
        displayPresent: Boolean,
        displayOn: Boolean,
        deviceInteractive: Boolean
    ): Boolean = when {
        displayId == Display.DEFAULT_DISPLAY -> false
        !displayPresent -> true
        else -> !displayOn && deviceInteractive
    }
}
