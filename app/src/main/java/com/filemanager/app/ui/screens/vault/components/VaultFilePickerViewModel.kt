package com.filemanager.app.ui.screens.vault.components

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.filemanager.app.data.model.FileEntry
import com.filemanager.app.data.repository.FileOperationsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class VaultFilePickerUiState(
    val currentDir: File = File("/storage/emulated/0"),
    val entries: List<FileEntry> = emptyList(),
    val selectedPaths: Set<String> = emptySet(),
    val isLoading: Boolean = true
)

@HiltViewModel
class VaultFilePickerViewModel @Inject constructor(
    private val repository: FileOperationsRepository
) : ViewModel() {

    private val root = File("/storage/emulated/0")
    private val backStack = ArrayDeque<File>()
    private val _uiState = MutableStateFlow(VaultFilePickerUiState(currentDir = root))
    val uiState: StateFlow<VaultFilePickerUiState> = _uiState.asStateFlow()

    init { load() }

    fun openFolder(folder: File) {
        backStack.addLast(_uiState.value.currentDir)
        _uiState.value = _uiState.value.copy(currentDir = folder)
        load()
    }

    fun goUp(): Boolean {
        val parent = backStack.removeLastOrNull() ?: return false
        _uiState.value = _uiState.value.copy(currentDir = parent)
        load()
        return true
    }

    fun toggleFile(path: String) {
        val current = _uiState.value.selectedPaths
        _uiState.value = _uiState.value.copy(selectedPaths = if (current.contains(path)) current - path else current + path)
    }

    fun selectedFiles(): List<File> = _uiState.value.selectedPaths.map { File(it) }

    private fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val entries = repository.listDirectory(_uiState.value.currentDir)
            _uiState.value = _uiState.value.copy(
                entries = repository.sortEntries(entries, com.filemanager.app.data.model.SortOption()),
                isLoading = false
            )
        }
    }
}
