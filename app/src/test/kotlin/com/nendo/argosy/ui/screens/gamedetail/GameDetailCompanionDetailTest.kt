package com.nendo.argosy.ui.screens.gamedetail

import com.nendo.argosy.core.game.AchievementUi
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.ui.dualscreen.CompanionDetail
import com.nendo.argosy.ui.dualscreen.CompanionGameStats
import org.junit.Assert.assertEquals
import org.junit.Test

class GameDetailCompanionDetailTest {
    @Test
    fun `detail publishes artwork and typed stats to the shared showcase`() {
        val game = GameEntity(
            id = 42,
            platformId = 1,
            platformSlug = "snes",
            title = "Test game",
            sortTitle = "Test game",
            localPath = null,
            rommId = 42,
            igdbId = null,
            source = GameSource.ROMM_REMOTE
        ).toGameDetailUi(
            art = null,
            screenshotRows = emptyList(),
            platformName = "SNES",
            emulatorName = null,
            canPlay = false
        ).copy(
            coverPath = "/cover.png",
            backgroundPath = "/background.png",
            boxSpinePath = "/spine.png",
            developer = "Studio",
            releaseYear = 1995,
            players = "1-2",
            genre = "Platformer",
            rating = 84f,
            userRating = 8,
            userDifficulty = 3,
            playTimeMinutes = 120,
            timeToBeatMainSec = 3600,
            timeToBeatCompletionistSec = 7200,
            status = "finished",
            achievements = listOf(
                AchievementUi(1, "First", null, 5, null, null, isUnlocked = true),
                AchievementUi(2, "Second", null, 5, null, null)
            )
        )

        assertEquals(
            CompanionDetail(
                title = "Test game",
                subtitle = "SNES",
                platformSlug = "snes",
                artUrl = "/cover.png",
                backdropUrl = "/background.png",
                backdropIsScreenshotOrArt = true,
                spineUrl = "/spine.png",
                isGameTitle = true,
                gameId = 42,
                stats = CompanionGameStats(
                    developer = "Studio",
                    releaseYear = 1995,
                    players = "1-2",
                    genre = "Platformer",
                    communityRating = 84f,
                    userRating = 8,
                    userDifficulty = 3,
                    playTimeMinutes = 120,
                    timeToBeatMainSec = 3600,
                    timeToBeatCompletionistSec = 7200,
                    status = "finished",
                    achievementCount = 2,
                    earnedAchievementCount = 1
                )
            ),
            game.toCompanionDetail()
        )
    }
}
