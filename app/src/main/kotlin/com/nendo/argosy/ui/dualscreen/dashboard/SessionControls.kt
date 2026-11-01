package com.nendo.argosy.ui.dualscreen.dashboard

data class DashboardQuickState(
    val slotNumber: Int,
    val savedAtMillis: Long,
    val screenshotPath: String?
)

/**
 * What the running game lets the in-game dashboard do, published by the game's activity. The
 * quick states are the rolling quick-save ring, newest first.
 */
data class SessionControls(
    val quickStates: List<DashboardQuickState> = emptyList(),
    val cheatsAvailable: Boolean = false,
    val settingsAvailable: Boolean = false
)
