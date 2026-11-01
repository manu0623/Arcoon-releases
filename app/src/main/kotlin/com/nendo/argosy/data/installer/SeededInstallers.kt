package com.nendo.argosy.data.installer

import com.nendo.argosy.data.launcher.DroidDeckLauncher
import com.nendo.argosy.data.launcher.GameNativeLauncher
import com.nendo.argosy.data.local.entity.ManagedInstallerEntity
import com.nendo.argosy.data.remote.github.GitHubAsset

const val CONTROLLER_KEYBOARD_PACKAGE = "com.handheldkeyboard.ime"

object SeededInstallers {

    val ALL: List<ManagedInstallerEntity> = listOf(
        ManagedInstallerEntity(
            repoOwner = "luisho24",
            repoName = "HandheldKeyboard",
            displayName = "Handheld Keyboard",
            packageName = CONTROLLER_KEYBOARD_PACKAGE,
            locked = true,
            sortOrder = 0
        ),
        ManagedInstallerEntity(
            repoOwner = "utkarshdalal",
            repoName = "GameNative",
            displayName = GameNativeLauncher.displayName,
            packageName = GameNativeLauncher.packageName,
            locked = true,
            sortOrder = 1
        ),
        ManagedInstallerEntity(
            repoOwner = "Droid-Deck",
            repoName = "DroidDeck",
            displayName = DroidDeckLauncher.displayName,
            packageName = DroidDeckLauncher.packageName,
            locked = true,
            sortOrder = 2
        )
    )

    private val renamedPackageAssetSuffixes: Map<Pair<String, String>, List<String>> = mapOf(
        ("Droid-Deck" to "DroidDeck") to listOf("-pubg.apk", "-antutu.apk", "-ludashi.apk")
    )

    fun isControllerKeyboard(entity: ManagedInstallerEntity): Boolean =
        entity.packageName == CONTROLLER_KEYBOARD_PACKAGE

    /**
     * Release assets [entity] may install: every asset except a seeded repo's builds
     * published under a package name other than the row's.
     */
    fun installableAssets(entity: ManagedInstallerEntity, assets: List<GitHubAsset>): List<GitHubAsset> {
        val excluded = renamedPackageAssetSuffixes[entity.repoOwner to entity.repoName] ?: return assets
        return assets.filterNot { asset -> excluded.any { asset.name.endsWith(it, ignoreCase = true) } }
    }
}
