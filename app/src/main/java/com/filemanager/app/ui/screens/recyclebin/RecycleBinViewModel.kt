package com.filemanager.app.ui.screens.recyclebin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.filemanager.app.data.local.RecycleBinEntity
import com.filemanager.app.data.repository.FileOpResult
import com.filemanager.app.data.repository.RecycleBinRepository
import com.filemanager.app.data.repository.RecycleOpResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RecycleBinUiState(
    val items: List<RecycleBinEntity> = emptyList(),
    val selectedIds: Set<Long> = emptySet(),
    val pendingPermanentDelete: List<RecycleBinEntity>? = null,
    val pendingEmptyBin: Boolean = false,
    val errorMessage: String? = null
) {
    val isSelectionMode: Boolean get() = selectedIds.isNotEmpty()
    val totalSizeBytes: Long get() = items.sumOf { it.sizeBytes }
}

/** Local-only UI state that doesn't belong in Room (dialogs, transient errors). */
private data class TransientState(
    val pendingPermanentDelete: List<RecycleBinEntity>? = null,
    val pendingEmptyBin: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class RecycleBinViewModel @Inject constructor(
    private val repository: RecycleBinRepository
) : ViewModel() {

    private val selectedIds = MutableStateFlow<Set<Long>>(emptySet())
    private val transientState = MutableStateFlow(TransientState())

    val uiState: StateFlow<RecycleBinUiState> = combine(
        repository.observeAll(),
        selectedIds,
        transientState
    ) { items, selected, transient ->
        RecycleBinUiState(
            items = items,
            selectedIds = selected,
            pendingPermanentDelete = transient.pendingPermanentDelete,
            pendingEmptyBin = transient.pendingEmptyBin,
            errorMessage = transient.errorMessage
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), RecycleBinUiState())

    fun toggleSelection(id: Long) {
        selectedIds.value = if (selectedIds.value.contains(id)) selectedIds.value - id else selectedIds.value + id
    }

    fun clearSelection() {
        selectedIds.value = emptySet()
    }

    fun restore(entity: RecycleBinEntity) {
        viewModelScope.launch {
            val result = repository.restore(entity)
            if (result is RecycleOpResult.Failure) setError(result.message)
        }
    }

    fun restoreSelected() {
        viewModelScope.launch {
            val items = uiState.value.items.filter { it.id in selectedIds.value }
            items.forEach { repository.restore(it) }
            clearSelection()
        }
    }

    fun requestPermanentDelete(entity: RecycleBinEntity) {
        transientState.value = transientState.value.copy(pendingPermanentDelete = listOf(entity))
    }

    fun requestPermanentDeleteSelected() {
        val items = uiState.value.items.filter { it.id in selectedIds.value }
        if (items.isNotEmpty()) transientState.value = transientState.value.copy(pendingPermanentDelete = items)
    }

    fun cancelPermanentDelete() {
        transientState.value = transientState.value.copy(pendingPermanentDelete = null)
    }

    fun confirmPermanentDelete() {
        val targets = uiState.value.pendingPermanentDelete ?: return
        viewModelScope.launch {
            var failures = 0
            targets.forEach { if (repository.permanentlyDelete(it) is FileOpResult.Failure) failures++ }
            transientState.value = transientState.value.copy(pendingPermanentDelete = null)
            clearSelection()
            if (failures > 0) setError("Couldn't permanently delete $failures item(s)")
        }
    }

    fun requestEmptyBin() {
        if (uiState.value.items.isNotEmpty()) transientState.value = transientState.value.copy(pendingEmptyBin = true)
    }

    fun cancelEmptyBin() {
        transientState.value = transientState.value.copy(pendingEmptyBin = false)
    }

    fun confirmEmptyBin() {
        viewModelScope.launch {
            val result = repository.emptyBin(uiState.value.items)
            transientState.value = transientState.value.copy(pendingEmptyBin = false)
            if (result is FileOpResult.Failure) setError(result.message)
        }
    }

    fun setError(message: String?) {
        transientState.value = transientState.value.copy(errorMessage = message)
    }
}
