package com.filemanager.app.ui.screens.cleaner.duplicatefolders

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.filemanager.app.data.scanner.DuplicateFolderGroup
import com.filemanager.app.ui.screens.files.components.DeleteConfirmDialog
import com.filemanager.app.util.FormatUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DuplicateFolderScreen(
    navController: NavHostController,
    viewModel: DuplicateFolderViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        if (state.phase == FolderScanPhase.IDLE) viewModel.startScan()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Duplicate Folders") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (state.phase == FolderScanPhase.SCANNING || state.phase == FolderScanPhase.COMPARING) {
                        TextButton(onClick = viewModel::cancelScan) { Text("Cancel") }
                    } else if (state.phase == FolderScanPhase.COMPLETE && state.groups.isNotEmpty()) {
                        TextButton(onClick = viewModel::selectAllExceptOneEach) { Text("Select All") }
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
                    Text("${state.selectedPaths.size} folders · ${FormatUtils.formatBytes(state.selectedTotalBytes)}")
                    Button(onClick = viewModel::requestDeleteSelected) { Text("Delete Selected") }
                }
            }
        }
    ) { padding ->
        Crossfade(targetState = state.phase, modifier = Modifier.padding(padding).fillMaxSize(), label = "scanPhase") { phase ->
            when (phase) {
                FolderScanPhase.IDLE -> {}
                FolderScanPhase.SCANNING -> ScanningBlock(
                    label = "Scanning folders...",
                    detail = "Folders scanned: ${state.foldersScanned}",
                    path = state.currentPath
                )
                FolderScanPhase.COMPARING -> ScanningBlock(
                    label = "Comparing folder contents...",
                    detail = "${state.compareProgress.first} / ${state.compareProgress.second}",
                    path = null,
                    progress = if (state.compareProgress.second > 0) {
                        state.compareProgress.first.toFloat() / state.compareProgress.second.toFloat()
                    } else null
                )
                FolderScanPhase.COMPLETE -> {
                    if (state.groups.isEmpty()) {
                        Column(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
                            Text("No duplicate folders found", modifier = Modifier.padding(top = 8.dp))
                        }
                    } else {
                        Column(modifier = Modifier.fillMaxSize()) {
                            Text(
                                "${state.groups.size} duplicate folder groups found",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(16.dp)
                            )
                            LazyColumn(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(state.groups, key = { it.signature }) { group ->
                                    DuplicateFolderCard(
                                        modifier = Modifier.animateItem(),
                                        group = group,
                                        selectedPaths = state.selectedPaths,
                                        onToggle = viewModel::toggleFolderSelection
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
}

@Composable
private fun ScanningBlock(label: String, detail: String, path: String?, progress: Float? = null) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (progress != null) {
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
        } else {
            CircularProgressIndicator()
        }
        Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 20.dp))
        Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (path != null) {
            Text(
                path,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
private fun DuplicateFolderCard(
    modifier: Modifier = Modifier,
    group: DuplicateFolderGroup,
    selectedPaths: Set<String>,
    onToggle: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
                    Column(modifier = Modifier.padding(start = 10.dp)) {
                        Text(
                            "${group.folders.size} identical folders",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            "${group.folders.first().fileCount} files · ${FormatUtils.formatBytes(group.folders.first().totalSizeBytes)} each",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = "Expand")
                }
            }

            if (expanded) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    group.folders.forEach { snapshot ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = selectedPaths.contains(snapshot.file.absolutePath),
                                onCheckedChange = { onToggle(snapshot.file.absolutePath) }
                            )
                            Column {
                                Text(
                                    snapshot.file.absolutePath,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    "${snapshot.fileCount} files · ${FormatUtils.formatBytes(snapshot.totalSizeBytes)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
