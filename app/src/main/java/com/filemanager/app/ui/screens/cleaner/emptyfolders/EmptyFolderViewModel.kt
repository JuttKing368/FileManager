package com.filemanager.app.ui.screens.cleaner.emptyfolders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.filemanager.app.data.repository.RecycleBinRepository
import com.filemanager.app.data.repository.RecycleOpResult
import com.filemanager.app.data.scanner.EmptyFolderEntry
import com.filemanager.app.data.scanner.EmptyFolderScanProgress
import com.filemanager.app.data.scanner.EmptyFolderScanner
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

enum class EmptyFolderScanPhase { IDLE, SCANNING, COMPLETE }

data class EmptyFolderUiState(
    val phase: EmptyFolderScanPhase = EmptyFolderScanPhase.IDLE,
    val foldersScanned: Int = 0,
    val currentPath: String = "",
    val results: List<EmptyFolderEntry> = emptyList(),
    val selectedPaths: Set<String> = emptySet(),
    val pendingDeleteCount: Int? = null
)

@HiltViewModel
class EmptyFolderViewModel @Inject constructor(
    private val scanner: EmptyFolderScanner,
    private val recycleBinRepository: RecycleBinRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(EmptyFolderUiState())
    val uiState: StateFlow<EmptyFolderUiState> = _uiState.asStateFlow()

    private var scanJob: Job? = null
    private val root = File("/storage/emulated/0")

    fun startScan() {
        scanJob?.cancel()
        _uiState.value = EmptyFolderUiState(phase = EmptyFolderScanPhase.SCANNING)
        scanJob = viewModelScope.launch {
            scanner.scan(root).collect { progress ->
                _uiState.value = when (progress) {
                    is EmptyFolderScanProgress.Scanning -> _uiState.value.copy(
                        foldersScanned = progress.foldersScanned,
                        currentPath = progress.currentPath
                    )
                    is EmptyFolderScanProgress.Complete -> _uiState.value.copy(
                        phase = EmptyFolderScanPhase.COMPLETE,
                        results = progress.results
                    )
                }
            }
        }
    }

    fun cancelScan() {
        scanJob?.cancel()
        _uiState.value = _uiState.value.copy(phase = EmptyFolderScanPhase.IDLE)
    }

    fun toggleSelection(path: String) {
        val current = _uiState.value.selectedPaths
        _uiState.value = _uiState.value.copy(
            selectedPaths = if (current.contains(path)) current - path else current + path
        )
    }

    fun selectAll() {
        _uiState.value = _uiState.value.copy(
            selectedPaths = _uiState.value.results.map { it.file.absolutePath }.toSet()
        )
    }

    fun clearSelection() {
        _uiState.value = _uiState.value.copy(selectedPaths = emptySet())
    }

    fun requestDeleteSelected() {
        if (_uiState.value.selectedPaths.isNotEmpty()) {
            _uiState.value = _uiState.value.copy(pendingDeleteCount = _uiState.value.selectedPaths.size)
        }
    }

    fun cancelDelete() {
        _uiState.value = _uiState.value.copy(pendingDeleteCount = null)
    }

    fun confirmDelete() {
        val targets = _uiState.value.selectedPaths.map { File(it) }
        viewModelScope.launch {
            // Empty folders only ever need a plain move (no recursion needed by
            // definition), but moveMultipleToRecycleBin's per-item error handling
            // still guards against races (folder removed/populated during the scan).
            val results = recycleBinRepository.moveMultipleToRecycleBin(targets)
            val movedPaths = results.filter { it.second is RecycleOpResult.Success }
                .map { it.first.absolutePath }
                .toSet()

            _uiState.value = _uiState.value.copy(
                results = _uiState.value.results.filterNot { it.file.absolutePath in movedPaths },
                selectedPaths = emptySet(),
                pendingDeleteCount = null
            )
        }
    }
}
