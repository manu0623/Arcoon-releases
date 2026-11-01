package com.nendo.argosy.ui.home.grid

import com.nendo.argosy.domain.model.HomeTileTargetRef
import com.nendo.argosy.ui.components.CustomGridState
import com.nendo.argosy.ui.components.TilePickerAction
import com.nendo.argosy.ui.components.TilePickerCategory
import com.nendo.argosy.ui.components.TilePickerEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val SHOWS_ID = "lib-shows"

/**
 * The media tab drills from libraries into one library's titles, so back has two meanings there.
 * Closing the picker from inside a library would throw away the step the reader just took.
 */
class PickerMediaLibraryStepTest {

    private var state = CustomGridState(pickerCategory = TilePickerCategory.MEDIA)
    private val asked = mutableListOf<String?>()

    private val coordinator = CustomGridCoordinator(
        context = io.mockk.mockk<android.content.Context>(relaxed = true),
        scope = CoroutineScope(Dispatchers.Unconfined),
        repository = null,
        ownerUserId = { null },
        pickerEntries = { _, _, libraryId ->
            asked += libraryId
            if (libraryId == null) listOf(libraryRow()) else listOf(titleRow())
        },
        read = { state },
        write = { transform -> state = transform(state) }
    )

    private fun libraryRow() = TilePickerEntry(
        target = HomeTileTargetRef.Unresolvable,
        title = "Shows",
        subtitle = "Library",
        action = TilePickerAction.OPEN_MEDIA_LIBRARY,
        libraryId = SHOWS_ID
    )

    private fun titleRow() = TilePickerEntry(
        target = HomeTileTargetRef.Media("item-1"),
        title = "Adventure Time",
        subtitle = "Series"
    )

    @Test
    fun `choosing a library asks for that library's titles`() {
        coordinator.selectPickerEntry(libraryRow())

        assertEquals(SHOWS_ID, state.pickerLibraryId)
        assertEquals(SHOWS_ID, asked.last())
    }

    @Test
    fun `back steps out of the library before it closes the picker`() {
        coordinator.selectPickerEntry(libraryRow())

        assertTrue(coordinator.backOutOfPickerLibrary())
        assertNull(state.pickerLibraryId)
        assertNull(asked.last())
    }

    @Test
    fun `back at the library list leaves the picker to close`() {
        assertFalse(coordinator.backOutOfPickerLibrary())
    }

    @Test
    fun `switching tab leaves no library selected behind it`() {
        coordinator.selectPickerEntry(libraryRow())
        coordinator.setPickerCategory(TilePickerCategory.GAMES)

        assertNull(state.pickerLibraryId)
    }
}
