package com.nendo.argosy.ui.screens.common

import android.annotation.SuppressLint
import android.app.ActivityOptions
import android.os.Build
import android.os.Bundle
import android.window.SplashScreen

/**
 * The start options every game window opens with: its display when one is resolved, and a solid
 * colour starting window where the platform supports one.
 */
object GameLaunchOptions {

    /**
     * Whether a start on [sdkInt] can ask for the solid colour splash. The request is public from
     * API 33; on 31 and 32 only the theme's transparent icon applies.
     */
    fun solidSplashOn(sdkInt: Int): Boolean = sdkInt >= Build.VERSION_CODES.TIRAMISU

    @SuppressLint("NewApi")
    fun forDisplay(displayId: Int?, sdkInt: Int = Build.VERSION.SDK_INT): Bundle? {
        val solidSplash = solidSplashOn(sdkInt)
        if (displayId == null && !solidSplash) return null
        val options = ActivityOptions.makeBasic()
        displayId?.let { options.setLaunchDisplayId(it) }
        if (solidSplash) options.setSplashScreenStyle(SplashScreen.SPLASH_SCREEN_STYLE_SOLID_COLOR)
        return options.toBundle()
    }
}
