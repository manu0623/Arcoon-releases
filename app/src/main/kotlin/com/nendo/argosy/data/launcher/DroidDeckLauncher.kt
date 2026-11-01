package com.nendo.argosy.data.launcher

import android.content.ComponentName
import android.content.Intent

object DroidDeckLauncher : SteamLauncher {
    override val packageName = "com.droiddeck.launcher"
    override val displayName = "DroidDeck"

    override fun createLaunchIntent(steamAppId: Long): Intent = Intent().apply {
        component = ComponentName(packageName, "$packageName.MainActivity")
        action = "com.droiddeck.launcher.LAUNCH_GAME"
        putExtra("app_id", steamAppId.toInt())
    }
}
