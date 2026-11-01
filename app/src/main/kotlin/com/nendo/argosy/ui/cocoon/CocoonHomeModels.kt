package com.nendo.argosy.ui.cocoon

import androidx.annotation.StringRes
import com.nendo.argosy.R
import kotlinx.coroutines.flow.MutableStateFlow

/** Where a board tile leads. Platforms and collections carry the id they open. */
sealed interface CocoonTileTarget {
    data class Platform(val platformId: Long) : CocoonTileTarget
    data class Collection(val collectionId: Long) : CocoonTileTarget
    data object AndroidGames : CocoonTileTarget
    data object Emulators : CocoonTileTarget
    data object Settings : CocoonTileTarget
    /** Shortcuts the user put on Home with Start ▸ New. */
    data class App(val packageName: String) : CocoonTileTarget
    data class Pod(val kind: CocoonPodKind) : CocoonTileTarget
    data class Widget(val id: String, val kind: CocoonWidgetKind, val value: String?) : CocoonTileTarget
    data class Game(val gameId: Long) : CocoonTileTarget
    data class Smart(val kind: CocoonSmartKind) : CocoonTileTarget
    /** A deliberately empty space on the board (something was placed further along). */
    data object Gap : CocoonTileTarget
    /** A slot taken up by a bigger widget next to it. */
    data class Covered(val ownerKey: String) : CocoonTileTarget
    /** A Home folder: holds other Home items (folders, apps, widgets…), and can sit in another. */
    data class Group(val id: String, val name: String) : CocoonTileTarget
}

/** Cocoon's smart folders: they fill themselves from a rule. */
enum class CocoonSmartKind(@StringRes val labelRes: Int, val themeType: String) {
    FAVORITES(R.string.cocoon_smart_favorites, "favorites"),
    RECENT(R.string.cocoon_smart_recent, "recent"),
    MOST_PLAYED(R.string.cocoon_smart_most_played, "most_played"),
    NEWLY_ADDED(R.string.cocoon_smart_newly_added, "newly_added"),
}

/** Cocoon's built-in mini apps. */
enum class CocoonPodKind(@StringRes val labelRes: Int) {
    LOG(R.string.cocoon_pod_log),
    SCREENSHOTS(R.string.cocoon_pod_screenshots),
}

/** Cocoon's Home widgets (each fills one board slot). */
enum class CocoonWidgetKind(@StringRes val labelRes: Int) {
    CLOCK(R.string.cocoon_widget_clock),
    CLOCK_ANALOG(R.string.cocoon_widget_clock_analog),
    RECENT(R.string.cocoon_widget_recent),
    LAST_PLAYED(R.string.cocoon_widget_last_played),
    RANDOM(R.string.cocoon_widget_random),
    PLAYTIME(R.string.cocoon_widget_playtime),
    MUSIC(R.string.cocoon_widget_music),
    IMAGE_PIN(R.string.cocoon_widget_image_pin),
    BLANK(R.string.cocoon_widget_blank),
}

/** Start ▸ … in Cocoon: add things to Home, edit the grid, settings. */
enum class CocoonStartRow(@StringRes val labelRes: Int) {
    NEW(R.string.cocoon_start_new),
    NEW_FOLDER(R.string.cocoon_tile_menu_new_folder),
    NEW_HOME_FOLDER(R.string.cocoon_start_new_home_folder),
    ADD_APP(R.string.cocoon_start_add_app),
    ADD_WIDGET(R.string.cocoon_start_add_widget),
    ADD_POD(R.string.cocoon_start_add_pod),
    ADD_SMART(R.string.cocoon_start_add_smart),
    EDIT_GRID(R.string.cocoon_tile_menu_edit_grid),
    SETTINGS(R.string.cocoon_tile_settings),
    SEARCH(R.string.cocoon_search_settings),
    MORE(R.string.cocoon_start_more),
}

/** Start menu rows: the main list, or the "New ▸" list of things to add. */
fun cocoonStartRows(newOpen: Boolean): List<CocoonStartRow> =
    if (newOpen) listOf(
        CocoonStartRow.NEW_FOLDER, CocoonStartRow.NEW_HOME_FOLDER, CocoonStartRow.ADD_APP,
        CocoonStartRow.ADD_WIDGET, CocoonStartRow.ADD_POD, CocoonStartRow.ADD_SMART
    ) else listOf(
        CocoonStartRow.NEW, CocoonStartRow.EDIT_GRID, CocoonStartRow.SEARCH,
        CocoonStartRow.SETTINGS, CocoonStartRow.MORE
    )

