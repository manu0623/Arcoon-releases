package com.nendo.argosy.domain.model

/**
 * What the feature tiles on a curated page draw from, read independently of the tile list: the
 * game the continue tile resumes and what the RetroAchievements tile shows. Either is null when no
 * tile of that kind is on the page.
 */
data class FeatureTileContent(
    val continueGameId: Long?,
    val raSummary: RaTileContent?
)
