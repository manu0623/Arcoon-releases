package com.nendo.argosy.libretro

import com.swordfish.libretrodroid.AchievementDef
import com.swordfish.libretrodroid.LibretroDroid

/**
 * The native achievement watcher a session arms and clears.
 */
interface AchievementRuntime {
    fun arm(definitions: Array<AchievementDef>, consoleId: Int)
    fun clear()
}

object NativeAchievementRuntime : AchievementRuntime {
    override fun arm(definitions: Array<AchievementDef>, consoleId: Int) {
        LibretroDroid.initAchievements(definitions, consoleId)
    }

    override fun clear() {
        LibretroDroid.clearAchievements()
    }
}
