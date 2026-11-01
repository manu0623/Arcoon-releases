package com.nendo.argosy.ui.components

internal const val DOCK_MINIMUM_VISIBLE_APPS = 3

internal data class DockWidths(
    val width: Float,
    val appsWidth: Float,
    val padding: Float,
    val groupGap: Float
)

internal fun dockWidths(
    maximum: Float,
    preferredMaximum: Float,
    appCount: Int,
    slot: Float,
    gap: Float,
    padding: Float,
    appIconSize: Float,
    controlIconSize: Float,
    controlCount: Int = 2
): DockWidths {
    val layoutAppCount = if (appCount == 0 && controlCount > 1) DOCK_MINIMUM_VISIBLE_APPS else appCount
    val groupCount = ((if (layoutAppCount > 0) 1 else 0) + controlCount - 1).coerceAtLeast(0)
    val controlsWidth = slot * controlCount
    val appInset = (slot - appIconSize).coerceAtLeast(0f) / 2f
    val controlInset = (slot - controlIconSize).coerceAtLeast(0f) / 2f
    val visibleAppGap = gap + appInset * 2
    val desiredGroupGap = if (layoutAppCount > 0) {
        (visibleAppGap * 2 - appInset - controlInset).coerceAtLeast(0f)
    } else gap * 2
    val minimumVisibleApps = layoutAppCount.coerceAtMost(DOCK_MINIMUM_VISIBLE_APPS)
    val desiredMinimumApps = minimumVisibleApps * slot + (minimumVisibleApps - 1).coerceAtLeast(0) * gap
    val minimumWidth = controlsWidth + desiredMinimumApps + desiredGroupGap * groupCount + padding * 2
    val usable = preferredMaximum.coerceAtLeast(minimumWidth).coerceAtMost(maximum)
    val availableAfterControls = (usable - controlsWidth).coerceAtLeast(0f)
    val minimumApps = desiredMinimumApps.coerceAtMost(availableAfterControls)
    val groupGap = if (groupCount == 0) 0f else {
        desiredGroupGap.coerceAtMost((availableAfterControls - minimumApps) / groupCount)
    }
    val controlsAndGaps = controlsWidth + groupGap * groupCount
    val resolvedPadding = padding.coerceIn(
        0f, (availableAfterControls - minimumApps - groupGap * groupCount) / 2f
    )
    val fixed = controlsAndGaps + resolvedPadding * 2
    val desiredApps = layoutAppCount * slot + (layoutAppCount - 1).coerceAtLeast(0) * gap
    val apps = desiredApps.coerceAtMost((usable - fixed).coerceAtLeast(0f))
    return DockWidths(fixed + apps, apps, resolvedPadding, groupGap)
}

internal fun dockToolsEnabled(
    onSwapRoles: (() -> Unit)?,
    onKeyboardToggle: (() -> Unit)?,
    onPickerToggle: (() -> Unit)?,
    displays: List<DisplayFocusTarget>
): List<Boolean> = listOf(
    onSwapRoles != null,
    onKeyboardToggle != null,
    onPickerToggle != null && displays.size > 1
)

internal fun dockToolFocusMove(index: Int, delta: Int, enabled: List<Boolean>): Int {
    if (enabled.isEmpty() || enabled.none { it } || delta == 0) return index
    val direction = if (delta < 0) -1 else 1
    var next = index.coerceIn(enabled.indices)
    repeat(enabled.size) {
        next = (next + direction).mod(enabled.size)
        if (enabled[next]) return next
    }
    return index
}

internal fun dockPopupLeft(center: Int, width: Int, window: Int, inset: Int): Int {
    val minimum = inset.coerceAtMost((window - width).coerceAtLeast(0) / 2)
    return (center - width / 2).coerceIn(minimum, (window - width - minimum).coerceAtLeast(minimum))
}
