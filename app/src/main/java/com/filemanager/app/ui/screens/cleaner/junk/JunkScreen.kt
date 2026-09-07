package com.filemanager.app.ui.screens.cleaner.junk

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
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.filemanager.app.data.scanner.JunkCategory
import com.filemanager.app.data.scanner.JunkItem
import com.filemanager.app.ui.screens.files.components.DeleteConfirmDialog
import com.filemanager.app.util.FormatUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JunkScreen(
    navController: NavHostController,
    viewModel: JunkViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        if (state.phase == JunkScanPhase.IDLE) viewModel.startScan()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Junk Cleaner") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (state.phase == JunkScanPhase.SCANNING) {
                        TextButton(onClick = viewModel::cancelScan) { Text("Cancel") }
                    } else if (state.phase == JunkScanPhase.COMPLETE && state.items.isNotEmpty()) {
                        TextButton(onClick = {
                            if (state.selectedPaths.size == state.items.size) viewModel.deselectAll() else viewModel.selectAll()
                        }) {
                            Text(if (state.selectedPaths.size == state.items.size) "Deselect All" else "Select All")
                        }
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
                    Text("You are about to remove ${FormatUtils.formatBytes(state.selectedTotalBytes)}")
                    Button(onClick = viewModel::requestDelete) { Text("Clean") }
                }
            }
        }
    ) { padding ->
        Crossfade(targetState = state.phase, modifier = Modifier.padding(padding).fillMaxSize(), label = "scanPhase") { phase ->
            when (phase) {
                JunkScanPhase.IDLE -> {}
                JunkScanPhase.SCANNING -> Column(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator()
                    Text("Scanning for junk...", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 20.dp))
                    Text("Files scanned: ${state.filesScanned}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        state.currentPath,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                JunkScanPhase.COMPLETE -> {
                    if (state.items.isEmpty()) {
                        Column(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
                            Text("Nothing to clean", modifier = Modifier.padding(top = 8.dp))
                        }
                    } else {
                        Column(modifier = Modifier.fillMaxSize()) {
                            Text(
                                "${FormatUtils.formatBytes(state.totalBytes)} removable across ${state.items.size} items",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(16.dp)
                            )
                            LazyColumn(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(state.groupedByCategory.entries.toList(), key = { it.key }) { (category, categoryItems) ->
                                    JunkCategoryCard(
                                        modifier = Modifier.animateItem(),
                                        category = category,
                                        items = categoryItems,
                                        selectedPaths = state.selectedPaths,
                                        onToggleItem = viewModel::toggleItem,
                                        onToggleCategory = { viewModel.toggleCategory(category) }
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
private fun JunkCategoryCard(
    modifier: Modifier = Modifier,
    category: JunkCategory,
    items: List<JunkItem>,
    selectedPaths: Set<String>,
    onToggleItem: (String) -> Unit,
    onToggleCategory: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val categorySize = items.sumOf { it.sizeBytes }
    val allSelected = items.all { it.file.absolutePath in selectedPaths }

    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = allSelected, onCheckedChange = { onToggleCategory() })
                    Column {
                        Text(category.displayName, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${items.size} items · ${FormatUtils.formatBytes(categorySize)}",
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
                Column(modifier = Modifier.padding(top = 4.dp)) {
                    items.forEach { item ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = item.file.absolutePath in selectedPaths,
                                onCheckedChange = { onToggleItem(item.file.absolutePath) }
                            )
                            Column {
                                Text(item.file.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    item.file.absolutePath,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    "${FormatUtils.formatBytes(item.sizeBytes)} · ${item.reason}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
