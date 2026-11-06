package com.nendo.argosy.ui.screens.library

import com.nendo.argosy.data.model.GameSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * The quick menu is Cocoon's game menu: Launch, the full game page, achievements for server
 * games, then the library actions. Everything else (edit, scrape, delete, variants, files) lives
 * on the game's own page, so none of it belongs in this list.
 */
class LibraryQuickMenuRowsTest {

    private fun game(
        isDownloaded: Boolean = false,
        canRemoveFromLibrary: Boolean = false,
        isRommGame: Boolean = true
    ) = LibraryGameUi(
        id = 1,
        title = "Game",
        sortTitle = "game",
        platformId = 7,
        platformSlug = "snes",
        platformDisplayName = "SNES",
        coverPath = null,
        source = if (isRommGame) GameSource.ROMM_REMOTE else GameSource.LOCAL_ONLY,
        isFavorite = false,
        isDownloaded = isDownloaded,
        isRommGame = isRommGame,
        canRemoveFromLibrary = canRemoveFromLibrary,
        isAndroidApp = false,
        emulatorName = null,
        listDetails = com.nendo.argosy.ui.common.GameListDetails()
    )

    @Test
    fun `the menu opens on launch and closes on the platform resync`() {
        val rows = libraryQuickMenuRows(game(), isCustomGridHome = true, hasSiblingGroup = false)

        assertEquals(LibraryQuickMenuRow.PRIMARY, rows.first())
        assertEquals(LibraryQuickMenuRow.RESYNC_PLATFORM, rows.last())
    }

    @Test
    fun `the sequence is launch, game page, achievements, then the library actions`() {
        val rows = libraryQuickMenuRows(game(), isCustomGridHome = true, hasSiblingGroup = true)

        assertEquals(
            listOf(
                LibraryQuickMenuRow.PRIMARY,
                LibraryQuickMenuRow.DETAILS,
                LibraryQuickMenuRow.ACHIEVEMENTS,
                LibraryQuickMenuRow.HIDE,
                LibraryQuickMenuRow.FAVORITE,
                LibraryQuickMenuRow.ADD_TO_COLLECTION,
                LibraryQuickMenuRow.ADD_TO_GRID,
                LibraryQuickMenuRow.RESYNC_PLATFORM
            ),
            rows
        )
    }

    @Test
    fun `a game that is not on the server offers no achievements row`() {
        val rows = libraryQuickMenuRows(
            game(isRommGame = false),
            isCustomGridHome = true,
            hasSiblingGroup = false
        )

        assertFalse(rows.contains(LibraryQuickMenuRow.ACHIEVEMENTS))
    }

    @Test
    fun `actions that live on the game page are never listed`() {
        val rows = libraryQuickMenuRows(
            game(isDownloaded = true, canRemoveFromLibrary = true),
            isCustomGridHome = true,
            hasSiblingGroup = true
        )

        listOf(
            LibraryQuickMenuRow.ACTIVE_VARIANT,
            LibraryQuickMenuRow.DELETE,
            LibraryQuickMenuRow.REMOVE_FROM_LIBRARY
        ).forEach { assertFalse("$it belongs on the game page, not the quick menu",
            rows.contains(it)) }
    }
}
