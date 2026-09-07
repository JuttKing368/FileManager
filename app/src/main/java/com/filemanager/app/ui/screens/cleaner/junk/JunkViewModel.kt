package com.filemanager.app.ui.screens.cleaner.junk

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.filemanager.app.data.repository.RecycleBinRepository
import com.filemanager.app.data.repository.RecycleOpResult
import com.filemanager.app.data.scanner.JunkCategory
import com.filemanager.app.data.scanner.JunkItem
import com.filemanager.app.data.scanner.JunkScanProgress
import com.filemanager.app.data.scanner.JunkScanner
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

enum class JunkScanPhase { IDLE, SCANNING, COMPLETE }

data class JunkUiState(
    val phase: JunkScanPhase = JunkScanPhase.IDLE,
    val filesScanned: Int = 0,
    val currentPath: String = "",
    val items: List<JunkItem> = emptyList(),
    val selectedPaths: Set<String> = emptySet(),
    val pendingDeleteCount: Int? = null
) {
    val groupedByCategory: Map<JunkCategory, List<JunkItem>> get() = items.groupBy { it.category }
    val totalBytes: Long get() = items.sumOf { it.sizeBytes }
    val selectedTotalBytes: Long get() = items.filter { it.file.absolutePath in selectedPaths }.sumOf { it.sizeBytes }
}

@HiltViewModel
class JunkViewModel @Inject constructor(
    private val scanner: JunkScanner,
    private val recycleBinRepository: RecycleBinRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(JunkUiState())
    val uiState: StateFlow<JunkUiState> = _uiState.asStateFlow()

    private var scanJob: Job? = null
    private val root = File("/storage/emulated/0")

    fun startScan() {
        scanJob?.cancel()
        _uiState.value = JunkUiState(phase = JunkScanPhase.SCANNING)
        scanJob = viewModelScope.launch {
            scanner.scan(root).collect { progress ->
                _uiState.value = when (progress) {
                    is JunkScanProgress.Scanning -> _uiState.value.copy(
                        filesScanned = progress.filesScanned,
                        currentPath = progress.currentPath
                    )
                    is JunkScanProgress.Complete -> _uiState.value.copy(
                        phase = JunkScanPhase.COMPLETE,
                        items = progress.items,
                        // Pre-select everything found — every category here was
                        // matched against a specific, evidence-based rule (spec
                        // §15: never flag anything without strong evidence), so
                        // there's nothing ambiguous being force-selected. The
                        // user can still deselect anything before confirming.
                        selectedPaths = progress.items.map { it.file.absolutePath }.toSet()
                    )
                }
            }
        }
    }

    fun cancelScan() {
        scanJob?.cancel()
        _uiState.value = _uiState.value.copy(phase = JunkScanPhase.IDLE)
    }

    fun toggleItem(path: String) {
        val current = _uiState.value.selectedPaths
        _uiState.value = _uiState.value.copy(selectedPaths = if (current.contains(path)) current - path else current + path)
    }

    fun toggleCategory(category: JunkCategory) {
        val categoryPaths = _uiState.value.groupedByCategory[category].orEmpty().map { it.file.absolutePath }
        val allSelected = categoryPaths.all { it in _uiState.value.selectedPaths }
        _uiState.value = _uiState.value.copy(
            selectedPaths = if (allSelected) _uiState.value.selectedPaths - categoryPaths.toSet()
            else _uiState.value.selectedPaths + categoryPaths
        )
    }

    fun selectAll() {
        _uiState.value = _uiState.value.copy(selectedPaths = _uiState.value.items.map { it.file.absolutePath }.toSet())
    }

    fun deselectAll() {
        _uiState.value = _uiState.value.copy(selectedPaths = emptySet())
    }

    fun requestDelete() {
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
            val movedPaths = results.filter { it.second is RecycleOpResult.Success }.map { it.first.absolutePath }.toSet()
            _uiState.value = _uiState.value.copy(
                items = _uiState.value.items.filterNot { it.file.absolutePath in movedPaths },
                selectedPaths = emptySet(),
                pendingDeleteCount = null
            )
        }
    }
}
