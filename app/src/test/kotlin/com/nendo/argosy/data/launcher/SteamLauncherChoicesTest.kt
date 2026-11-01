package com.nendo.argosy.data.launcher

import com.nendo.argosy.domain.model.LaunchPromptOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamLauncherChoicesTest {

    private val gameNative = GameNativeLauncher.packageName
    private val droidDeck = DroidDeckLauncher.packageName
    private val everythingInstalled: (String) -> Boolean = { true }

    @Test
    fun `both launchers reporting an unpinned game asks between them`() {
        val choices = SteamLaunchers.launchCandidates(
            storedPackage = null,
            pinned = false,
            reporting = listOf(gameNative, droidDeck),
            isInstalled = everythingInstalled
        )

        assertEquals(listOf(gameNative, droidDeck), choices)
    }

    @Test
    fun `a pinned game returns its installed launcher`() {
        val choices = SteamLaunchers.launchCandidates(
            storedPackage = droidDeck,
            pinned = true,
            reporting = listOf(gameNative, droidDeck),
            isInstalled = everythingInstalled
        )

        assertEquals(listOf(droidDeck), choices)
    }

    @Test
    fun `one reporting replacement remains available for recovery`() {
        listOf(gameNative, droidDeck).forEach { only ->
            val choices = SteamLaunchers.launchCandidates(
                storedPackage = "removed.launcher",
                pinned = false,
                reporting = listOf(only),
                isInstalled = everythingInstalled
            )

            assertEquals(listOf(only), choices)
        }
    }

    @Test
    fun `a reporting launcher that is no longer installed is not offered`() {
        val choices = SteamLaunchers.launchCandidates(
            storedPackage = gameNative,
            pinned = false,
            reporting = listOf(gameNative, droidDeck),
            isInstalled = { it == droidDeck }
        )

        assertEquals(listOf(droidDeck), choices)
    }

    @Test
    fun `nothing reporting does not ask`() {
        val choices = SteamLaunchers.launchCandidates(
            storedPackage = "removed.launcher",
            pinned = false,
            reporting = emptyList(),
            isInstalled = everythingInstalled
        )

        assertTrue(choices.isEmpty())
    }

    @Test
    fun `a missing manual pin can use a positively reported installed replacement`() {
        val choices = SteamLaunchers.launchCandidates(
            storedPackage = gameNative,
            pinned = true,
            reporting = listOf(gameNative, droidDeck),
            isInstalled = { it == droidDeck }
        )

        assertEquals(listOf(droidDeck), choices)
    }

    @Test
    fun `an installed automatic association remains valid when its export is unavailable`() {
        val choices = SteamLaunchers.launchCandidates(
            storedPackage = gameNative,
            pinned = false,
            reporting = emptyList(),
            isInstalled = everythingInstalled
        )

        assertEquals(listOf(gameNative), choices)
    }

    @Test
    fun `an unknown saved package never becomes a launch target even when installed`() {
        val choices = SteamLaunchers.launchCandidates(
            storedPackage = "unknown.launcher",
            pinned = true,
            reporting = listOf("unknown.launcher"),
            isInstalled = everythingInstalled
        )

        assertTrue(choices.isEmpty())
    }

    @Test
    fun `duplicate and unknown reports cannot create extra launcher choices`() {
        val choices = SteamLaunchers.launchCandidates(
            storedPackage = "removed.launcher",
            pinned = false,
            reporting = listOf(droidDeck, "unknown.launcher", gameNative, droidDeck),
            isInstalled = everythingInstalled
        )

        assertEquals(listOf(droidDeck, gameNative), choices)
    }

    @Test
    fun `reports without any installed launcher cannot be launched`() {
        val choices = SteamLaunchers.launchCandidates(
            storedPackage = gameNative,
            pinned = true,
            reporting = listOf(gameNative, droidDeck),
            isInstalled = { false }
        )

        assertTrue(choices.isEmpty())
    }

    @Test
    fun `an unspecified native route retains the preferred installed launcher without reports`() {
        val choices = SteamLaunchers.launchCandidates(
            storedPackage = null,
            pinned = false,
            reporting = emptyList(),
            isInstalled = { it == droidDeck }
        )

        assertEquals(listOf(droidDeck), choices)
    }

    @Test
    fun `prompt options map back to the launcher they name`() {
        assertEquals(LaunchPromptOption.LAUNCH_WITH_GAMENATIVE, SteamLaunchers.promptOptionFor(gameNative))
        assertEquals(LaunchPromptOption.LAUNCH_WITH_DROIDDECK, SteamLaunchers.promptOptionFor(droidDeck))
        assertEquals(GameNativeLauncher, SteamLaunchers.forPromptOption(LaunchPromptOption.LAUNCH_WITH_GAMENATIVE))
        assertEquals(DroidDeckLauncher, SteamLaunchers.forPromptOption(LaunchPromptOption.LAUNCH_WITH_DROIDDECK))
    }
}
