package com.nendo.argosy.ui.screens.apps

import androidx.lifecycle.ViewModelStore
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.data.preferences.UserPreferences
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.repository.AppsRepository
import com.nendo.argosy.data.repository.GameRepository
import com.nendo.argosy.data.repository.InstalledApp
import com.nendo.argosy.ui.components.AppContextMenuItem
import com.nendo.argosy.ui.components.AppMenuRow
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppsDockPinObservationTest {
    private val models = ViewModelStore()
    private val packages = MutableSharedFlow<String>(replay = 1)
    private val prefs = MutableStateFlow(UserPreferences())
    private val repository = mockk<AppsRepository>(relaxed = true)
    private val preferences = mockk<UserPreferencesRepository>(relaxed = true)
    private val games = mockk<GameRepository>(relaxed = true)
    private val app = InstalledApp("test.app", "App", isSystemApp = false)

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        packages.tryEmit("")
        every { repository.packageChanges } returns packages
        every { preferences.preferences } returns prefs
        coEvery { repository.getInstalledApps(includeSystemApps = true) } returns listOf(app)
        coEvery { games.getBySource(GameSource.ANDROID_APP) } returns emptyList()
        coEvery { preferences.toggleDockPin(app.packageName) } answers {
            prefs.value = prefs.value.copy(secondaryHomeApps = listOf(app.packageName))
            true
        }
    }

    @After
    fun tearDown() {
        models.clear()
        Dispatchers.resetMain()
    }

    private fun viewModel() = AppsViewModel(
        appsRepository = repository,
        preferencesRepository = preferences,
        soundManager = mockk(relaxed = true),
        gameRepository = games,
        playStoreService = mockk(relaxed = true),
        imageCacheManager = mockk(relaxed = true),
        metadataFetcher = mockk(relaxed = true),
        androidGameScanner = mockk(relaxed = true),
        displayAffinityHelper = mockk { every { hasSecondaryDisplay } returns true },
        context = mockk(relaxed = true)
    ).also { models.put("apps", it) }

    @Test
    fun `pinning during a package refresh keeps the reloaded badge in sync`() = runTest {
        val model = viewModel()
        runCurrent()
        model.showContextMenu()
        val inventory = CompletableDeferred<List<InstalledApp>>()
        coEvery { repository.getInstalledApps(includeSystemApps = true) } coAnswers { inventory.await() }
        packages.emit(app.packageName)
        runCurrent()
        assertTrue(model.uiState.value.isLoading)

        val pinIndex = model.uiState.value.contextMenuItems.indexOf(
            AppMenuRow.Action(AppContextMenuItem.TOGGLE_SECONDARY_HOME)
        )
        model.selectContextMenuItem(pinIndex)
        runCurrent()
        assertEquals(listOf(app.packageName), prefs.value.secondaryHomeApps)
        assertTrue(model.uiState.value.apps.single().isOnSecondaryHome)

        inventory.complete(listOf(app))
        runCurrent()
        assertFalse(model.uiState.value.isLoading)
        assertTrue(model.uiState.value.apps.single().isOnSecondaryHome)
    }

    @Test
    fun `pin changes from another screen update badges without an inventory reload`() = runTest {
        val model = viewModel()
        runCurrent()
        assertFalse(model.uiState.value.apps.single().isOnSecondaryHome)

        prefs.value = prefs.value.copy(secondaryHomeApps = listOf(app.packageName))
        runCurrent()
        assertTrue(model.uiState.value.apps.single().isOnSecondaryHome)

        prefs.value = prefs.value.copy(secondaryHomeApps = emptyList())
        runCurrent()
        assertFalse(model.uiState.value.apps.single().isOnSecondaryHome)
    }
}
