package com.filemanager.app.ui.screens.files.components

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.filemanager.app.data.repository.FileOperationsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class FolderPickerUiState(
    val currentDir: File = File("/storage/emulated/0"),
    val subfolders: List<File> = emptyList(),
    val isLoading: Boolean = true
)

/**
 * Backs [FolderPickerDialog] — a directories-only browser used anywhere
 * the app needs the user to pick a destination (Move, and later the
 * Vault's "add to vault" flow). Deliberately separate from
 * [com.filemanager.app.ui.screens.files.FilesViewModel] since it only
 * ever shows folders and never mutates anything itself.
 */
@HiltViewModel
class FolderPickerViewModel @Inject constructor(
    private val repository: FileOperationsRepository
) : ViewModel() {

    private val root = File("/storage/emulated/0")
    private val backStack = ArrayDeque<File>()
    private val _uiState = MutableStateFlow(FolderPickerUiState(currentDir = root))
    val uiState: StateFlow<FolderPickerUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun openFolder(folder: File) {
        backStack.addLast(_uiState.value.currentDir)
        _uiState.value = _uiState.value.copy(currentDir = folder)
        load()
    }

    /** Returns false if already at the root. */
    fun goUp(): Boolean {
        val parent = backStack.removeLastOrNull() ?: return false
        _uiState.value = _uiState.value.copy(currentDir = parent)
        load()
        return true
    }

    private fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val entries = repository.listDirectory(_uiState.value.currentDir)
            _uiState.value = _uiState.value.copy(
                subfolders = entries.filter { it.isDirectory }.map { File(it.path) }.sortedBy { it.name.lowercase() },
                isLoading = false
            )
        }
    }
}