/** Which list the Start menu's second step shows. */
enum class CocoonChoice { WIDGETS, PODS, SMART, MOVE }

/** A shortcut saved on Home (apps, Pods, widgets). */
data class CocoonHomeItem(val id: String, val type: String, val value: String?)

/**
 * One tile on the board. [title] is data (a platform or collection name) when present; built-in
 * tiles carry [titleRes] instead so their label is translated.
 */
data class CocoonTile(
    val key: String,
    val target: CocoonTileTarget,
    val title: String?,
    @StringRes val titleRes: Int?,
    @StringRes val kindRes: Int,
    val glyph: CocoonGlyph,
    val platformSlug: String?,
    val colorIndex: Int,
    /** Widgets can be bigger than one slot: columns × rows. */
    val spanCols: Int = 1,
    val spanRows: Int = 1,
)

data class CocoonHomeState(
    val tiles: List<CocoonTile> = emptyList(),
    val focusedIndex: Int = 0,
    val isLoading: Boolean = true,
    /** Package names in the dock row above the board, in the user's order. */
    val dockApps: List<String> = emptyList(),
    val inDock: Boolean = false,
    /** Dock slot under the cursor: 0 is the All apps button, 1.. are the pinned apps. */
    val dockIndex: Int = 0,
    /** Cocoon's Y menu on a board tile. */
    val tileMenuOpen: Boolean = false,
    val tileMenuIndex: Int = 0,
    /** Edit Grid: the focused tile is lifted and the d-pad moves it through the board. */
    val editingGrid: Boolean = false,
    /** Cocoon's New ▸ Folder: the name prompt for a new collection folder. */
    val newFolderOpen: Boolean = false,
    /** Delete Folder asks for a second A before it deletes. */
    val deleteArmed: Boolean = false,
    /** Cocoon's Y menu on an app in the dock. */
    val dockMenuOpen: Boolean = false,
    val dockMenuIndex: Int = 0,
    /** Cocoon's app picker for an empty dock slot (or Edit App, which swaps the app in a slot). */
    val addAppOpen: Boolean = false,
    val addAppReplacing: String? = null,
    val addApps: List<CocoonPickApp> = emptyList(),
    val addAppIndex: Int = 0,
    /** The app picker adds to Home (Start ▸ Add App) instead of the dock. */
    val addAppToHome: Boolean = false,
    val startMenuOpen: Boolean = false,
    val startMenuIndex: Int = 0,
    val startNewOpen: Boolean = false,
    val choice: CocoonChoice? = null,
    val choiceIndex: Int = 0,
    /** Image Pin widget: the file browser is choosing its picture. */
    val pickingImage: Boolean = false,
    val screenshotsOpen: Boolean = false,
    /** The empty space Start ▸ New was opened from; what gets added goes there. */
    val pendingSlot: Int? = null,
    /** Y ▸ Edit App open for this package. */
    val appEditPackage: String? = null,
    /** Names of the Home folders we are inside, outermost first (empty on Home itself). */
    val groupTitles: List<String> = emptyList(),
    /** Start ▸ New Home Folder: naming it. */
    val namingGroup: Boolean = false,
    /** The tile being moved with Y ▸ Move to Folder. */
    val movingKey: String? = null,
    val searchOpen: Boolean = false,
    /** Y ▸ Console Jingle: choosing a default jingle for this platform slug. */
    val jingleForPlatform: String? = null,
    /** Y ▸ Appearance on a folder: the key of the tile being restyled. */
    val appearanceKey: String? = null,
) {
    val dockSize: Int get() = COCOON_DOCK_SIZE
    val tileMenuRows: List<CocoonTileMenuRow>
        get() = withHomeRows(when (focusedTile?.target) {
            is CocoonTileTarget.Platform -> listOf(
                CocoonTileMenuRow.OPEN, CocoonTileMenuRow.EDIT, CocoonTileMenuRow.APPEARANCE, CocoonTileMenuRow.JINGLE,
                CocoonTileMenuRow.MOVE, CocoonTileMenuRow.NEW_FOLDER, CocoonTileMenuRow.EDIT_GRID
            )
            is CocoonTileTarget.Collection -> listOf(
                CocoonTileMenuRow.OPEN, CocoonTileMenuRow.APPEARANCE, CocoonTileMenuRow.MOVE, CocoonTileMenuRow.NEW_FOLDER,
                CocoonTileMenuRow.EDIT_GRID, CocoonTileMenuRow.DELETE_FOLDER
            )
            is CocoonTileTarget.Smart -> listOf(
                CocoonTileMenuRow.OPEN, CocoonTileMenuRow.APPEARANCE, CocoonTileMenuRow.MOVE, CocoonTileMenuRow.EDIT_GRID,
                CocoonTileMenuRow.REMOVE
            )
            is CocoonTileTarget.App -> listOf(
                CocoonTileMenuRow.OPEN, CocoonTileMenuRow.EDIT_APP, CocoonTileMenuRow.MOVE, CocoonTileMenuRow.EDIT_GRID,
                CocoonTileMenuRow.REMOVE
            )
            is CocoonTileTarget.Pod, is CocoonTileTarget.Game -> listOf(
                CocoonTileMenuRow.OPEN, CocoonTileMenuRow.MOVE, CocoonTileMenuRow.EDIT_GRID, CocoonTileMenuRow.REMOVE
            )
            is CocoonTileTarget.Widget -> listOf(CocoonTileMenuRow.SIZE, CocoonTileMenuRow.MOVE, CocoonTileMenuRow.EDIT_GRID, CocoonTileMenuRow.REMOVE)
            is CocoonTileTarget.Group -> listOf(
                CocoonTileMenuRow.OPEN, CocoonTileMenuRow.APPEARANCE, CocoonTileMenuRow.MOVE, CocoonTileMenuRow.EDIT_GRID, CocoonTileMenuRow.REMOVE
            )
            else -> listOf(CocoonTileMenuRow.OPEN, CocoonTileMenuRow.MOVE, CocoonTileMenuRow.NEW_FOLDER, CocoonTileMenuRow.EDIT_GRID)
        })
    val focusedTile: CocoonTile?
        get() = tiles.getOrNull(focusedIndex)?.takeIf { it.target !is CocoonTileTarget.Gap && it.target !is CocoonTileTarget.Covered }

    /**
     * Board slots including Cocoon's empty spaces: the last column is filled out and a few blank
     * columns follow, so the cursor can travel on and the last folders can still reach the centre.
     * A on a blank slot opens Start ▸ New to put something there.
     */
    val slotCount: Int
        get() = if (tiles.isEmpty()) 0
        else ((tiles.size + COCOON_BOARD_ROWS - 1) / COCOON_BOARD_ROWS + COCOON_EMPTY_COLUMNS) * COCOON_BOARD_ROWS
}

