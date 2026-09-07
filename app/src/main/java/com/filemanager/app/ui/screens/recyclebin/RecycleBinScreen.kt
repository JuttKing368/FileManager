package com.filemanager.app.ui.screens.recyclebin

import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.filemanager.app.data.local.RecycleBinEntity
import com.filemanager.app.data.model.FileCategory
import com.filemanager.app.data.model.FileCategoryClassifier
import com.filemanager.app.ui.screens.files.components.iconFor
import com.filemanager.app.util.FormatUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecycleBinScreen(
    navController: NavHostController,
    viewModel: RecycleBinViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.isSelectionMode) "${state.selectedIds.size} selected" else "Recycle Bin") },
                navigationIcon = {
                    IconButton(onClick = {
                        if (state.isSelectionMode) viewModel.clearSelection() else navController.popBackStack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (!state.isSelectionMode && state.items.isNotEmpty()) {
                        TextButton(onClick = viewModel::requestEmptyBin) { Text("Empty Bin") }
                    }
                }
            )
        },
        bottomBar = {
            if (state.isSelectionMode) {
                BottomAppBar {
                    IconButton(onClick = viewModel::restoreSelected) {
                        Icon(Icons.Filled.Restore, contentDescription = "Restore")
                    }
                    IconButton(onClick = viewModel::requestPermanentDeleteSelected) {
                        Icon(Icons.Filled.DeleteForever, contentDescription = "Delete permanently", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (state.items.isEmpty()) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Filled.RestoreFromTrash, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(48.dp))
                    Text("Recycle Bin is empty", modifier = Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    Text(
                        "${state.items.size} items - ${FormatUtils.formatBytes(state.totalSizeBytes)}",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(16.dp)
                    )
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(state.items, key = { it.id }) { entity ->
                            RecycleBinRow(
                                modifier = Modifier.animateItem(),
                                entity = entity,
                                isSelected = state.selectedIds.contains(entity.id),
                                isSelectionMode = state.isSelectionMode,
                                onClick = { if (state.isSelectionMode) viewModel.toggleSelection(entity.id) },
                                onLongClick = { viewModel.toggleSelection(entity.id) },
                                onRestore = { viewModel.restore(entity) },
                                onDeletePermanently = { viewModel.requestPermanentDelete(entity) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (state.pendingEmptyBin) {
        AlertDialog(
            onDismissRequest = viewModel::cancelEmptyBin,
            title = { Text("Empty Recycle Bin?") },
            text = { Text("${state.items.size} items (${FormatUtils.formatBytes(state.totalSizeBytes)}) will be permanently deleted. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = viewModel::confirmEmptyBin) {
                    Text("Empty Bin", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = viewModel::cancelEmptyBin) { Text("Cancel") } }
        )
    }

    state.pendingPermanentDelete?.let { targets ->
        AlertDialog(
            onDismissRequest = viewModel::cancelPermanentDelete,
            title = { Text(if (targets.size == 1) "Delete permanently?" else "Delete ${targets.size} items permanently?") },
            text = { Text("Total size: ${FormatUtils.formatBytes(targets.sumOf { it.sizeBytes })}. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = viewModel::confirmPermanentDelete) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = viewModel::cancelPermanentDelete) { Text("Cancel") } }
        )
    }
}

@Composable
private fun RecycleBinRow(
    modifier: Modifier = Modifier,
    entity: RecycleBinEntity,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onRestore: () -> Unit,
    onDeletePermanently: () -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isSelectionMode) {
            Checkbox(checked = isSelected, onCheckedChange = { onClick() })
        }
        val category = if (entity.isDirectory) FileCategory.FOLDER
        else FileCategoryClassifier.classify(entity.fileName, false)
        Icon(iconFor(category), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)

        Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
            Text(entity.fileName, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "From: ${entity.originalPath}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "${FormatUtils.formatBytes(entity.sizeBytes)} - Deleted ${FormatUtils.formatDate(entity.deletedAtEpochMillis)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (!isSelectionMode) {
            IconButton(onClick = onRestore) {
                Icon(Icons.Filled.Restore, contentDescription = "Restore")
            }
            IconButton(onClick = onDeletePermanently) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete permanently", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}
