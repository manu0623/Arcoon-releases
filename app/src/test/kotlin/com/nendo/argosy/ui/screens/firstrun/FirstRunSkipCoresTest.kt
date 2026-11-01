package com.nendo.argosy.ui.screens.firstrun

import android.app.Application
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.remote.ssl.UserCertStore
import com.nendo.argosy.libretro.LibretroCoreManager
import com.nendo.argosy.libretro.LibretroCoreRegistry
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class FirstRunSkipCoresTest {

    private val dispatcher = StandardTestDispatcher()
    private val preferences = mockk<UserPreferencesRepository>(relaxed = true)
    private val coreManager = mockk<LibretroCoreManager>(relaxed = true)
    private val certStore = mockk<UserCertStore>(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { certStore.certs } returns MutableStateFlow(emptyList())
        every { coreManager.getMissingCoresForPlatforms(any()) } returns
            listOf(mockk<LibretroCoreRegistry.CoreInfo>(relaxed = true))
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `skipping the core download leaves the built-in emulator enabled`() = runTest(dispatcher) {
        val viewModel = FirstRunViewModel(
            application = mockk<Application>(relaxed = true),
            preferencesRepository = preferences,
            romMRepository = mockk(relaxed = true),
            platformRepository = mockk(relaxed = true),
            permissionHelper = mockk(relaxed = true),
            coreManager = coreManager,
            gamepadInputHandler = mockk(relaxed = true),
            hapticManager = mockk(relaxed = true),
            userCertStore = certStore
        )
        repeat(6) { viewModel.nextStep() }
        assertEquals(FirstRunStep.CORE_PROMPT, viewModel.uiState.value.currentStep)
        assertEquals(1, viewModel.uiState.value.missingCoreCount)

        viewModel.skipCorePrompt()
        advanceUntilIdle()

        assertEquals(FirstRunStep.COMPLETE, viewModel.uiState.value.currentStep)
        coVerify(exactly = 0) { preferences.setBuiltinLibretroEnabled(any()) }
    }
}
