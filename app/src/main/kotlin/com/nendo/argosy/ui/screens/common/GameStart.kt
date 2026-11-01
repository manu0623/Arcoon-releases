package com.nendo.argosy.ui.screens.common

/**
 * How a launch hands its game to [GameStartRouter]: the game whose display target places it, a
 * one-off display that overrides that target, and what the caller shows when the window fails to
 * open.
 */
data class GameStart(
    val gameId: Long,
    val overrideDisplayId: Int? = null,
    val onFailure: ((Throwable) -> Unit)? = null
)
