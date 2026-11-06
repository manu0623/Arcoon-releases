package com.nendo.argosy.ui.screens.library

enum class LibraryQuickMenuRow(val isDangerous: Boolean = false) {
    PRIMARY,
    /** Cocoon's Launch ▸ screen choice, for one launch on a two-screen device. */
    LAUNCH_SCREEN_1,
    LAUNCH_SCREEN_2,
    ACHIEVEMENTS,
    FAVORITE,
    DETAILS,
    ADD_TO_COLLECTION,
    ADD_TO_GRID,
    ACTIVE_VARIANT,
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
    // Cocoon's game menu: Launch, Achievements, Hide, Favorite, Add to... The rest of Argosy's
    // game actions (edit, scrape, delete, variants, files...) live on the game's own page.
    // Launch carries the two-screen choice behind a right press instead of listing every screen.
    add(LibraryQuickMenuRow.PRIMARY)
    // Argosy's full game page (saves, files, play history, info, edit and scrape)
    add(LibraryQuickMenuRow.DETAILS)
    if (game.isRommGame) add(LibraryQuickMenuRow.ACHIEVEMENTS)
    add(LibraryQuickMenuRow.HIDE)
    add(LibraryQuickMenuRow.FAVORITE)
    add(LibraryQuickMenuRow.ADD_TO_COLLECTION)
    // On the Cocoon board this is Cocoon's "Add to Home".
    if (isCustomGridHome || !com.nendo.argosy.ui.cocoon.CocoonMode.useClassicHome.value) add(LibraryQuickMenuRow.ADD_TO_GRID)
    add(LibraryQuickMenuRow.RESYNC_PLATFORM)
}

/**
 * Whether Launch has a screen to ask about: the game is here and the device has two screens.
 * One screen means no choice, so Launch starts the game straight away and carries no chevron.
 */
fun launchScreenChoiceFor(game: LibraryGameUi): Boolean =
    game.isDownloaded &&
        (com.nendo.argosy.DualScreenManagerHolder.instance?.focusableDisplays()?.size ?: 0) > 1

/**
 * The rows the menu shows right now. Launch ▸ a screen replaces the whole list in place, so the
 * screen has to draw from this rather than from [libraryQuickMenuRows], or the picker never
 * appears even though the view model has already swapped the rows.
 */
fun LibraryUiState.quickMenuRowList(): List<LibraryQuickMenuRow> {
    val game = focusedGame ?: return emptyList()
    if (quickMenuLaunchOpen && launchScreenChoiceFor(game)) {
        return listOf(LibraryQuickMenuRow.LAUNCH_SCREEN_1, LibraryQuickMenuRow.LAUNCH_SCREEN_2)
    }
    return libraryQuickMenuRows(game, isCustomGridHome, quickMenuHasSiblingGroup)
}
