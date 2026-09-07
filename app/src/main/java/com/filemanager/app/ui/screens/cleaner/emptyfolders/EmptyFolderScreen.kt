package com.filemanager.app.ui.screens.cleaner.emptyfolders

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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.filemanager.app.data.scanner.EmptyFolderEntry
import com.filemanager.app.ui.screens.files.components.DeleteConfirmDialog
import com.filemanager.app.util.FormatUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmptyFolderScreen(
    navController: NavHostController,
    viewModel: EmptyFolderViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        if (state.phase == EmptyFolderScanPhase.IDLE) viewModel.startScan()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Empty Folders") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (state.phase == EmptyFolderScanPhase.SCANNING) {
                        TextButton(onClick = viewModel::cancelScan) { Text("Cancel") }
                    } else if (state.phase == EmptyFolderScanPhase.COMPLETE && state.results.isNotEmpty()) {
                        TextButton(onClick = viewModel::selectAll) { Text("Select All") }
                    }
                }
            )
        },
        bottomBar = {
            if (state.selectedPaths.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("${state.selectedPaths.size} selected")
                    Button(onClick = viewModel::requestDeleteSelected) { Text("Delete Selected") }
                }
            }
        }
    ) { padding ->
        Crossfade(targetState = state.phase, modifier = Modifier.padding(padding).fillMaxSize(), label = "scanPhase") { phase ->
            when (phase) {
                EmptyFolderScanPhase.IDLE -> {}
                EmptyFolderScanPhase.SCANNING -> Column(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator()
                    Text("Scanning...", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 20.dp))
                    Text(
                        "Folders scanned: ${state.foldersScanned}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        state.currentPath,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                EmptyFolderScanPhase.COMPLETE -> {
                    if (state.results.isEmpty()) {
                        Column(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
                            Text("No empty folders found", modifier = Modifier.padding(top = 8.dp))
                        }
                    } else {
                        Column(modifier = Modifier.fillMaxSize()) {
                            Text(
                                "${state.results.size} empty folders",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(16.dp)
                            )
                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                items(state.results, key = { it.file.absolutePath }) { entry ->
                                    EmptyFolderRow(
                                        modifier = Modifier.animateItem(),
                                        entry = entry,
                                        isSelected = state.selectedPaths.contains(entry.file.absolutePath),
                                        onToggle = { viewModel.toggleSelection(entry.file.absolutePath) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    state.pendingDeleteCount?.let { count ->
        DeleteConfirmDialog(
            itemCount = count,
            totalSizeBytes = 0L, // empty folders have no content size by definition
            onDismiss = viewModel::cancelDelete,
            onConfirm = viewModel::confirmDelete
        )
    }
}

@Composable
private fun EmptyFolderRow(entry: EmptyFolderEntry, isSelected: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = isSelected, onCheckedChange = { onToggle() })
        Icon(
            Icons.Filled.FolderOff,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
        Column {
            Text(entry.file.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                entry.file.absolutePath,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "Modified ${FormatUtils.formatDate(entry.lastModified)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
