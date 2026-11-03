package com.nendo.argosy.ui.dualscreen

data class BuiltInResumeRequest(
    val gameId: Long,
    val displayId: Int,
    val requestedAtMillis: Long
)

/**
 * The slot the companion renders, or null to hold black: while [showsDashboard], any slot but
 * [PresentationSlot.InGame] is held back.
 */
fun companionSlotToRender(showsDashboard: Boolean, slot: PresentationSlot): PresentationSlot? =
    if (showsDashboard && slot !is PresentationSlot.InGame) null else slot

/**
 * How long the in-game dashboard fades in over the companion's black hold: [arrivalMs] while the
 * companion arrives from the hold, zero under reduced motion or when no hold applies.
 */
fun dashboardArrivalMs(showsDashboard: Boolean, reduced: Boolean, arrivalMs: Int): Int =
    if (showsDashboard && !reduced) arrivalMs else 0

fun primaryWillShowDashboard(
    primaryDisplayId: Int?,
    gameDisplayId: Int?,
    showcaseDisplayId: Int?
): Boolean {
    if (gameDisplayId == null || gameDisplayId == primaryDisplayId) return false
    return showcaseDisplayId == null || gameDisplayId == showcaseDisplayId
}

/**
 * How long the companion's launcher dissolves into the black hold during a game handoff:
 * [veilMs], or zero under reduced motion or when no game dashboard is taking over.
 */
fun companionHandoffMs(showsDashboard: Boolean, reduced: Boolean, veilMs: Int): Int =
    if (showsDashboard && !reduced) veilMs else 0
