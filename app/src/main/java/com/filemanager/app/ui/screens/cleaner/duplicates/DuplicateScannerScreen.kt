package com.filemanager.app.ui.screens.cleaner.duplicates

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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.filemanager.app.data.scanner.DuplicateGroup
import com.filemanager.app.ui.screens.files.components.DeleteConfirmDialog
import com.filemanager.app.util.FormatUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DuplicateScannerScreen(
    navController: NavHostController,
    viewModel: DuplicateScannerViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        if (state.phase == ScanPhase.IDLE) viewModel.startScan()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Duplicate Files") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (state.phase == ScanPhase.SCANNING || state.phase == ScanPhase.HASHING) {
                        TextButton(onClick = viewModel::cancelScan) { Text("Cancel") }
                    } else if (state.phase == ScanPhase.COMPLETE && state.groups.isNotEmpty()) {
                        TextButton(onClick = viewModel::selectAllDuplicatesKeepingOneEach) { Text("Select All") }
                    }
                }
            )
        },
        bottomBar = {
            if (state.selectedPaths.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("${state.selectedPaths.size} selected · ${FormatUtils.formatBytes(state.selectedTotalBytes)}")
                    Button(onClick = viewModel::requestDeleteSelected) {
                        Text("Delete Selected")
                    }
                }
            }
        }
    ) { padding ->
        Crossfade(targetState = state.phase, modifier = Modifier.padding(padding).fillMaxSize(), label = "scanPhase") { phase ->
            when (phase) {
                ScanPhase.IDLE -> {}
                ScanPhase.SCANNING -> ScanningState(
                    label = "Scanning...",
                    detail = "Files scanned: ${state.filesScanned}",
                    currentPath = state.currentPath
                )
                ScanPhase.HASHING -> ScanningState(
                    label = "Comparing candidates...",
                    detail = "${state.hashProgress.first} / ${state.hashProgress.second}",
                    currentPath = null,
                    determinateProgress = if (state.hashProgress.second > 0) {
                        state.hashProgress.first.toFloat() / state.hashProgress.second.toFloat()
                    } else null
                )
                ScanPhase.COMPLETE -> {
                    if (state.groups.isEmpty()) {
                        Column(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
                            Text("No duplicates found", modifier = Modifier.padding(top = 8.dp))
                        }
                    } else {
                        Column(modifier = Modifier.fillMaxSize()) {
                            Text(
                                text = "${state.groups.size} groups found · ${FormatUtils.formatBytes(state.totalRecoverableBytes)} recoverable",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(16.dp)
                            )
                            LazyColumn(
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(state.groups, key = { it.hash }) { group ->
                                    DuplicateGroupCard(
                                        modifier = Modifier.animateItem(),
                                        group = group,
                                        selectedPaths = state.selectedPaths,
                                        onToggleFile = viewModel::toggleFileSelection,
                                        onSelectAllButOldest = { viewModel.selectAllExceptOldestInGroup(group) }
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
            totalSizeBytes = state.selectedTotalBytes,
            onDismiss = viewModel::cancelDelete,
            onConfirm = viewModel::confirmDelete
        )
    }
}

@Composable
private fun ScanningState(
    label: String,
    detail: String,
    currentPath: String?,
    determinateProgress: Float? = null
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (determinateProgress != null) {
            LinearProgressIndicator(progress = { determinateProgress }, modifier = Modifier.fillMaxWidth())
        } else {
            CircularProgressIndicator()
        }
        Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 20.dp))
        Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (currentPath != null) {
            Text(
                currentPath,
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
private fun DuplicateGroupCard(
    modifier: Modifier = Modifier,
    group: DuplicateGroup,
    selectedPaths: Set<String>,
    onToggleFile: (String) -> Unit,
    onSelectAllButOldest: () -> Unit
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
                    Icon(Icons.Filled.ContentCopy, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
                    Column(modifier = Modifier.padding(start = 10.dp)) {
                        Text(group.oldestFile.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${FormatUtils.formatBytes(group.sizeBytes)} · ${group.files.size} copies",
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
                    group.files.forEach { file ->
                        val isOldest = file == group.oldestFile
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = selectedPaths.contains(file.absolutePath),
                                onCheckedChange = { onToggleFile(file.absolutePath) }
                            )
                            Column {
                                Text(
                                    file.absolutePath,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (isOldest) {
                                    Text(
                                        "Likely original (oldest)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                    OutlinedButton(
                        onClick = onSelectAllButOldest,
                        modifier = Modifier.padding(top = 6.dp)
                    ) {
                        Text("Select all but oldest")
                    }
                }
            }
        }
    }
}
