package com.nendo.argosy.ui.dualscreen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import com.nendo.argosy.hardware.CompanionInGameState
import com.nendo.argosy.ui.cocoon.CocoonGameHero

/**
 * The running game's hero on the screen the game is not on, over the launcher's app bar: its art
 * with its logo centred, settling into black a few seconds into the session (see [InGameSettle]).
 */
@Composable
fun InGameHero(
    state: CompanionInGameState,
    modifier: Modifier = Modifier,
    appBar: @Composable (Modifier) -> Unit = {}
) {
    val detail = remember(state.gameId, state.title, state.platformName, state.coverPath, state.backgroundPath) {
        CompanionDetail(
            title = state.title,
            subtitle = state.platformName.takeIf { it.isNotBlank() },
            artUrl = state.coverPath,
            backdropUrl = state.backgroundPath ?: state.coverPath,
            isGameTitle = true,
            gameId = state.gameId.takeIf { it > 0 }
        )
    }
    InGameSettle(
        gameId = state.gameId,
        sessionStartTimeMillis = state.sessionStartTimeMillis,
        modifier = modifier
    ) { rest ->
        CocoonGameHero(
            rawDetail = detail,
            modifier = Modifier.fillMaxSize(),
            showInfo = false,
            dim = rest
        )
        InGameChrome(
            lit = 1f - rest,
            earnedAchievements = state.earnedAchievementCount,
            achievements = state.achievementCount
        )
        // Once settled the app bar leaves the composition: an invisible one would still take the
        // taps meant to wake the screen, and launch an app instead.
        if (rest < 1f) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .graphicsLayer { alpha = 1f - rest }
            ) {
                appBar(Modifier)
            }
        }
    }
}
