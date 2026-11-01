package com.nendo.argosy.ui.screens.settings.delegates

import android.content.Context
import com.nendo.argosy.data.launcher.DroidDeckLauncher
import com.nendo.argosy.data.launcher.GameNativeLauncher
import com.nendo.argosy.data.repository.SteamRepository
import com.nendo.argosy.data.repository.SteamResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SteamAddGameLauncherTest {

    private val scope = TestScope(StandardTestDispatcher())
    private val context: Context = mockk(relaxed = true)
    private val steamRepository: SteamRepository = mockk {
        coEvery { addGame(any(), any()) } returns SteamResult.Error("stop")
    }

    private val delegate = SteamSettingsDelegate(
        steamRepository = steamRepository,
        steamAuthManager = mockk(relaxed = true),
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

    private fun addFrom(launcherPackage: String?) {
        delegate.showAddSteamGameDialog(launcherPackage)
        delegate.setAddGameAppId(APP_ID.toString())
        delegate.confirmAddSteamGame(context, scope)
        scope.runCurrent()
    }

    @Test
    fun `a game added from the DroidDeck section is linked to DroidDeck`() {
        addFrom(DroidDeckLauncher.packageName)

        coVerify { steamRepository.addGame(APP_ID, DroidDeckLauncher.packageName) }
    }

    @Test
    fun `a game added from the general row is linked to GameNative`() {
        addFrom(null)

        coVerify { steamRepository.addGame(APP_ID, GameNativeLauncher.packageName) }
    }

    @Test
    fun `closing the dialog forgets the launcher it was opened from`() {
        delegate.showAddSteamGameDialog(DroidDeckLauncher.packageName)
        delegate.dismissAddSteamGameDialog()
        addFrom(null)

        coVerify { steamRepository.addGame(APP_ID, GameNativeLauncher.packageName) }
    }

    private companion object {
        const val APP_ID = 1145360L
    }
}
