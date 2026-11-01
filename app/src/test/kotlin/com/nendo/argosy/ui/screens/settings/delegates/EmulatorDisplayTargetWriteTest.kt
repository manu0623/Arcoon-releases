package com.nendo.argosy.ui.screens.settings.delegates

import com.nendo.argosy.data.local.entity.PlatformEntity
import com.nendo.argosy.data.preferences.EmulatorDisplayTarget
import com.nendo.argosy.domain.usecase.game.ConfigureEmulatorUseCase
import com.nendo.argosy.ui.screens.settings.EmulatorState
import com.nendo.argosy.ui.screens.settings.PlatformEmulatorConfig
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EmulatorDisplayTargetWriteTest {

    private val configureEmulatorUseCase: ConfigureEmulatorUseCase = mockk(relaxed = true)

    private val delegate = EmulatorSettingsDelegate(
        emulatorDetector = mockk(relaxed = true),
        configureEmulatorUseCase = configureEmulatorUseCase,
        soundManager = mockk(relaxed = true),
        emulatorSaveConfigRepository = mockk(relaxed = true),
        emulatorConfigRepo = mockk(relaxed = true),
        coreManager = mockk(relaxed = true),
        coreVersionRepository = mockk(relaxed = true),
        emulatorUpdateManager = mockk(relaxed = true),
        emulatorDownloadManager = mockk(relaxed = true),
        emulatorUpdateRepository = mockk(relaxed = true),
        saveHandlerRegistry = mockk(relaxed = true),
        savePathAuthority = mockk(relaxed = true),
        builtinCoreSummaryLoader = mockk(relaxed = true)
    )

    @Test
    fun `a picked role is stored by name and shown on the platform at once`() = runTest {
        delegate.updateState(EmulatorState(platforms = listOf(config(7L), config(8L))))
        var reloaded = false

        delegate.setDisplayTarget(this, 7L, EmulatorDisplayTarget.PRIMARY) { reloaded = true }
        advanceUntilIdle()

        coVerify { configureEmulatorUseCase.setDisplayTargetForPlatform(7L, "PRIMARY") }
        assertEquals(EmulatorDisplayTarget.PRIMARY, delegate.state.value.platforms[0].displayTarget)
        assertEquals(EmulatorDisplayTarget.DEFAULT, delegate.state.value.platforms[1].displayTarget)
        assertTrue(reloaded)
    }

    @Test
    fun `use default clears the stored target`() = runTest {
        delegate.updateState(EmulatorState(platforms = listOf(config(7L, EmulatorDisplayTarget.PRESENTATION))))

        delegate.setDisplayTarget(this, 7L, EmulatorDisplayTarget.DEFAULT) {}
        advanceUntilIdle()

        coVerify { configureEmulatorUseCase.setDisplayTargetForPlatform(7L, null) }
        assertEquals(EmulatorDisplayTarget.DEFAULT, delegate.state.value.platforms.single().displayTarget)
    }

    @Test
    fun `cycling the platform row writes through the same path`() = runTest {
        val platform = config(7L, EmulatorDisplayTarget.DEFAULT)
        delegate.updateState(EmulatorState(platforms = listOf(platform)))

        delegate.cycleDisplayTarget(this, platform, 1) {}
        advanceUntilIdle()

        coVerify { configureEmulatorUseCase.setDisplayTargetForPlatform(7L, "PRESENTATION") }
    }

    private fun config(id: Long, target: EmulatorDisplayTarget = EmulatorDisplayTarget.DEFAULT) =
        PlatformEmulatorConfig(
            platform = PlatformEntity(id = id, slug = "slug$id", name = "P$id", shortName = "P$id", romExtensions = ""),
            selectedEmulator = null,
            isUserConfigured = false,
            availableEmulators = emptyList(),
            displayTarget = target
        )
}
