package com.nendo.argosy.util

import android.content.Context
import com.nendo.argosy.data.preferences.DisplayPreferencesRepository
import com.nendo.argosy.util.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HomeAppAvailability @Inject constructor(
    @ApplicationContext private val context: Context,
    private val displayPreferences: DisplayPreferencesRepository
) {

    suspend fun applyAtStartup() = withContext(Dispatchers.IO) {
        val stored = displayPreferences.preferences.first().homeAppAvailable
        val defaultApplied = displayPreferences.isHomeAppDefaultApplied()
        val isDefaultHome = SecondaryHomeComponent.isDefaultHome(context)
        val desktopModeSupported = DesktopMode.isSupported(context)
        val startup = HomeAppComponent.startupAvailability(
            stored = stored,
            defaultApplied = defaultApplied,
            desktopModeSupported = desktopModeSupported,
            isDefaultHome = isDefaultHome
        )
        Logger.info(
            TAG,
            "applyAtStartup | stored=$stored defaultApplied=$defaultApplied desktopModeSupported=$desktopModeSupported " +
                "isDefaultHome=$isDefaultHome -> available=${startup.available} persistDefault=${startup.persistDefault}"
        )
        if (startup.persistDefault) displayPreferences.setHomeAppAvailable(startup.available)
        HomeAppComponent.setEnabled(
            context,
            HomeAppComponent.componentEnabled(startup.available, isDefaultHome)
        )
    }

    private companion object {
        const val TAG = "HomeAppAvailability"
    }

    suspend fun isDefaultHome(): Boolean = withContext(Dispatchers.IO) {
        SecondaryHomeComponent.isDefaultHome(context)
    }

    suspend fun setAvailable(available: Boolean): Boolean = withContext(Dispatchers.IO) {
        val isDefaultHome = SecondaryHomeComponent.isDefaultHome(context)
        val applied = HomeAppComponent.componentEnabled(available, isDefaultHome)
        displayPreferences.setHomeAppAvailable(applied)
        HomeAppComponent.setEnabled(context, applied)
        applied
    }
}
