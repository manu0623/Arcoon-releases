package com.nendo.argosy.data.launcher

/**
 * The Frontend Sync export folders Argosy reads: GameNative's four per-store folders and
 * DroidDeck's single game files folder. Each holds one `<Title><ext>` file per install whose
 * content is the store's numeric id. GameNative's slugs and every marker extension are
 * upstream-exact - renaming them breaks marker discovery. [launcherPackage] is the launcher that
 * exported the folder and the one its markers mark installed.
 */
enum class GameNativeSyncFolder(
    val slug: String,
    val displayName: String,
    val markerExtension: String,
    val launcherPackage: String
) {
    STEAM("steam", "Steam", ".steam", GameNativeLauncher.packageName),
    GOG("gog", "GOG", ".gog", GameNativeLauncher.packageName),
    EPIC("epic", "Epic Games", ".epic", GameNativeLauncher.packageName),
    AMAZON("amazon", "Amazon Games", ".amazon", GameNativeLauncher.packageName),
    DROIDDECK("droiddeck", "DroidDeck", ".steam", DroidDeckLauncher.packageName);

    companion object {
        val STEAM_INSTALL_MARKERS: List<GameNativeSyncFolder> = listOf(STEAM, DROIDDECK)
    }
}
