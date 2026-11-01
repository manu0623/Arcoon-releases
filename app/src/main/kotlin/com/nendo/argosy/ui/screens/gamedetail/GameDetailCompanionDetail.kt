package com.nendo.argosy.ui.screens.gamedetail

import com.nendo.argosy.data.social.FriendActivity
import com.nendo.argosy.ui.dualscreen.CompanionDetail
import com.nendo.argosy.ui.dualscreen.CompanionGameStats

fun GameDetailUi.toCompanionDetail(friends: List<FriendActivity> = emptyList()): CompanionDetail =
    CompanionDetail(
        title = title,
        subtitle = platformName,
        platformSlug = platformSlug,
        artUrl = coverPath,
        backdropUrl = backgroundPath,
        backdropIsScreenshotOrArt = backgroundPath != null,
        isGameTitle = true,
        spineUrl = boxSpinePath,
        backUrl = boxBackPath,
        gameId = id,
        stats = CompanionGameStats(
            developer = developer,
            releaseYear = releaseYear,
            players = players,
            genre = genre,
            communityRating = rating,
            userRating = userRating,
            userDifficulty = userDifficulty,
            playTimeMinutes = playTimeMinutes,
            timeToBeatMainSec = timeToBeatMainSec,
            timeToBeatCompletionistSec = timeToBeatCompletionistSec,
            status = status,
            achievementCount = achievements.size,
            earnedAchievementCount = achievements.count { it.isUnlocked },
            friends = friends
        )
    )
