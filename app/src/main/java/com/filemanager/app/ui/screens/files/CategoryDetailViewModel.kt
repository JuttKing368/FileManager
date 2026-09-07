package com.filemanager.app.ui.screens.files

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.filemanager.app.data.model.FileCategory
import com.filemanager.app.data.model.FileEntry
import com.filemanager.app.data.model.SortOption
import com.filemanager.app.data.repository.FileOperationsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class CategoryDetailUiState(
    val category: FileCategory = FileCategory.OTHER,
    val entries: List<FileEntry> = emptyList(),
    val isLoading: Boolean = true,
    val sortOption: SortOption = SortOption()
)

/**
 * Backs the screen opened from a Home category card. Unlike the folder
 * browser (which lists one directory at a time — cheap), this does a
 * one-time recursive scan of external storage filtered to the requested
 * category. It runs off the main thread and only once per screen visit,
 * so it doesn't fight with the performance requirements around avoiding
 * repeated full-device traversal.
 */
@HiltViewModel
class CategoryDetailViewModel @Inject constructor(
    private val repository: FileOperationsRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(CategoryDetailUiState())
    val uiState: StateFlow<CategoryDetailUiState> = _uiState.asStateFlow()

    init {
        val categoryName = savedStateHandle.get<String>("categoryName") ?: FileCategory.OTHER.name
        val category = runCatching { FileCategory.valueOf(categoryName) }.getOrDefault(FileCategory.OTHER)
        _uiState.value = _uiState.value.copy(category = category)
        loadCategory(category)
    }

    private fun loadCategory(category: FileCategory) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val root = File("/storage/emulated/0")
            // Empty query with a category filter matches every filename;
            // searchRecursive already supports this combination.
            val results = repository.searchRecursive(root, query = "", category = category, maxResults = 2000)
            _uiState.value = _uiState.value.copy(
                entries = repository.sortEntries(results, _uiState.value.sortOption),
                isLoading = false
            )
        }
    }

    fun setSortOption(sort: SortOption) {
        _uiState.value = _uiState.value.copy(
            sortOption = sort,
            entries = repository.sortEntries(_uiState.value.entries, sort)
        )
    }
}
