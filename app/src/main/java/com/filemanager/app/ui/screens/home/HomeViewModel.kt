package com.filemanager.app.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.filemanager.app.data.model.FileCategory
import com.filemanager.app.data.repository.StorageOverview
import com.filemanager.app.data.repository.StorageStatsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val isLoading: Boolean = true,
    val overview: StorageOverview = StorageOverview(0, 0, 0),
    val categoryBreakdown: Map<FileCategory, Long> = emptyMap(),
    // Placeholders until their respective scanner modules are wired in;
    // each card shows real numbers once that module ships.
    val duplicateFilesRecoverableBytes: Long? = null,
    val largeFilesCount: Int? = null,
    val emptyFoldersCount: Int? = null,
    val junkBytes: Long? = null,
    val vaultLocked: Boolean = true
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val storageStatsRepository: StorageStatsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val overview = storageStatsRepository.getStorageOverview()
            val breakdown = storageStatsRepository.getCategoryBreakdown()
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                overview = overview,
                categoryBreakdown = breakdown
            )
        }
    }
}
