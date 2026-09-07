package com.filemanager.app.ui.screens.cleaner.duplicates

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.filemanager.app.data.repository.RecycleBinRepository
import com.filemanager.app.data.repository.RecycleOpResult
import com.filemanager.app.data.scanner.DuplicateFileScanner
import com.filemanager.app.data.scanner.DuplicateGroup
import com.filemanager.app.data.scanner.DuplicateScanProgress
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

enum class ScanPhase { IDLE, SCANNING, HASHING, COMPLETE }

data class DuplicateScannerUiState(
    val phase: ScanPhase = ScanPhase.IDLE,
    val filesScanned: Int = 0,
    val currentPath: String = "",
    val hashProgress: Pair<Int, Int> = 0 to 0,
    val groups: List<DuplicateGroup> = emptyList(),
    val selectedPaths: Set<String> = emptySet(),
    val pendingDeleteCount: Int? = null,
    val errorMessage: String? = null
) {
    val totalRecoverableBytes: Long get() = groups.sumOf { it.recoverableBytes }
    val selectedTotalBytes: Long get() = groups.sumOf { group ->
        group.files.filter { it.absolutePath in selectedPaths }.sumOf { it.length() }
    }
}

@HiltViewModel
class DuplicateScannerViewModel @Inject constructor(
    private val scanner: DuplicateFileScanner,
    private val recycleBinRepository: RecycleBinRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DuplicateScannerUiState())
    val uiState: StateFlow<DuplicateScannerUiState> = _uiState.asStateFlow()

    private var scanJob: Job? = null
    private val root = File("/storage/emulated/0")

    fun startScan() {
        scanJob?.cancel()
        _uiState.value = DuplicateScannerUiState(phase = ScanPhase.SCANNING)
        scanJob = viewModelScope.launch {
            scanner.scan(root).collect { progress ->
                _uiState.value = when (progress) {
                    is DuplicateScanProgress.Scanning -> _uiState.value.copy(
                        phase = ScanPhase.SCANNING,
                        filesScanned = progress.filesScanned,
                        currentPath = progress.currentPath
                    )
                    is DuplicateScanProgress.Hashing -> _uiState.value.copy(
                        phase = ScanPhase.HASHING,
                        hashProgress = progress.candidatesHashed to progress.totalCandidates
                    )
                    is DuplicateScanProgress.Complete -> _uiState.value.copy(
                        phase = ScanPhase.COMPLETE,
                        groups = progress.groups
                    )
                    is DuplicateScanProgress.Error -> _uiState.value.copy(
                        errorMessage = progress.message
                    )
                }
            }
        }
    }

    fun cancelScan() {
        scanJob?.cancel()
        _uiState.value = _uiState.value.copy(phase = ScanPhase.IDLE)
    }

    fun toggleFileSelection(path: String) {
        val current = _uiState.value.selectedPaths
        _uiState.value = _uiState.value.copy(
            selectedPaths = if (current.contains(path)) current - path else current + path
        )
    }

    /** Selects every file in the group except the oldest (kept as the presumed original). */
    fun selectAllExceptOldestInGroup(group: DuplicateGroup) {
        val toSelect = group.files.filter { it != group.oldestFile }.map { it.absolutePath }
        _uiState.value = _uiState.value.copy(selectedPaths = _uiState.value.selectedPaths + toSelect)
    }

    fun selectAllDuplicatesKeepingOneEach() {
        val toSelect = _uiState.value.groups.flatMap { group ->
            group.files.filter { it != group.oldestFile }.map { it.absolutePath }
        }
        _uiState.value = _uiState.value.copy(selectedPaths = toSelect.toSet())
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
            val results = recycleBinRepository.moveMultipleToRecycleBin(targets)
            val movedPaths = results.filter { it.second is RecycleOpResult.Success }
                .map { it.first.absolutePath }
                .toSet()

            val updatedGroups = _uiState.value.groups
                .map { group -> group.copy(files = group.files.filterNot { it.absolutePath in movedPaths }) }
                .filter { it.files.size >= 2 } // a group stops being a "duplicate" once only one copy remains

            _uiState.value = _uiState.value.copy(
                groups = updatedGroups,
                selectedPaths = emptySet(),
                pendingDeleteCount = null
            )
        }
    }
}
