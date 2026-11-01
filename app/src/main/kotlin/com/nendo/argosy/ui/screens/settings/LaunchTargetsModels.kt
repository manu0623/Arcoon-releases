package com.nendo.argosy.ui.screens.settings

import com.nendo.argosy.data.local.entity.PlatformEntity
import com.nendo.argosy.data.platform.LocalPlatformIds
import com.nendo.argosy.data.preferences.EmulatorDisplayTarget
import com.nendo.argosy.domain.model.ScreenRole
import com.nendo.argosy.ui.components.ListSection

data class LaunchTargetScreen(
    val key: String,
    val displayId: Int,
    val number: Int,
    val role: ScreenRole
)

sealed interface LaunchTargetEntry {
    val key: String
    val label: String
    val displayId: Int?
    val isSet: Boolean
}

data class LaunchTargetPlatformEntry(
    val platformId: Long,
    val slug: String,
    override val label: String,
    val stored: EmulatorDisplayTarget,
    val gameOverrides: Int,
    override val displayId: Int?
) : LaunchTargetEntry {
    override val key: String get() = "platform:$platformId"
    override val isSet: Boolean get() = stored != EmulatorDisplayTarget.DEFAULT
}

/**
 * [pinnedScreen] is the attached screen [pinnedKey] names, Off ones included, or null when that
 * screen is not attached.
 */
data class LaunchTargetAppEntry(
    val packageName: String,
    override val label: String,
    val pinnedKey: String?,
    val pinnedScreen: LaunchTargetScreen?,
    override val displayId: Int?
) : LaunchTargetEntry {
    override val key: String get() = "app:$packageName"
    override val isSet: Boolean get() = pinnedKey != null
}

/**
 * [screens] holds the attached screens that are not Off, in screen-number order, each in the role
 * it holds now.
 */
data class LaunchTargetsState(
    val screens: List<LaunchTargetScreen> = emptyList(),
    val platforms: List<LaunchTargetPlatformEntry> = emptyList(),
    val apps: List<LaunchTargetAppEntry> = emptyList(),
    val appsLoaded: Boolean = false,
    val platformDefaultRole: ScreenRole = ScreenRole.PRESENTATION,
    val appDefaultNumber: Int = 0,
    val pickerKey: String? = null,
    val pickerOpen: Boolean = false
) {
    val pickerEntry: LaunchTargetEntry?
        get() = pickerKey?.let { key -> platforms.firstOrNull { it.key == key } ?: apps.firstOrNull { it.key == key } }
}

data class LaunchTargetGroup(
    val screen: LaunchTargetScreen,
    val platforms: List<LaunchTargetPlatformEntry>,
    val apps: List<LaunchTargetAppEntry>
) {
    val entries: List<LaunchTargetEntry> get() = platforms + apps
}

enum class LaunchTargetBlock { PLATFORMS, APPS }

sealed interface LaunchTargetsRow {
    val key: String

    data object Intro : LaunchTargetsRow {
        override val key: String = "intro"
    }

    data class Gap(val number: Int) : LaunchTargetsRow {
        override val key: String get() = "gap:$number"
    }

    data class Heading(val screen: LaunchTargetScreen) : LaunchTargetsRow {
        override val key: String get() = "heading:${screen.number}"
    }

    data class BlockLabel(val number: Int, val block: LaunchTargetBlock, val count: Int) : LaunchTargetsRow {
        override val key: String get() = "label:$number:${block.name}"
    }

    data class Entry(val entry: LaunchTargetEntry) : LaunchTargetsRow {
        override val key: String get() = entry.key
    }

    data class Loading(val number: Int) : LaunchTargetsRow {
        override val key: String get() = "loading:$number"
    }

    data class Empty(val number: Int) : LaunchTargetsRow {
        override val key: String get() = "empty:$number"
    }
}

/**
 * [sections] has one span per group, empty groups included so the rail still names them; an
 * empty group's focus span is empty and [jumpSections] leaves it out.
 */
data class LaunchTargetsLayout(
    val rows: List<LaunchTargetsRow>,
    val entries: List<LaunchTargetEntry>,
    val entryListIndices: List<Int>,
    val groups: List<LaunchTargetGroup>,
    val sections: List<ListSection>
) {
    val maxFocusIndex: Int get() = (entries.size - 1).coerceAtLeast(0)

    val jumpSections: List<ListSection> get() = sections.filter { it.focusEndIndex >= it.focusStartIndex }

    fun entryAt(focusIndex: Int): LaunchTargetEntry? = entries.getOrNull(focusIndex)

    fun focusIndexOf(key: String?): Int = entries.indexOfFirst { it.key == key }

    fun listIndexOf(focusIndex: Int): Int = entryListIndices.getOrElse(focusIndex) { focusIndex }

    fun screenNumberOf(key: String): Int? =
        groups.firstOrNull { group -> group.entries.any { it.key == key } }?.screen?.number
}

internal val EmulatorDisplayTarget.screenRole: ScreenRole
    get() = when (this) {
        EmulatorDisplayTarget.PRIMARY -> ScreenRole.PRIMARY
        EmulatorDisplayTarget.APP_SCREEN -> ScreenRole.APP_TARGET
        EmulatorDisplayTarget.PRESENTATION, EmulatorDisplayTarget.DEFAULT -> ScreenRole.PRESENTATION
    }

