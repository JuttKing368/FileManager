package com.filemanager.app.ui.screens.vault.content

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.filemanager.app.data.local.VaultItemEntity
import com.filemanager.app.data.repository.VaultOpResult
import com.filemanager.app.data.repository.VaultRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class VaultContentUiState(
    val items: List<VaultItemEntity> = emptyList(),
    val isAdding: Boolean = false,
    val errorMessage: String? = null,
    val pendingRemove: VaultItemEntity? = null
)

@HiltViewModel
class VaultContentViewModel @Inject constructor(
    private val repository: VaultRepository
) : ViewModel() {

    private val transientError = MutableStateFlow<String?>(null)
    private val isAdding = MutableStateFlow(false)
    private val pendingRemove = MutableStateFlow<VaultItemEntity?>(null)

    val uiState: StateFlow<VaultContentUiState> = combine(
        repository.observeAll(), isAdding, transientError, pendingRemove
    ) { items, adding, error, pending ->
        VaultContentUiState(items = items, isAdding = adding, errorMessage = error, pendingRemove = pending)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), VaultContentUiState())

    fun addFiles(files: List<File>) {
        viewModelScope.launch {
            isAdding.value = true
            var failures = 0
            files.forEach { if (repository.addToVault(it) is VaultOpResult.Failure) failures++ }
            isAdding.value = false
            if (failures > 0) transientError.value = "$failures file(s) couldn't be added"
        }
    }

    fun requestRemove(entity: VaultItemEntity) {
        pendingRemove.value = entity
    }

    fun cancelRemove() {
        pendingRemove.value = null
    }

    fun confirmRemove() {
        val entity = pendingRemove.value ?: return
        viewModelScope.launch {
            val result = repository.removeFromVault(entity)
            pendingRemove.value = null
            if (result is VaultOpResult.Failure) transientError.value = result.message
        }
    }

    fun requestView(entity: VaultItemEntity, onReady: (File) -> Unit) {
        viewModelScope.launch {
            val file = repository.decryptToCache(entity)
            onReady(file)
        }
    }

    fun setError(message: String?) {
        transientError.value = message
    }
}
