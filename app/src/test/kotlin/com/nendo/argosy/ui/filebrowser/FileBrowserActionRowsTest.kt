package com.nendo.argosy.ui.filebrowser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FileBrowserActionRowsTest {

    private val entries = listOf(
        FileEntry(name = "..", path = "/storage/emulated/0", isDirectory = true, isParentLink = true),
        FileEntry(name = "roms", path = "/storage/emulated/0/Games/roms", isDirectory = true),
        FileEntry(name = "cert.pem", path = "/storage/emulated/0/Games/cert.pem", isDirectory = false)
    )

    private fun state(mode: FileBrowserMode, path: String = "/storage/emulated/0/Games") =
        FileBrowserState(currentPath = path, entries = entries, mode = mode)

    @Test
    fun `folder selection pins use and new folder rows`() {
        assertEquals(
            listOf(FileBrowserAction.USE_FOLDER, FileBrowserAction.NEW_FOLDER),
            state(FileBrowserMode.FOLDER_SELECTION).actions
        )
    }

    @Test
    fun `file or folder selection pins only the use row`() {
        assertEquals(
            listOf(FileBrowserAction.USE_FOLDER),
            state(FileBrowserMode.FILE_OR_FOLDER_SELECTION).actions
        )
    }

    @Test
    fun `file selection and an unopened browser pin nothing`() {
        assertEquals(emptyList<FileBrowserAction>(), state(FileBrowserMode.FILE_SELECTION).actions)
        assertEquals(emptyList<FileBrowserAction>(), state(FileBrowserMode.FOLDER_SELECTION, path = "").actions)
    }

    @Test
    fun `initial focus lands on the first real entry`() {
        val folder = state(FileBrowserMode.FOLDER_SELECTION)
        val atFirst = folder.copy(fileFocusIndex = folder.firstEntryIndex)

        assertEquals(entries.first(), atFirst.focusedEntry)
        assertNull(atFirst.focusedAction)
        assertEquals(0, state(FileBrowserMode.FILE_SELECTION).firstEntryIndex)
    }

    @Test
    fun `an empty folder focuses the first action row`() {
        val empty = state(FileBrowserMode.FOLDER_SELECTION).copy(entries = emptyList())

        assertEquals(FileBrowserAction.USE_FOLDER, empty.copy(fileFocusIndex = empty.firstEntryIndex).focusedAction)
    }

    @Test
    fun `moving up from the first entry reaches the action rows then wraps to the last entry`() {
        val folder = state(FileBrowserMode.FOLDER_SELECTION)
        val start = folder.copy(fileFocusIndex = folder.firstEntryIndex)

        val newFolder = start.withFileFocusMoved(-1)
        assertEquals(FileBrowserAction.NEW_FOLDER, newFolder.focusedAction)

        val useFolder = newFolder.withFileFocusMoved(-1)
        assertEquals(FileBrowserAction.USE_FOLDER, useFolder.focusedAction)

        val wrapped = useFolder.withFileFocusMoved(-1)
        assertEquals(entries.last(), wrapped.focusedEntry)
        assertEquals(FileBrowserAction.USE_FOLDER, wrapped.withFileFocusMoved(1).focusedAction)
    }
}