internal val ScreenRole.displayTarget: EmulatorDisplayTarget?
    get() = when (this) {
        ScreenRole.PRIMARY -> EmulatorDisplayTarget.PRIMARY
        ScreenRole.PRESENTATION -> EmulatorDisplayTarget.PRESENTATION
        ScreenRole.APP_TARGET -> EmulatorDisplayTarget.APP_SCREEN
        ScreenRole.OFF -> null
    }

internal fun currentScreenRole(
    displayId: Int,
    layoutRole: ScreenRole,
    roleDisplayIds: Pair<Int, Int>?,
    appScreenDisplayId: Int?
): ScreenRole = when {
    layoutRole == ScreenRole.OFF -> ScreenRole.OFF
    roleDisplayIds?.first == displayId -> ScreenRole.PRIMARY
    roleDisplayIds?.second == displayId -> ScreenRole.PRESENTATION
    appScreenDisplayId == displayId -> ScreenRole.APP_TARGET
    else -> layoutRole
}

internal fun launchTargetPlatforms(
    platforms: List<PlatformEntity>,
    showsDisplayTarget: Boolean
): List<PlatformEntity> =
    if (!showsDisplayTarget) {
        emptyList()
    } else {
        platforms.inPlatformsListOrder { it }
            .filter { it.gameCount > 0 && it.id != LocalPlatformIds.ANDROID }
    }

internal fun LaunchTargetsState.groups(): List<LaunchTargetGroup> {
    val fallback = screens.firstOrNull() ?: return emptyList()
    fun homeOf(entry: LaunchTargetEntry): Int =
        (screens.firstOrNull { it.displayId == entry.displayId } ?: fallback).number
    val platformsByScreen = platforms.groupBy { homeOf(it) }
    val appsByScreen = apps.sortedBy { it.label.lowercase() }.groupBy { homeOf(it) }
    return screens.map { screen ->
        LaunchTargetGroup(
            screen = screen,
            platforms = platformsByScreen[screen.number].orEmpty(),
            apps = appsByScreen[screen.number].orEmpty()
        )
    }
}

internal fun launchTargetsLayout(state: LaunchTargetsState): LaunchTargetsLayout {
    val groups = state.groups()
    val rows = mutableListOf<LaunchTargetsRow>(LaunchTargetsRow.Intro)
    val entries = mutableListOf<LaunchTargetEntry>()
    val entryListIndices = mutableListOf<Int>()

    fun addBlock(number: Int, block: LaunchTargetBlock, items: List<LaunchTargetEntry>) {
        if (items.isEmpty()) return
        rows += LaunchTargetsRow.BlockLabel(number, block, items.size)
        items.forEach { entry ->
            entryListIndices += rows.size
            rows += LaunchTargetsRow.Entry(entry)
            entries += entry
        }
    }

    val sections = groups.mapIndexed { index, group ->
        val number = group.screen.number
        if (index > 0) rows += LaunchTargetsRow.Gap(number)
        val listStart = rows.size
        val focusStart = entries.size
        rows += LaunchTargetsRow.Heading(group.screen)
        addBlock(number, LaunchTargetBlock.PLATFORMS, group.platforms)
        when {
            !state.appsLoaded -> rows += LaunchTargetsRow.Loading(number)
            group.apps.isNotEmpty() -> addBlock(number, LaunchTargetBlock.APPS, group.apps)
            group.platforms.isEmpty() -> rows += LaunchTargetsRow.Empty(number)
        }
        ListSection(
            listStartIndex = listStart,
            listEndIndex = rows.lastIndex,
            focusStartIndex = focusStart,
            focusEndIndex = entries.size - 1
        )
    }
    return LaunchTargetsLayout(rows.toList(), entries.toList(), entryListIndices.toList(), groups, sections)
}

internal fun LaunchTargetsState.pickerSelection(entry: LaunchTargetEntry): Int {
    if (!entry.isSet) return 0
    val index = when (entry) {
        is LaunchTargetPlatformEntry -> screens.indexOfFirst { it.role == entry.stored.screenRole }
        is LaunchTargetAppEntry -> screens.indexOfFirst { it.key == entry.pinnedKey }
    }
    return if (index < 0) -1 else index + 1
}

internal fun LaunchTargetsState.isPickable(index: Int): Boolean = index in 0..screens.size

internal fun LaunchTargetsState.platformPick(index: Int): EmulatorDisplayTarget? =
    if (index == 0) EmulatorDisplayTarget.DEFAULT else screens.getOrNull(index - 1)?.role?.displayTarget

internal fun LaunchTargetsState.appPick(index: Int): String? = screens.getOrNull(index - 1)?.key

internal fun refocusByKey(before: LaunchTargetsLayout, after: LaunchTargetsLayout, focusIndex: Int): Int =
    after.focusIndexOf(before.entryAt(focusIndex)?.key).takeIf { it >= 0 }
        ?: focusIndex.coerceIn(0, after.maxFocusIndex)

internal fun refocusAfterCommit(
    before: LaunchTargetsLayout,
    after: LaunchTargetsLayout,
    committedKey: String,
    focusIndex: Int
): Int {
    val clamped = focusIndex.coerceIn(0, after.maxFocusIndex)
    if (before.screenNumberOf(committedKey) == after.screenNumberOf(committedKey)) {
        return after.focusIndexOf(committedKey).takeIf { it >= 0 } ?: clamped
    }
    val committedIndex = before.focusIndexOf(committedKey)
    if (committedIndex < 0) return clamped
    return listOfNotNull(before.entryAt(committedIndex + 1), before.entryAt(committedIndex - 1))
        .map { after.focusIndexOf(it.key) }
        .firstOrNull { it >= 0 }
        ?: clamped
}
