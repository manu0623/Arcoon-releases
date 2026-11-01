package com.nendo.argosy.data.remote.github

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UpdateApkSelectionTest {

    private fun asset(name: String) = GitHubAsset(name, "https://example/$name", 1L)

    private val release = listOf(
        asset("argosy-v2.13.0.apk"),
        asset("argosy-v2.13.0-arm64.apk"),
        asset("argosy-v2.13.0-arm32.apk"),
        asset("argosy-v2.13.0-x86_64.apk"),
        asset("argosy-v2.13.0-x86.apk")
    )

    private fun pick(versionCode: Int) =
        UpdateRepository.selectApkAsset(release, versionCode)?.name

    @Test
    fun `each abi takes its own asset`() {
        assertEquals("argosy-v2.13.0-arm32.apk", pick(1_000_332))
        assertEquals("argosy-v2.13.0-arm64.apk", pick(2_000_332))
        assertEquals("argosy-v2.13.0-x86.apk", pick(4_000_332))
        assertEquals("argosy-v2.13.0-x86_64.apk", pick(5_000_332))
    }

    @Test
    fun `x86 does not take the x86_64 asset whose name contains it`() {
        assertEquals("argosy-v2.13.0-x86.apk", pick(4_000_332))
    }

    @Test
    fun `universal build takes the unsuffixed asset`() {
        assertEquals("argosy-v2.13.0.apk", pick(3_000_332))
    }

    @Test
    fun `an abi with no asset of its own falls back to the unsuffixed one`() {
        val without = release.filterNot { it.name.endsWith("-x86_64.apk") }
        assertEquals(
            "argosy-v2.13.0.apk",
            UpdateRepository.selectApkAsset(without, 5_000_332)?.name
        )
    }

    @Test
    fun `a release of only suffixed assets still yields one`() {
        val suffixedOnly = release.filterNot { it.name == "argosy-v2.13.0.apk" }
        assertEquals(
            "argosy-v2.13.0-arm64.apk",
            UpdateRepository.selectApkAsset(suffixedOnly, 3_000_332)?.name
        )
    }

    @Test
    fun `non apk assets are never chosen`() {
        assertNull(UpdateRepository.selectApkAsset(listOf(asset("notes.md")), 2_000_332))
    }

    /**
     * The selector shipped in v2.11.0 through v2.13.0, which every already-installed client runs
     * and which no release can change. It recognises only arm64 and arm32 and treats every other
     * name as the universal build, so publishing `-x86.apk` handed universal clients an x86
     * package and Android refused it with INSTALL_FAILED_NO_MATCHING_ABIS.
     */
    private fun legacyPick(assets: List<GitHubAsset>, versionCode: Int): String? {
        val apks = assets.filter { it.name.endsWith(".apk") }
        val suffix = when (versionCode / 1_000_000) {
            1 -> "arm32"
            2 -> "arm64"
            else -> null
        }
        return (suffix?.let { s -> apks.find { it.name.contains(s) } }
            ?: apks.find { !it.name.contains("arm64") && !it.name.contains("arm32") }
            ?: apks.firstOrNull())?.name
    }

    @Test
    fun `published asset names stay safe for clients running the frozen selector`() {
        val published = listOf(
            asset("argosy-v2.14.0-arm32.apk"),
            asset("argosy-v2.14.0-arm64.apk"),
            asset("argosy-v2.14.0.apk")
        )
        assertEquals("argosy-v2.14.0.apk", legacyPick(published, 3_000_333))
        assertEquals("argosy-v2.14.0-arm64.apk", legacyPick(published, 2_000_333))
        assertEquals("argosy-v2.14.0-arm32.apk", legacyPick(published, 1_000_333))
    }

    @Test
    fun `an abi asset the frozen selector cannot recognise is never published`() {
        val withX86 = listOf(
            asset("argosy-v2.14.0-arm32.apk"),
            asset("argosy-v2.14.0-arm64.apk"),
            asset("argosy-v2.14.0-x86.apk"),
            asset("argosy-v2.14.0.apk")
        )
        assertEquals("argosy-v2.14.0-x86.apk", legacyPick(withX86, 3_000_333))
    }
}
