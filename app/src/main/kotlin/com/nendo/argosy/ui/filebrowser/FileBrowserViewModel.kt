package com.nendo.argosy.ui.filebrowser

import android.content.Context
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nendo.argosy.R
import com.nendo.argosy.core.storage.StorageVolume
import com.nendo.argosy.data.storage.FileAccessLayer
import com.nendo.argosy.data.storage.FileInfo
import com.nendo.argosy.data.storage.StorageVolumeDetector
import com.nendo.argosy.util.StorageAccess
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class FileBrowserViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val volumeDetector: StorageVolumeDetector,
    private val fileAccessLayer: FileAccessLayer
) : ViewModel() {

    private val _state = MutableStateFlow(FileBrowserState())
    val state: StateFlow<FileBrowserState> = _state.asStateFlow()

    private val _resultPath = MutableSharedFlow<String>()
    val resultPath: SharedFlow<String> = _resultPath.asSharedFlow()

    init {
        recheckPermission()
    }

    fun hasPermission(): Boolean = _state.value.hasPermission

    fun recheckPermission() {
        viewModelScope.launch {
            val hasPermission = StorageAccess.isGranted(context)

            _state.update { it.copy(hasPermission = hasPermission) }

            if (hasPermission && _state.value.volumes.isEmpty()) {
                loadVolumes()
            }
        }
    }

    private fun loadVolumes() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }

            val volumes = withContext(Dispatchers.IO) {
                volumeDetector.detectStorageVolumes()
            }

            _state.update { state ->
                state.copy(
                    volumes = volumes,
                    isLoading = false
                )
            }

            if (volumes.isNotEmpty()) {
                navigate(volumes.first().path)
            }
        }
    }

    fun selectVolume(volume: StorageVolume) {
        val currentIndex = _state.value.volumes.indexOf(volume)
        if (currentIndex >= 0) {
            _state.update { it.copy(volumeFocusIndex = currentIndex) }
        }
        navigate(volume.path)
    }

    fun navigate(path: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorRes = null) }

            val result = withContext(Dispatchers.IO) {
                loadDirectory(path)
            }
            when (result) {
                is DirectoryListing.Success -> {
                    _state.update { state ->
                        val listed = state.copy(
                            currentPath = path,
                            entries = result.entries,
                            focusedPane = FocusedPane.FILES,
                            isLoading = false
                        )
                        listed.copy(fileFocusIndex = listed.firstEntryIndex)
                    }
                }
                is DirectoryListing.Error -> {
                    _state.update { state ->
                        state.copy(
                            errorRes = result.messageRes,
                            isLoading = false
                        )
                    }
                }
            }
        }
    }

    private sealed interface DirectoryListing {
        data class Success(val entries: List<FileEntry>) : DirectoryListing
        data class Error(@StringRes val messageRes: Int) : DirectoryListing
    }

    private fun loadDirectory(path: String): DirectoryListing {
        val isVolumeRoot = _state.value.volumes.any { it.path == path }
        val fileFilter = _state.value.fileFilter

        val children = fileAccessLayer.listFiles(path)
            ?: return DirectoryListing.Error(R.string.ui_file_browser_error_unreadable)

        val entries = mutableListOf<FileEntry>()

        if (!isVolumeRoot) {
            parentOf(path)?.let { parentPath ->
                entries.add(
                    FileEntry(
                        name = "..",
                        path = parentPath,
                        isDirectory = true,
                        isParentLink = true
                    )
                )
            }
        }

        val visible = children.filter { !it.name.startsWith(".") }

        val sortedDirs = visible
            .filter { it.isDirectory }
            .sortedBy { it.name.lowercase() }
            .map { it.toFileEntry() }

        val sortedFiles = visible
            .filter { it.isFile }
            .filter { fileFilter?.matches(it.name) ?: true }
            .sortedBy { it.name.lowercase() }
            .map { it.toFileEntry() }

        entries.addAll(sortedDirs)
        entries.addAll(sortedFiles)
        return DirectoryListing.Success(entries)
    }

    private fun FileInfo.toFileEntry(): FileEntry = FileEntry(
        name = name,
        path = path,
        isDirectory = isDirectory,
        size = if (isFile) size else 0L,
        lastModified = lastModified
    )

    private fun parentOf(path: String): String? {
        val trimmed = path.trimEnd('/')
        if (trimmed.isEmpty() || trimmed == "/") return null
        val idx = trimmed.lastIndexOf('/')
        if (idx <= 0) return null
        return trimmed.substring(0, idx)
    }

    fun goUp() {
        val currentPath = _state.value.currentPath
        val isVolumeRoot = _state.value.volumes.any { it.path == currentPath }

        if (!isVolumeRoot) {
            parentOf(currentPath)?.let { navigate(it) }
        }
    }

    fun isAtVolumeRoot(): Boolean {
        return _state.value.volumes.any { it.path == _state.value.currentPath }
    }

    fun moveFocus(delta: Int): Boolean {
        var moved = false
        _state.update { state ->
            when (state.focusedPane) {
                FocusedPane.VOLUMES -> {
                    val maxIndex = (state.volumes.size - 1).coerceAtLeast(0)
                    val newIndex = (state.volumeFocusIndex + delta).coerceIn(0, maxIndex)
                    moved = newIndex != state.volumeFocusIndex
                    state.copy(volumeFocusIndex = newIndex)
                }
                FocusedPane.FILES -> {
                    val next = state.withFileFocusMoved(delta)
                    moved = next.fileFocusIndex != state.fileFocusIndex
                    next
                }
            }
        }
        return moved
    }

    fun switchPane(direction: Int) {
        _state.update { state ->
            val newPane = when {
                direction < 0 && state.focusedPane == FocusedPane.FILES -> FocusedPane.VOLUMES
                direction > 0 && state.focusedPane == FocusedPane.VOLUMES -> FocusedPane.FILES
                else -> state.focusedPane
            }
            state.copy(focusedPane = newPane)
        }
    }

    fun confirmFocusedItem() {
        val state = _state.value
        when (state.focusedPane) {
            FocusedPane.VOLUMES -> {
                val volume = state.volumes.getOrNull(state.volumeFocusIndex)
                if (volume != null) {
                    selectVolume(volume)
                }
            }
            FocusedPane.FILES -> {
                val action = state.focusedAction
                if (action != null) {
                    performAction(action)
                } else {
                    state.focusedEntry?.let { openEntry(it) }
                }
            }
        }
    }

    fun openEntry(entry: FileEntry) {
        when {
            entry.isDirectory -> navigate(entry.path)
            _state.value.mode != FileBrowserMode.FOLDER_SELECTION -> selectPath(entry.path)
        }
    }

    fun performAction(action: FileBrowserAction) {
        if (action !in _state.value.actions) return
        when (action) {
            FileBrowserAction.USE_FOLDER -> selectPath(_state.value.currentPath)
            FileBrowserAction.NEW_FOLDER -> showCreateFolderDialog()
        }
    }

    private fun selectPath(path: String) {
        viewModelScope.launch {
            _resultPath.emit(path)
        }
    }

    fun setMode(mode: FileBrowserMode) {
        _state.update { it.copy(mode = mode) }
    }

    fun setFileFilter(filter: FileFilter?) {
        _state.update { it.copy(fileFilter = filter) }
        if (_state.value.currentPath.isNotEmpty()) {
            navigate(_state.value.currentPath)
        }
    }

    private fun showCreateFolderDialog() {
        _state.update {
            it.copy(
                showCreateFolderDialog = true,
                newFolderName = "",
                createFolderErrorRes = null
            )
        }
    }

    fun dismissCreateFolderDialog() {
        _state.update {
            it.copy(
                showCreateFolderDialog = false,
                newFolderName = "",
                createFolderErrorRes = null
            )
        }
    }

    fun setNewFolderName(name: String) {
        _state.update { it.copy(newFolderName = name, createFolderErrorRes = null) }
    }

    fun confirmCreateFolder() {
        val state = _state.value
        val folderName = state.newFolderName.trim()

        if (folderName.isEmpty()) {
            _state.update {
                it.copy(createFolderErrorRes = R.string.ui_file_browser_new_folder_error_empty)
            }
            return
        }

        if (folderName.contains("/") || folderName.contains("\\")) {
            _state.update {
                it.copy(createFolderErrorRes = R.string.ui_file_browser_new_folder_error_invalid)
            }
            return
        }

        viewModelScope.launch {
            val newPath = "${state.currentPath.trimEnd('/')}/$folderName"
            val success = withContext(Dispatchers.IO) {
                if (fileAccessLayer.exists(newPath)) {
                    _state.update {
                        it.copy(
                            createFolderErrorRes =
                                R.string.ui_file_browser_new_folder_error_exists
                        )
                    }
                    return@withContext false
                }
                if (!fileAccessLayer.mkdirs(newPath)) {
                    _state.update {
                        it.copy(
                            createFolderErrorRes =
                                R.string.ui_file_browser_new_folder_error_failed
                        )
                    }
                    return@withContext false
                }
                true
            }

            if (success) {
                dismissCreateFolderDialog()
                navigate(state.currentPath)
            }
        }
    }
}
