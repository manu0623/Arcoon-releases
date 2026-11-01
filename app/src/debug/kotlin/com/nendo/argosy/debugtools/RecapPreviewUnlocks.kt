package com.nendo.argosy.debugtools

import com.nendo.argosy.data.remote.romm.RomMEarnedAchievement
import com.nendo.argosy.domain.model.WindowUnlock
import com.nendo.argosy.domain.usecase.recap.UnlockWindow
import java.time.Instant

object RecapPreviewUnlocks {

    const val RECENT_LIMIT = 10

    /**
     * The newest [limit] dated unlocks of [earned], newest first, whatever their date. A hardcore
     * date wins over a softcore one for the same achievement.
     */
    fun mostRecent(earned: List<RomMEarnedAchievement>, limit: Int = RECENT_LIMIT): List<WindowUnlock> =
        UnlockWindow.unlocksWithin(earned, Instant.EPOCH, Instant.MAX).asReversed().take(limit)
}
