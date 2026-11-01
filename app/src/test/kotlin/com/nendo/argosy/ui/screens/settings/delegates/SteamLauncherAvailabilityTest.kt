package com.nendo.argosy.ui.screens.settings.delegates

import android.content.Context
import android.content.pm.PackageManager
import com.nendo.argosy.data.launcher.DroidDeckLauncher
import com.nendo.argosy.data.launcher.GameNativeLauncher
import com.nendo.argosy.data.steam.SteamAuthManager
import com.nendo.argosy.ui.screens.settings.SteamSettingsState
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamLauncherAvailabilityTest {
    private var installedPackages = setOf(GameNativeLauncher.packageName)
    private val packageManager: PackageManager = mockk {
        every { getPackageInfo(any<String>(), any<Int>()) } answers {
            if (firstArg<String>() in installedPackages) mockk() else throw PackageManager.NameNotFoundException()
        }
    }
    private val context: Context = mockk(relaxed = true) {
        every { packageManager } returns this@SteamLauncherAvailabilityTest.packageManager
    }
    private val authManager: SteamAuthManager = mockk(relaxed = true) {
        coEvery { getActiveAccount() } returns null
    }
    private val delegate = SteamSettingsDelegate(
        steamRepository = mockk(relaxed = true),
        steamAuthManager = authManager,
        steamLibraryManager = mockk(relaxed = true),
        androidDataAccessor = mockk(relaxed = true),
        notificationManager = mockk(relaxed = true),
        emulatorDownloadManager = mockk(relaxed = true),
        emulatorUpdateRepository = mockk(relaxed = true),
        steamIgdbResolver = mockk(relaxed = true),
        steamPathResolver = mockk(relaxed = true),
        storagePrefs = mockk(relaxed = true),
        gameNativeStoreSync = mockk(relaxed = true)
    )

    @Test
    fun `package changes refresh availability and preserve unrelated settings`() = runTest {
        delegate.updateState(SteamSettingsState(
            username = "player",
            qrUrl = "synthetic-qr",
            error = "pending message",
            addGameAppId = "42",
            gnStoragePath = "configured-storage",
            selectedLauncherPackage = GameNativeLauncher.packageName,
            showGameNativeFoldersModal = true
        ))
        delegate.refreshLauncherAvailability(context)
        val before = delegate.state.value
        assertTrue(before.gnInstalled)
        assertEquals(listOf(GameNativeLauncher.packageName), before.installedLaunchers.map { it.packageName })
        assertFalse(before.notInstalledLaunchers.any { it.emulatorId == "gamenative" })
        assertEquals("configured-storage", before.gnStoragePath)

        installedPackages = setOf(DroidDeckLauncher.packageName)
        delegate.refreshLauncherAvailability(context)
        val after = delegate.state.value

        assertFalse(after.gnInstalled)
        assertEquals(listOf(DroidDeckLauncher.packageName), after.installedLaunchers.map { it.packageName })
        assertTrue(after.notInstalledLaunchers.any { it.emulatorId == "gamenative" })
        assertEquals(before.copy(
            gnInstalled = false,
            installedLaunchers = after.installedLaunchers,
            notInstalledLaunchers = after.notInstalledLaunchers
        ), after)
    }
}
