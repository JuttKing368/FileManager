package com.filemanager.app.ui.screens.cleaner.largefiles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.filemanager.app.data.model.FileEntry
import com.filemanager.app.data.model.SortOption
import com.filemanager.app.data.repository.FileOpResult
import com.filemanager.app.data.repository.FileOperationsRepository
import com.filemanager.app.data.repository.RecycleBinRepository
import com.filemanager.app.data.repository.RecycleOpResult
import com.filemanager.app.data.scanner.LargeFileScanProgress
import com.filemanager.app.data.scanner.LargeFileScanner
import com.filemanager.app.data.scanner.LargeFileThreshold
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

enum class LargeFileScanPhase { PICK_THRESHOLD, SCANNING, COMPLETE }

data class LargeFileUiState(
    val phase: LargeFileScanPhase = LargeFileScanPhase.PICK_THRESHOLD,
    val threshold: LargeFileThreshold = LargeFileThreshold.MB_100,
    val customThresholdMb: String = "",
    val filesScanned: Int = 0,
    val currentPath: String = "",
    val results: List<FileEntry> = emptyList(),
    val sortOption: SortOption = SortOption(),
    val selectedPaths: Set<String> = emptySet(),
    val pendingDeleteTargets: List<File>? = null,
    val showFolderPicker: Boolean = false,
    val errorMessage: String? = null
) {
    val isSelectionMode: Boolean get() = selectedPaths.isNotEmpty()
    val selectedTotalBytes: Long get() = results.filter { it.path in selectedPaths }.sumOf { it.sizeBytes }
}

@HiltViewModel
class LargeFileViewModel @Inject constructor(
    private val scanner: LargeFileScanner,
    private val repository: FileOperationsRepository,
    private val recycleBinRepository: RecycleBinRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LargeFileUiState())
    val uiState: StateFlow<LargeFileUiState> = _uiState.asStateFlow()

    private var scanJob: Job? = null
    private val root = File("/storage/emulated/0")

    fun selectThreshold(threshold: LargeFileThreshold) {
        _uiState.value = _uiState.value.copy(threshold = threshold)
    }

    fun setCustomThresholdMb(value: String) {
        _uiState.value = _uiState.value.copy(customThresholdMb = value.filter { it.isDigit() })
    }

    fun startScan() {
        val minBytes = _uiState.value.customThresholdMb.toLongOrNull()?.times(1024 * 1024)
            ?: _uiState.value.threshold.bytes

        scanJob?.cancel()
        _uiState.value = _uiState.value.copy(phase = LargeFileScanPhase.SCANNING, filesScanned = 0, selectedPaths = emptySet())
        scanJob = viewModelScope.launch {
            scanner.scan(root, minBytes).collect { progress ->
                _uiState.value = when (progress) {
                    is LargeFileScanProgress.Scanning -> _uiState.value.copy(
                        filesScanned = progress.filesScanned,
                        currentPath = progress.currentPath
                    )
                    is LargeFileScanProgress.Complete -> _uiState.value.copy(
                        phase = LargeFileScanPhase.COMPLETE,
                        results = repository.sortEntries(progress.results, _uiState.value.sortOption)
                    )
                }
            }
        }
    }

    fun cancelScan() {
        scanJob?.cancel()
        _uiState.value = _uiState.value.copy(phase = LargeFileScanPhase.PICK_THRESHOLD)
    }

    fun rescan() {
        _uiState.value = _uiState.value.copy(phase = LargeFileScanPhase.PICK_THRESHOLD, results = emptyList())
    }

    fun setSortOption(sort: SortOption) {
        _uiState.value = _uiState.value.copy(
            sortOption = sort,
            results = repository.sortEntries(_uiState.value.results, sort)
        )
    }

    fun toggleSelection(path: String) {
        val current = _uiState.value.selectedPaths
        _uiState.value = _uiState.value.copy(selectedPaths = if (current.contains(path)) current - path else current + path)
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
            val movedPaths = results.filter { it.second is RecycleOpResult.Success }.map { it.first.absolutePath }.toSet()
            _uiState.value = _uiState.value.copy(
                results = _uiState.value.results.filterNot { it.path in movedPaths },
                selectedPaths = emptySet(),
                pendingDeleteTargets = null
            )
        }
    }

    fun rename(entry: FileEntry, newName: String) {
        viewModelScope.launch {
            when (val result = repository.rename(File(entry.path), newName)) {
                is FileOpResult.Success -> {
                    val renamed = File(entry.path.substringBeforeLast('/'), newName)
                    _uiState.value = _uiState.value.copy(
                        results = _uiState.value.results.map {
                            if (it.path == entry.path) it.copy(name = newName, path = renamed.absolutePath) else it
                        }
                    )
                }
                is FileOpResult.Failure -> _uiState.value = _uiState.value.copy(errorMessage = result.message)
            }
        }
    }

    fun openFolderPicker() {
        _uiState.value = _uiState.value.copy(showFolderPicker = true)
    }

    fun dismissFolderPicker() {
        _uiState.value = _uiState.value.copy(showFolderPicker = false)
    }

    fun moveSelectedTo(destination: File) {
        val targets = _uiState.value.selectedPaths.map { File(it) }
        viewModelScope.launch {
            var failures = 0
            for (file in targets) {
                if (repository.move(file, destination) is FileOpResult.Failure) failures++
            }
            _uiState.value = _uiState.value.copy(
                results = _uiState.value.results.filterNot { it.path in _uiState.value.selectedPaths },
                selectedPaths = emptySet(),
                showFolderPicker = false,
                errorMessage = if (failures > 0) "$failures item(s) couldn't be moved" else null
            )
        }
    }

    fun setError(message: String?) {
        _uiState.value = _uiState.value.copy(errorMessage = message)
    }
}
