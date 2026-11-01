package com.nendo.argosy.ui.screens.library

enum class LibraryQuickMenuRow(val isDangerous: Boolean = false) {
    PRIMARY,
    /** Cocoon's Launch ▸ screen choice, for one launch on a two-screen device. */
    LAUNCH_SCREEN_1,
    LAUNCH_SCREEN_2,
    EDIT,
    ACHIEVEMENTS,
    JINGLE,
    FAVORITE,
    DETAILS,
    ADD_TO_COLLECTION,
    ADD_TO_GRID,
    ACTIVE_VARIANT,
    REFRESH,
    RESYNC_PLATFORM,
    DELETE(isDangerous = true),
    REMOVE_FROM_LIBRARY(isDangerous = true),
    HIDE
}

fun libraryQuickMenuRows(
    game: LibraryGameUi,
    isCustomGridHome: Boolean,
    hasSiblingGroup: Boolean
): List<LibraryQuickMenuRow> = buildList {
    // Cocoon's game menu: Launch, Edit, Achievements, Hide, Favorite, Add to... The rest of
    // Argosy's game actions (delete, refresh, variants, files...) live in the Edit Game sheet.
    add(LibraryQuickMenuRow.PRIMARY)
    if (game.isDownloaded && (com.nendo.argosy.DualScreenManagerHolder.instance?.focusableDisplays()?.size ?: 0) > 1) {
        add(LibraryQuickMenuRow.LAUNCH_SCREEN_1)
        add(LibraryQuickMenuRow.LAUNCH_SCREEN_2)
    }
    add(LibraryQuickMenuRow.EDIT)
    // Argosy's full game page (saves, files, play history, info)
    add(LibraryQuickMenuRow.DETAILS)
    // Cocoon's Scrape Game: fetch this game's info and art again
    add(LibraryQuickMenuRow.REFRESH)
    if (game.isRommGame) add(LibraryQuickMenuRow.ACHIEVEMENTS)
    add(LibraryQuickMenuRow.HIDE)
    add(LibraryQuickMenuRow.FAVORITE)
    add(LibraryQuickMenuRow.JINGLE)
    add(LibraryQuickMenuRow.ADD_TO_COLLECTION)
    // On the Cocoon board this is Cocoon's "Add to Home".
    if (isCustomGridHome || !com.nendo.argosy.ui.cocoon.CocoonMode.useClassicHome.value) add(LibraryQuickMenuRow.ADD_TO_GRID)
    add(LibraryQuickMenuRow.RESYNC_PLATFORM)
}
