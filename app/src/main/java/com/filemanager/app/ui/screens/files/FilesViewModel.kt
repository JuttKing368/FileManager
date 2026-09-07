package com.filemanager.app.ui.screens.files

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.filemanager.app.data.local.RecycleBinEntity
import com.filemanager.app.data.model.FileEntry
import com.filemanager.app.data.model.SortOption
import com.filemanager.app.data.model.ViewMode
import com.filemanager.app.data.repository.AppSettingsRepository
import com.filemanager.app.data.repository.FileOpResult
import com.filemanager.app.data.repository.FileOperationsRepository
import com.filemanager.app.data.repository.RecycleBinRepository
import com.filemanager.app.data.repository.RecycleOpResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/** What's queued for a Copy/Move — cleared once pasted or cancelled. */
sealed class ClipboardOp {
    data object None : ClipboardOp()
    data class Copy(val files: List<File>) : ClipboardOp()
    data class Move(val files: List<File>) : ClipboardOp()
}

data class FilesUiState(
    val currentDir: File = File("/storage/emulated/0"),
    val entries: List<FileEntry> = emptyList(),
    val isLoading: Boolean = true,
    val viewMode: ViewMode = ViewMode.LIST,
    val sortOption: SortOption = SortOption(),
    val selectedPaths: Set<String> = emptySet(),
    val searchQuery: String = "",
    val isSearchActive: Boolean = false,
    val searchResults: List<FileEntry> = emptyList(),
    val clipboard: ClipboardOp = ClipboardOp.None,
    val errorMessage: String? = null,
    val pendingDeleteTargets: List<File>? = null, // non-null while a delete confirmation is showing
    val recentlyRecycled: List<RecycleBinEntity>? = null // non-null right after a delete, drives the "Undo" snackbar
) {
    val isSelectionMode: Boolean get() = selectedPaths.isNotEmpty()
    val displayedEntries: List<FileEntry> get() = if (isSearchActive) searchResults else entries
}

