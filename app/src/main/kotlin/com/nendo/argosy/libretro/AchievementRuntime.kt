package com.nendo.argosy.libretro

import com.swordfish.libretrodroid.AchievementDef
import com.swordfish.libretrodroid.LibretroDroid

/**
 * The native achievement watcher a session arms and clears.
 */
interface AchievementRuntime {
    fun arm(definitions: Array<AchievementDef>, consoleId: Int)
    fun clear()

    /** Starts the game's rich presence script on an armed runtime; false when it is rejected. */
    fun armRichPresence(script: String): Boolean = false

    /** The game's current status line from its rich presence script, or null before one exists. */
    fun richPresence(): String? = null
}

object NativeAchievementRuntime : AchievementRuntime {
    override fun arm(definitions: Array<AchievementDef>, consoleId: Int) {
        LibretroDroid.initAchievements(definitions, consoleId)
    }

    override fun clear() {
        LibretroDroid.clearAchievements()
    }

    override fun armRichPresence(script: String): Boolean = LibretroDroid.activateRichPresence(script)

    override fun richPresence(): String? =
        LibretroDroid.currentRichPresence()?.toString(Charsets.UTF_8)?.trim()?.takeIf { it.isNotEmpty() }
}
