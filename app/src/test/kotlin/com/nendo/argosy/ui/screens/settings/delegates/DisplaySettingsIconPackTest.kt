package com.nendo.argosy.ui.screens.settings.delegates

import com.nendo.argosy.data.model.IconPack
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.repository.CustomGridShapeStore
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private const val PACK_A = "org.example.pack.a"
private const val PACK_B = "org.example.pack.b"

@OptIn(ExperimentalCoroutinesApi::class)
class DisplaySettingsIconPackTest {

    private val preferences = mockk<UserPreferencesRepository>(relaxed = true)

    private val delegate = DisplaySettingsDelegate(
        preferencesRepository = preferences,
        gameRepository = mockk(relaxed = true),
        ledController = mockk(relaxed = true),
        screenCaptureManager = mockk(relaxed = true),
        customGridShapeStore = CustomGridShapeStore(),
        homeTileRepository = mockk(relaxed = true),
        syncPreferencesRepository = mockk(relaxed = true),
        homeAppAvailability = mockk(relaxed = true)
    )

    private fun hydrate(selected: String?) {
        delegate.updateState(
            delegate.state.value.copy(
                iconPack = selected,
                iconPacks = listOf(IconPack(PACK_A, "Alpha"), IconPack(PACK_B, "Beta"))
            )
        )
    }

    private fun TestScope.cycle(direction: Int) {
        delegate.cycleIconPack(this, direction)
        advanceUntilIdle()
    }

    @Test
    fun `system icons lead the options and a stored pack selects its own index`() {
        hydrate(PACK_B)

        val state = delegate.state.value
        assertEquals(listOf(null, PACK_A, PACK_B), state.iconPackOptions)
        assertEquals(2, state.selectedIconPackIndex)
    }

    @Test
    fun `a stored pack that is no longer installed reads as system icons`() {
        hydrate("org.example.pack.gone")

        assertEquals(0, delegate.state.value.selectedIconPackIndex)
    }

    @Test
    fun `choosing a pack persists it and updates the state`() = runTest {
        hydrate(null)

        delegate.setIconPack(this, PACK_A)
        advanceUntilIdle()

        coVerify(exactly = 1) { preferences.setIconPack(PACK_A) }
        assertEquals(PACK_A, delegate.state.value.iconPack)
    }

    @Test
    fun `cycling forward past the last pack wraps to system icons`() = runTest {
        hydrate(PACK_B)

        cycle(1)

        coVerify(exactly = 1) { preferences.setIconPack(null) }
        assertNull(delegate.state.value.iconPack)
    }

    @Test
    fun `cycling backward from system icons wraps to the last pack`() = runTest {
        hydrate(null)

        cycle(-1)

        coVerify(exactly = 1) { preferences.setIconPack(PACK_B) }
        assertEquals(PACK_B, delegate.state.value.iconPack)
    }

    @Test
    fun `cycling from a pack that disappeared starts from system icons`() = runTest {
        hydrate("org.example.pack.gone")

        cycle(1)

        coVerify(exactly = 1) { preferences.setIconPack(PACK_A) }
        assertEquals(PACK_A, delegate.state.value.iconPack)
    }
}