/**
 * Cocoon's dock: All apps fixed on the left, six slots for the user's own apps, Settings fixed on
 * the right. Slot indexes run 0 (All apps), 1..6 (apps), 7 (Settings).
 */
const val COCOON_DOCK_SLOTS = 6
const val COCOON_DOCK_SIZE = COCOON_DOCK_SLOTS + 2

sealed interface CocoonDockTarget {
    data object AllApps : CocoonDockTarget
    data class App(val packageName: String) : CocoonDockTarget
    /** An empty slot: opens All apps, where an app can be added to the dock. */
    data object AddApp : CocoonDockTarget
    /** Cocoon's Pods button: Log, Screenshots, Themes. */
    data object Pods : CocoonDockTarget
}

fun cocoonDockTarget(slot: Int, apps: List<String>): CocoonDockTarget = when (slot) {
    0 -> CocoonDockTarget.AllApps
    COCOON_DOCK_SIZE - 1 -> CocoonDockTarget.Pods
    else -> apps.getOrNull(slot - 1)?.let { CocoonDockTarget.App(it) } ?: CocoonDockTarget.AddApp
}

/** The board is three rows tall and grows to the right, like Cocoon's. */
val COCOON_BOARD_ROWS: Int get() = CocoonHomeStore.rows
const val COCOON_EMPTY_COLUMNS = 3

internal fun builtInTiles(): List<CocoonTile> = listOf(
    CocoonTile(
        key = "android-games",
        target = CocoonTileTarget.AndroidGames,
        title = null,
        titleRes = R.string.cocoon_tile_android_games,
        kindRes = R.string.cocoon_kind_shortcut,
        glyph = CocoonGlyph.APPS,
        platformSlug = null,
        colorIndex = CocoonFolderPalette.APPS,
    ),
    CocoonTile(
        key = "emulators",
        target = CocoonTileTarget.Emulators,
        title = null,
        titleRes = R.string.cocoon_tile_emulators,
        kindRes = R.string.cocoon_kind_shortcut,
        glyph = CocoonGlyph.EMULATORS,
        platformSlug = null,
        colorIndex = CocoonFolderPalette.EMULATORS,
    ),
    CocoonTile(
        key = "settings",
        target = CocoonTileTarget.Settings,
        title = null,
        titleRes = R.string.cocoon_tile_settings,
        kindRes = R.string.cocoon_kind_shortcut,
        glyph = CocoonGlyph.SETTINGS,
        platformSlug = null,
        colorIndex = CocoonFolderPalette.SETTINGS,
    ),
)