@HiltViewModel
class FilesViewModel @Inject constructor(
    private val repository: FileOperationsRepository,
    private val recycleBinRepository: RecycleBinRepository,
    private val settingsRepository: AppSettingsRepository
) : ViewModel() {

    private val rootDir = File("/storage/emulated/0")
    private val _uiState = MutableStateFlow(FilesUiState(currentDir = rootDir))
    val uiState: StateFlow<FilesUiState> = _uiState.asStateFlow()

    // Back stack of directories, so the system back button retraces folder history.
    private val backStack = ArrayDeque<File>()

    init {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(viewMode = settingsRepository.defaultViewModeOnce())
        }
        loadCurrentDirectory()
    }

    fun navigateInto(folder: FileEntry) {
        if (!folder.isDirectory) return
        backStack.addLast(_uiState.value.currentDir)
        _uiState.value = _uiState.value.copy(currentDir = File(folder.path), selectedPaths = emptySet())
        loadCurrentDirectory()
    }

    /** Returns true if it navigated up a level; false if already at the top (caller should exit the screen/tab). */
    fun navigateUp(): Boolean {
        val parent = backStack.removeLastOrNull() ?: return false
        _uiState.value = _uiState.value.copy(currentDir = parent, selectedPaths = emptySet())
        loadCurrentDirectory()
        return true
    }

    fun refresh() = loadCurrentDirectory()

    private fun loadCurrentDirectory() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val entries = repository.listDirectory(_uiState.value.currentDir)
            val sorted = repository.sortEntries(entries, _uiState.value.sortOption)
            _uiState.value = _uiState.value.copy(entries = sorted, isLoading = false)
        }
    }

    fun setSortOption(sort: SortOption) {
        _uiState.value = _uiState.value.copy(
            sortOption = sort,
            entries = repository.sortEntries(_uiState.value.entries, sort)
        )
    }

    fun setViewMode(mode: ViewMode) {
        _uiState.value = _uiState.value.copy(viewMode = mode)
    }

    // ---- Search ----

    fun setSearchActive(active: Boolean) {
        _uiState.value = _uiState.value.copy(isSearchActive = active, searchQuery = "", searchResults = emptyList())
    }

    fun onSearchQueryChange(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
        if (query.length < 2) {
            _uiState.value = _uiState.value.copy(searchResults = emptyList())
            return
        }
        viewModelScope.launch {
            val results = repository.searchRecursive(_uiState.value.currentDir, query)
            _uiState.value = _uiState.value.copy(searchResults = repository.sortEntries(results, _uiState.value.sortOption))
        }
    }

    // ---- Selection ----

    fun toggleSelection(entry: FileEntry) {
        val current = _uiState.value.selectedPaths
        _uiState.value = _uiState.value.copy(
            selectedPaths = if (current.contains(entry.path)) current - entry.path else current + entry.path
        )
    }

    fun selectAll() {
        _uiState.value = _uiState.value.copy(
            selectedPaths = _uiState.value.displayedEntries.map { it.path }.toSet()
        )
    }

    fun clearSelection() {
        _uiState.value = _uiState.value.copy(selectedPaths = emptySet())
    }

    private fun selectedFiles(): List<File> =
        _uiState.value.selectedPaths.map { File(it) }

    // ---- Operations ----

    fun createFolder(name: String) {
        viewModelScope.launch {
            when (val result = repository.createFolder(_uiState.value.currentDir, name)) {
                is FileOpResult.Success -> loadCurrentDirectory()
                is FileOpResult.Failure -> setError(result.message)
            }
        }
    }

    fun rename(entry: FileEntry, newName: String) {
        viewModelScope.launch {
            when (val result = repository.rename(File(entry.path), newName)) {
                is FileOpResult.Success -> loadCurrentDirectory()
                is FileOpResult.Failure -> setError(result.message)
            }
        }
    }

    fun requestDeleteSelected() {
        val targets = selectedFiles()
        if (targets.isEmpty()) return
        viewModelScope.launch {
            if (settingsRepository.confirmBeforeDeleteOnce()) {
                _uiState.value = _uiState.value.copy(pendingDeleteTargets = targets)
            } else {
                _uiState.value = _uiState.value.copy(pendingDeleteTargets = targets)
                confirmDelete()
            }
        }
    }

    fun requestDeleteSingle(entry: FileEntry) {
        val targets = listOf(File(entry.path))
        viewModelScope.launch {
            if (settingsRepository.confirmBeforeDeleteOnce()) {
                _uiState.value = _uiState.value.copy(pendingDeleteTargets = targets)
            } else {
                _uiState.value = _uiState.value.copy(pendingDeleteTargets = targets)
                confirmDelete()
            }
        }
    }

    fun cancelDelete() {
        _uiState.value = _uiState.value.copy(pendingDeleteTargets = null)
    }

    fun confirmDelete() {
        val targets = _uiState.value.pendingDeleteTargets ?: return
        viewModelScope.launch {
            val results = recycleBinRepository.moveMultipleToRecycleBin(targets)
            val failures = results.count { it.second is RecycleOpResult.Failure }
            val recycled = results.mapNotNull { (it.second as? RecycleOpResult.Success)?.entity }
            _uiState.value = _uiState.value.copy(
                pendingDeleteTargets = null,
                selectedPaths = emptySet(),
                recentlyRecycled = recycled.ifEmpty { null }
            )
            if (failures > 0) {
                setError("Couldn't move $failures item(s) to Recycle Bin")
            }
            loadCurrentDirectory()
        }
    }

    fun dismissRecycledNotice() {
        _uiState.value = _uiState.value.copy(recentlyRecycled = null)
    }

    fun undoRecycle() {
        val entities = _uiState.value.recentlyRecycled ?: return
        viewModelScope.launch {
            entities.forEach { recycleBinRepository.restore(it) }
            _uiState.value = _uiState.value.copy(recentlyRecycled = null)
            loadCurrentDirectory()
        }
    }

    fun copySelectedToClipboard() {
        _uiState.value = _uiState.value.copy(clipboard = ClipboardOp.Copy(selectedFiles()), selectedPaths = emptySet())
    }

    fun moveSelectedToClipboard() {
        _uiState.value = _uiState.value.copy(clipboard = ClipboardOp.Move(selectedFiles()), selectedPaths = emptySet())
    }

    fun clearClipboard() {
        _uiState.value = _uiState.value.copy(clipboard = ClipboardOp.None)
    }

    fun pasteIntoCurrentDirectory() {
        val clipboard = _uiState.value.clipboard
        val destination = _uiState.value.currentDir
        viewModelScope.launch {
            val filesToProcess = when (clipboard) {
                is ClipboardOp.Copy -> clipboard.files
                is ClipboardOp.Move -> clipboard.files
                ClipboardOp.None -> emptyList()
            }
            var failures = 0
            for (file in filesToProcess) {
                val result = when (clipboard) {
                    is ClipboardOp.Copy -> repository.copy(file, destination)
                    is ClipboardOp.Move -> repository.move(file, destination)
                    ClipboardOp.None -> FileOpResult.Success
                }
                if (result is FileOpResult.Failure) failures++
            }
            _uiState.value = _uiState.value.copy(clipboard = ClipboardOp.None)
            if (failures > 0) setError("$failures item(s) couldn't be pasted")
            loadCurrentDirectory()
        }
    }

    fun setError(message: String?) {
        _uiState.value = _uiState.value.copy(errorMessage = message)
    }

    suspend fun getFileInfo(entry: FileEntry) = repository.getFileInfo(File(entry.path))
}
