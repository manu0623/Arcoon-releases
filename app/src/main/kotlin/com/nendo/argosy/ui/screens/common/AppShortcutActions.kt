package com.nendo.argosy.ui.screens.common

import android.content.Intent
import android.net.Uri
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.repository.AppsRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppShortcutActions @Inject constructor(
    private val preferencesRepository: UserPreferencesRepository,
    private val appsRepository: AppsRepository
) {
    suspend fun isPinned(packageName: String): Boolean =
        packageName in preferencesRepository.preferences.first().secondaryHomeApps

    suspend fun togglePinned(packageName: String) {
        preferencesRepository.toggleDockPin(packageName)
    }

    suspend fun isHidden(packageName: String): Boolean {
        val prefs = preferencesRepository.preferences.first()
        return if (isSystemApp(packageName)) {
            packageName !in prefs.visibleSystemApps
        } else {
            packageName in prefs.hiddenApps
        }
    }

    suspend fun toggleHidden(packageName: String) {
        val prefs = preferencesRepository.preferences.first()
        if (isSystemApp(packageName)) {
            val visible = prefs.visibleSystemApps
            preferencesRepository.setVisibleSystemApps(
                if (packageName in visible) visible - packageName else visible + packageName
            )
        } else {
            val hidden = prefs.hiddenApps
            preferencesRepository.setHiddenApps(
                if (packageName in hidden) hidden - packageName else hidden + packageName
            )
        }
    }

    fun uninstallIntent(packageName: String): Intent =
        Intent(Intent.ACTION_DELETE, Uri.parse("package:$packageName"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private suspend fun isSystemApp(packageName: String): Boolean =
        appsRepository.getInstalledApps(includeSystemApps = true)
            .firstOrNull { it.packageName == packageName }
            ?.isSystemApp == true
}