/**
 * Hidden fallback while the Cocoon screens are being built: when true, the home route draws
 * Argosy's original home instead of the board. Not persisted; a restart returns to Cocoon.
 */
object CocoonMode {
    val useClassicHome = MutableStateFlow(false)
}

/** One-shot requests the board hands to its screen. */
sealed interface CocoonHomeEvent {
    data class LaunchApp(val intent: android.content.Intent, val options: android.os.Bundle?) : CocoonHomeEvent
    data class Navigate(val route: String) : CocoonHomeEvent
}

/** Rows of the Y menu on an app in the dock. */
enum class CocoonDockMenuRow(@StringRes val labelRes: Int) {
    LAUNCH(R.string.cocoon_dock_launch),
    EDIT(R.string.cocoon_dock_edit_app),
    CHANGE(R.string.cocoon_dock_change_app),
    INFO(R.string.cocoon_dock_app_info),
    CLEAR(R.string.cocoon_dock_clear_slot),
}

/** Rows of the Y menu on a board tile. */
enum class CocoonTileMenuRow(@StringRes val labelRes: Int) {
    OPEN(R.string.cocoon_tile_menu_open),
    EDIT(R.string.cocoon_tile_menu_edit),
    NEW_FOLDER(R.string.cocoon_tile_menu_new_folder),
    EDIT_GRID(R.string.cocoon_tile_menu_edit_grid),
    DELETE_FOLDER(R.string.cocoon_tile_menu_delete_folder),
    REMOVE(R.string.cocoon_tile_menu_remove),
    APPEARANCE(R.string.cocoon_tile_menu_appearance),
    EDIT_APP(R.string.cocoon_dock_edit_app),
    JINGLE(R.string.cocoon_jingle_platform),
    SIZE(R.string.cocoon_widget_size),
    MOVE(R.string.cocoon_tile_menu_move),
    // Home-wide rows (the old Start menu, now part of Y)
    NEW(R.string.cocoon_start_new),
    SEARCH(R.string.cocoon_search_settings),
    SETTINGS(R.string.cocoon_tile_settings),
}

/**
 * Y's menu on Home: the item's own rows, then New ▸, Edit Grid, Search Settings and Settings.
 * Remove / Delete stay last so they are never pressed by accident.
 */
private fun withHomeRows(rows: List<CocoonTileMenuRow>): List<CocoonTileMenuRow> {
    val own = rows.filter {
        it != CocoonTileMenuRow.NEW_FOLDER && it != CocoonTileMenuRow.EDIT_GRID &&
            it != CocoonTileMenuRow.REMOVE && it != CocoonTileMenuRow.DELETE_FOLDER
    }
    val danger = rows.filter { it == CocoonTileMenuRow.REMOVE || it == CocoonTileMenuRow.DELETE_FOLDER }
    return own + listOf(
        CocoonTileMenuRow.NEW, CocoonTileMenuRow.EDIT_GRID, CocoonTileMenuRow.SEARCH, CocoonTileMenuRow.SETTINGS
    ) + danger
}


/** An installed app offered by the dock's app picker. */
data class CocoonPickApp(val packageName: String, val label: String)

const val COCOON_PICK_COLUMNS = 6


/** A folder's look chosen in Y ▸ Appearance; null parts follow the default. */
data class CocoonFolderLook(val color: Int? = null, val symbol: String? = null, val image: String? = null) {
    fun encode(): String = listOf(color?.toString().orEmpty(), symbol.orEmpty(), image.orEmpty()).joinToString("\u0001")

    companion object {
        fun decode(value: String?): CocoonFolderLook? {
            if (value.isNullOrEmpty()) return null
            val parts = value.split("\u0001")
            return CocoonFolderLook(
                color = parts.getOrNull(0)?.toIntOrNull(),
                symbol = parts.getOrNull(1)?.ifBlank { null },
                image = parts.getOrNull(2)?.ifBlank { null },
            )
        }
    }
}


/** Widget sizes, columns × rows, in the order Y cycles through them. */
val COCOON_WIDGET_SIZES = listOf(1 to 1, 2 to 1, 1 to 2, 2 to 2)
