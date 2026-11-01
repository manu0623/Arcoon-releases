package com.nendo.argosy

import com.nendo.argosy.data.launcher.DroidDeckLauncher
import com.nendo.argosy.data.launcher.GameNativeLauncher
import com.nendo.argosy.data.launcher.GameNativeStoreSync
import com.nendo.argosy.data.repository.AppsRepository
import com.nendo.argosy.util.Logger
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertSame
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.IOException

class SteamPackageChangesTest {
    private val sync = mockk<GameNativeStoreSync>()
    private val apps = mockk<AppsRepository>()

    @Before
    fun setUp() {
        mockkObject(Logger)
        every { Logger.error(any(), any(), any()) } just Runs
        every { apps.packageChanges } returns flowOf(
            GameNativeLauncher.packageName,
            "unrelated.package",
            DroidDeckLauncher.packageName
        )
    }

    @After
    fun tearDown() {
        unmockkObject(Logger)
    }

    private fun app() = ArgosyApp().also {
        it.appsRepository = apps
        it.gameNativeStoreSync = sync
    }

    @Test
    fun `failed refresh does not stop later package changes`() = runTest {
        var attempts = 0
        coEvery { sync.refreshSteamInstallations() } coAnswers {
            if (++attempts == 1) throw IOException("Preferences unavailable")
        }

        app().observeSteamPackageChanges()

        coVerify(exactly = 2) { sync.refreshSteamInstallations() }
    }

    @Test
    fun `cancellation stops package watching`() = runTest {
        val cancellation = CancellationException("Watcher cancelled")
        coEvery { sync.refreshSteamInstallations() } throws cancellation

        try {
            app().observeSteamPackageChanges()
            fail("Expected cancellation to propagate")
        } catch (actual: CancellationException) {
            assertSame(cancellation, actual)
        }

        coVerify(exactly = 1) { sync.refreshSteamInstallations() }
    }

    @Test
    fun `foreground refresh runs at most once an hour`() = runTest {
        coEvery { sync.refreshSteamInstallations() } returns Unit
        val app = app()
        val hourMs = 60 * 60 * 1000L

        app.refreshSteamInstallationsIfStale(now = 0L)
        app.refreshSteamInstallationsIfStale(now = hourMs - 1)
        coVerify(exactly = 1) { sync.refreshSteamInstallations() }

        app.refreshSteamInstallationsIfStale(now = hourMs)
        coVerify(exactly = 2) { sync.refreshSteamInstallations() }
    }

    @Test
    fun `failed foreground refresh is logged and still starts the interval`() = runTest {
        coEvery { sync.refreshSteamInstallations() } throws IOException("Preferences unavailable")
        val app = app()

        app.refreshSteamInstallationsIfStale(now = 0L)
        app.refreshSteamInstallationsIfStale(now = 1L)

        coVerify(exactly = 1) { sync.refreshSteamInstallations() }
    }
}
