package com.filemanager.app.ui.screens.cleaner.duplicatefolders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.filemanager.app.data.repository.RecycleBinRepository
import com.filemanager.app.data.repository.RecycleOpResult
import com.filemanager.app.data.scanner.DuplicateFolderGroup
import com.filemanager.app.data.scanner.DuplicateFolderScanProgress
import com.filemanager.app.data.scanner.DuplicateFolderScanner
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

enum class FolderScanPhase { IDLE, SCANNING, COMPARING, COMPLETE }

data class DuplicateFolderUiState(
    val phase: FolderScanPhase = FolderScanPhase.IDLE,
    val foldersScanned: Int = 0,
    val currentPath: String = "",
    val compareProgress: Pair<Int, Int> = 0 to 0,
    val groups: List<DuplicateFolderGroup> = emptyList(),
    val selectedPaths: Set<String> = emptySet(),
    val pendingDeleteTargets: List<File>? = null
) {
    val selectedTotalBytes: Long get() = groups
        .flatMap { it.folders }
        .filter { it.file.absolutePath in selectedPaths }
        .sumOf { it.totalSizeBytes }
}

@HiltViewModel
class DuplicateFolderViewModel @Inject constructor(
    private val scanner: DuplicateFolderScanner,
    private val recycleBinRepository: RecycleBinRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DuplicateFolderUiState())
    val uiState: StateFlow<DuplicateFolderUiState> = _uiState.asStateFlow()

    private var scanJob: Job? = null
    private val root = File("/storage/emulated/0")

    fun startScan() {
        scanJob?.cancel()
        _uiState.value = DuplicateFolderUiState(phase = FolderScanPhase.SCANNING)
        scanJob = viewModelScope.launch {
            scanner.scan(root).collect { progress ->
                _uiState.value = when (progress) {
                    is DuplicateFolderScanProgress.Scanning -> _uiState.value.copy(
                        phase = FolderScanPhase.SCANNING,
                        foldersScanned = progress.foldersScanned,
                        currentPath = progress.currentPath
                    )
                    is DuplicateFolderScanProgress.Comparing -> _uiState.value.copy(
                        phase = FolderScanPhase.COMPARING,
                        compareProgress = progress.comparedCount to progress.totalCandidates
                    )
                    is DuplicateFolderScanProgress.Complete -> _uiState.value.copy(
                        phase = FolderScanPhase.COMPLETE,
                        groups = progress.groups
                    )
                }
            }
        }
    }

    fun cancelScan() {
        scanJob?.cancel()
        _uiState.value = _uiState.value.copy(phase = FolderScanPhase.IDLE)
    }

    fun toggleFolderSelection(path: String) {
        val current = _uiState.value.selectedPaths
        _uiState.value = _uiState.value.copy(
            selectedPaths = if (current.contains(path)) current - path else current + path
        )
    }

    /** Keeps the largest-size (or first, ties) folder in each group and selects the rest. */
    fun selectAllExceptOneEach() {
        val toSelect = _uiState.value.groups.flatMap { group ->
            val keep = group.folders.first()
            group.folders.filter { it != keep }.map { it.file.absolutePath }
        }
        _uiState.value = _uiState.value.copy(selectedPaths = toSelect.toSet())
    }

    fun clearSelection() {
        _uiState.value = _uiState.value.copy(selectedPaths = emptySet())
    }

    fun requestDeleteSelected() {
        val targets = _uiState.value.selectedPaths.map { File(it) }
        if (targets.isNotEmpty()) _uiState.value = _uiState.value.copy(pendingDeleteTargets = targets)
    }

    fun cancelDelete() {
        _uiState.value = _uiState.value.copy(pendingDeleteTargets = null)
    }

    fun confirmDelete() {
        val targets = _uiState.value.pendingDeleteTargets ?: return
        viewModelScope.launch {
            val results = recycleBinRepository.moveMultipleToRecycleBin(targets)
            val movedPaths = results.filter { it.second is RecycleOpResult.Success }
                .map { it.first.absolutePath }
                .toSet()

            val updatedGroups = _uiState.value.groups
                .map { group -> group.copy(folders = group.folders.filterNot { it.file.absolutePath in movedPaths }) }
                .filter { it.folders.size >= 2 }

            _uiState.value = _uiState.value.copy(
                groups = updatedGroups,
                selectedPaths = emptySet(),
                pendingDeleteTargets = null
            )
        }
    }
}
