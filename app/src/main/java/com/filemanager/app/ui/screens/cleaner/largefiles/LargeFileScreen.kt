package com.filemanager.app.ui.screens.cleaner.largefiles

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.filemanager.app.data.model.FileEntry
import com.filemanager.app.data.scanner.LargeFileThreshold
import com.filemanager.app.ui.screens.files.components.DeleteConfirmDialog
import com.filemanager.app.ui.screens.files.components.FileListRow
import com.filemanager.app.ui.screens.files.components.FolderPickerDialog
import com.filemanager.app.ui.screens.files.components.SortMenu
import com.filemanager.app.ui.screens.files.components.TextInputDialog
import com.filemanager.app.util.FormatUtils
import com.filemanager.app.util.ShareUtils
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LargeFileScreen(
    navController: NavHostController,
    viewModel: LargeFileViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var sortMenuExpanded by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<FileEntry?>(null) }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.setError(null)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(if (state.isSelectionMode) "${state.selectedPaths.size} selected" else "Large Files") },
                navigationIcon = {
                    IconButton(onClick = {
                        if (state.isSelectionMode) viewModel.clearSelection() else navController.popBackStack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (state.phase == LargeFileScanPhase.COMPLETE && !state.isSelectionMode) {
                        Box {
                            IconButton(onClick = { sortMenuExpanded = true }) {
                                Icon(Icons.Filled.Sort, contentDescription = "Sort")
                            }
                            SortMenu(
                                expanded = sortMenuExpanded,
                                current = state.sortOption,
                                onDismiss = { sortMenuExpanded = false },
                                onSelect = viewModel::setSortOption
                            )
                        }
                        TextButton(onClick = viewModel::rescan) { Text("Rescan") }
                    }
                }
            )
        },
        bottomBar = {
            if (state.isSelectionMode) {
                BottomAppBar {
                    IconButton(onClick = { state.selectedPaths.singleOrNull()?.let { path -> state.results.find { it.path == path }?.let { renameTarget = it } } }) {
                        Icon(Icons.Filled.DriveFileRenameOutline, contentDescription = "Rename")
                    }
                    IconButton(onClick = viewModel::openFolderPicker) {
                        Icon(Icons.Filled.DriveFileMove, contentDescription = "Move")
                    }
                    if (state.selectedPaths.size == 1) {
                        IconButton(onClick = {
                            val file = File(state.selectedPaths.first())
                            context.startActivity(android.content.Intent.createChooser(ShareUtils.shareIntentFor(context, file), null))
                        }) {
                            Icon(Icons.Filled.Share, contentDescription = "Share")
                        }
                    }
                    IconButton(onClick = viewModel::requestDeleteSelected) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    ) { padding ->
        Crossfade(targetState = state.phase, modifier = Modifier.padding(padding).fillMaxSize(), label = "scanPhase") { phase ->
            when (phase) {
                LargeFileScanPhase.PICK_THRESHOLD -> ThresholdPicker(
                    selected = state.threshold,
                    customValue = state.customThresholdMb,
                    onSelect = viewModel::selectThreshold,
                    onCustomChange = viewModel::setCustomThresholdMb,
                    onScan = viewModel::startScan
                )
                LargeFileScanPhase.SCANNING -> Column(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator()
                    Text("Scanning...", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 20.dp))
                    Text("Files scanned: ${state.filesScanned}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        state.currentPath,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    TextButton(onClick = viewModel::cancelScan, modifier = Modifier.padding(top = 12.dp)) { Text("Cancel") }
                }
                LargeFileScanPhase.COMPLETE -> {
                    if (state.results.isEmpty()) {
                        Column(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Filled.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(48.dp))
                            Text("No files found above ${effectiveThresholdLabel(state)}", modifier = Modifier.padding(top = 8.dp))
                        }
                    } else {
                        Column(modifier = Modifier.fillMaxSize()) {
                            Text(
                                "${state.results.size} files ≥ ${effectiveThresholdLabel(state)}",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(16.dp)
                            )
                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                items(state.results, key = { it.path }) { entry ->
                                    FileListRow(
                                        modifier = Modifier.animateItem(),
                                        entry = entry,
                                        isSelected = state.selectedPaths.contains(entry.path),
                                        isSelectionMode = state.isSelectionMode,
                                        onClick = {
                                            if (state.isSelectionMode) {
                                                viewModel.toggleSelection(entry.path)
                                            } else {
                                                runCatching { context.startActivity(ShareUtils.openIntentFor(context, File(entry.path))) }
                                            }
                                        },
                                        onLongClick = { viewModel.toggleSelection(entry.path) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    state.pendingDeleteTargets?.let { targets ->
        DeleteConfirmDialog(
            itemCount = targets.size,
            totalSizeBytes = state.selectedTotalBytes,
            onDismiss = viewModel::cancelDelete,
            onConfirm = viewModel::confirmDelete
        )
    }

    renameTarget?.let { target ->
        TextInputDialog(
            title = "Rename",
            initialValue = target.name,
            confirmLabel = "Rename",
            onDismiss = { renameTarget = null },
            onConfirm = { newName ->
                viewModel.rename(target, newName)
                viewModel.clearSelection()
                renameTarget = null
            }
        )
    }

    if (state.showFolderPicker) {
        FolderPickerDialog(
            onDismiss = viewModel::dismissFolderPicker,
            onFolderSelected = viewModel::moveSelectedTo
        )
    }
}

private fun effectiveThresholdLabel(state: LargeFileUiState): String {
    val customMb = state.customThresholdMb.toLongOrNull()
    return if (customMb != null && customMb > 0) "$customMb MB" else state.threshold.label
}

@Composable
private fun ThresholdPicker(
    selected: LargeFileThreshold,
    customValue: String,
    onSelect: (LargeFileThreshold) -> Unit,
    onCustomChange: (String) -> Unit,
    onScan: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text("Minimum file size", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Find files at or above this size",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 16.dp, top = 4.dp)
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LargeFileThreshold.entries.take(3).forEach { threshold ->
                FilterChip(
                    selected = selected == threshold && customValue.isEmpty(),
                    onClick = { onSelect(threshold); onCustomChange("") },
                    label = { Text(threshold.label) }
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
            LargeFileThreshold.entries.drop(3).forEach { threshold ->
                FilterChip(
                    selected = selected == threshold && customValue.isEmpty(),
                    onClick = { onSelect(threshold); onCustomChange("") },
                    label = { Text(threshold.label) }
                )
            }
        }

        OutlinedTextField(
            value = customValue,
            onValueChange = onCustomChange,
            label = { Text("Custom size (MB)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 20.dp)
        )

        Button(onClick = onScan, modifier = Modifier.fillMaxWidth().padding(top = 24.dp)) {
            Text("Scan")
        }
    }
}
