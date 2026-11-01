package com.nendo.argosy.data.installer

import com.nendo.argosy.data.emulator.ApkAssetMatcher
import com.nendo.argosy.data.emulator.ApkMatchResult
import com.nendo.argosy.data.remote.github.GitHubAsset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SeededInstallersTest {

    private fun asset(name: String) = GitHubAsset(name, "https://example/$name", 1L)

    private fun seed(repoName: String) = SeededInstallers.ALL.single { it.repoName == repoName }

    private val droidDeckRelease = listOf(
        asset("DroidDeck-0.3.2-antutu.apk"),
        asset("DroidDeck-0.3.2-ludashi.apk"),
        asset("DroidDeck-0.3.2-pubg.apk"),
        asset("DroidDeck-0.3.2.apk"),
        asset("SHA256SUMS.txt")
    )

    @Test
    fun `droiddeck installs only the build under its own package`() {
        val assets = SeededInstallers.installableAssets(seed("DroidDeck"), droidDeckRelease)
        val match = ApkAssetMatcher.matchApk(assets, deviceAbi = "arm64-v8a")

        assertTrue(match is ApkMatchResult.SingleMatch)
        assertEquals("DroidDeck-0.3.2.apk", (match as ApkMatchResult.SingleMatch).asset.name)
    }

    @Test
    fun `gamenative keeps every build for the variant picker`() {
        val release = listOf(asset("gamenative-v1.2.1-legacy-xr.apk"), asset("gamenative-v1.2.1.apk"))

        assertEquals(release, SeededInstallers.installableAssets(seed("GameNative"), release))
    }

    @Test
    fun `a user-added repo keeps every asset`() {
        val userRow = seed("DroidDeck").copy(repoOwner = "someone", locked = false)

        assertEquals(droidDeckRelease, SeededInstallers.installableAssets(userRow, droidDeckRelease))
    }
}
