package com.filemanager.app.ui.screens.files

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.filemanager.app.data.model.FileEntry
import com.filemanager.app.data.model.ViewMode
import com.filemanager.app.ui.screens.files.components.DeleteConfirmDialog
import com.filemanager.app.ui.screens.files.components.FileGridItem
import com.filemanager.app.ui.screens.files.components.FileInfoDialog
import com.filemanager.app.ui.screens.files.components.FileListRow
import com.filemanager.app.ui.screens.files.components.SortMenu
import com.filemanager.app.ui.screens.files.components.TextInputDialog
import com.filemanager.app.util.ShareUtils
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilesScreen(
    navController: NavHostController,
    viewModel: FilesViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    var sortMenuExpanded by remember { mutableStateOf(false) }
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<FileEntry?>(null) }
    var infoTarget by remember { mutableStateOf<FileEntry?>(null) }
    var infoResult by remember { mutableStateOf<com.filemanager.app.data.repository.FileInfo?>(null) }

    // Hardware/gesture back: exit multi-select first, then search, then go up a folder.
    BackHandler(enabled = true) {
        when {
            state.isSelectionMode -> viewModel.clearSelection()
            state.isSearchActive -> viewModel.setSearchActive(false)
            else -> if (!viewModel.navigateUp()) { /* at root — let the tab handle it */ }
        }
    }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.setError(null)
        }
    }

    LaunchedEffect(state.recentlyRecycled) {
        val recycled = state.recentlyRecycled ?: return@LaunchedEffect
        val label = if (recycled.size == 1) "1 item moved to Recycle Bin" else "${recycled.size} items moved to Recycle Bin"
        val result = snackbarHostState.showSnackbar(label, actionLabel = "Undo")
        if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
            viewModel.undoRecycle()
        } else {
            viewModel.dismissRecycledNotice()
        }
    }

    infoTarget?.let { target ->
        LaunchedEffect(target) {
            infoResult = viewModel.getFileInfo(target)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        if (state.isSearchActive) {
                            OutlinedTextField(
                                value = state.searchQuery,
                                onValueChange = viewModel::onSearchQueryChange,
                                singleLine = true,
                                placeholder = { Text("Search in this folder") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else if (state.isSelectionMode) {
                            Text("${state.selectedPaths.size} selected")
                        } else {
                            Text(
                                state.currentDir.name.ifEmpty { "Storage" },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    },
                    navigationIcon = {
                        if (state.isSelectionMode) {
                            IconButton(onClick = viewModel::clearSelection) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Clear selection")
                            }
                        } else if (state.isSearchActive) {
                            IconButton(onClick = { viewModel.setSearchActive(false) }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close search")
                            }
                        }
                    },
                    actions = {
                        if (state.isSelectionMode) {
                            IconButton(onClick = viewModel::selectAll) {
                                Icon(Icons.Filled.SelectAll, contentDescription = "Select all")
                            }
                        } else if (!state.isSearchActive) {
                            IconButton(onClick = { viewModel.setSearchActive(true) }) {
                                Icon(Icons.Filled.Search, contentDescription = "Search")
                            }
                            IconButton(onClick = {
                                viewModel.setViewMode(if (state.viewMode == ViewMode.LIST) ViewMode.GRID else ViewMode.LIST)
                            }) {
                                Icon(
                                    if (state.viewMode == ViewMode.LIST) Icons.Filled.GridView else Icons.Filled.ViewList,
                                    contentDescription = "Toggle view"
                                )
                            }
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
                        }
                    }
                )

                if (state.clipboard !is com.filemanager.app.ui.screens.files.ClipboardOp.None && !state.isSelectionMode) {
                    PasteBar(
                        isMove = state.clipboard is com.filemanager.app.ui.screens.files.ClipboardOp.Move,
                        onPaste = viewModel::pasteIntoCurrentDirectory,
                        onCancel = viewModel::clearClipboard
                    )
                }
            }
        },
        floatingActionButton = {
            if (!state.isSelectionMode) {
                FloatingActionButton(onClick = { showNewFolderDialog = true }) {
                    Icon(Icons.Filled.CreateNewFolder, contentDescription = "New folder")
                }
            }
        },
        bottomBar = {
            if (state.isSelectionMode) {
                SelectionActionBar(
                    onCopy = viewModel::copySelectedToClipboard,
                    onMove = viewModel::moveSelectedToClipboard,
                    onDelete = viewModel::requestDeleteSelected,
                    onShare = {
                        val files = state.selectedPaths.map { File(it) }
                        if (files.size == 1) {
                            context.startActivity(
                                android.content.Intent.createChooser(ShareUtils.shareIntentFor(context, files.first()), null)
                            )
                        }
                    },
                    onRename = {
                        state.selectedPaths.singleOrNull()?.let { path ->
                            state.entries.find { it.path == path }?.let { renameTarget = it }
                        }
                    },
                    onInfo = {
                        state.selectedPaths.singleOrNull()?.let { path ->
                            state.entries.find { it.path == path }?.let { infoTarget = it }
                        }
                    },
                    singleSelected = state.selectedPaths.size == 1
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (state.displayedEntries.isEmpty() && !state.isLoading) {
                Text(
                    text = if (state.isSearchActive) "No matches" else "This folder is empty",
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else if (state.viewMode == ViewMode.LIST) {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(state.displayedEntries, key = { it.path }) { entry ->
                        FileListRow(
                            modifier = Modifier.animateItem(),
                            entry = entry,
                            isSelected = state.selectedPaths.contains(entry.path),
                            isSelectionMode = state.isSelectionMode,
                            onClick = {
                                if (state.isSelectionMode) {
                                    viewModel.toggleSelection(entry)
                                } else if (entry.isDirectory) {
                                    viewModel.navigateInto(entry)
                                } else {
                                    runCatching {
                                        context.startActivity(ShareUtils.openIntentFor(context, File(entry.path)))
                                    }
                                }
                            },
                            onLongClick = { viewModel.toggleSelection(entry) }
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(state.displayedEntries, key = { it.path }) { entry ->
                        FileGridItem(
                            modifier = Modifier.animateItem(),
                            entry = entry,
                            isSelected = state.selectedPaths.contains(entry.path),
                            isSelectionMode = state.isSelectionMode,
                            onClick = {
                                if (state.isSelectionMode) {
                                    viewModel.toggleSelection(entry)
                                } else if (entry.isDirectory) {
                                    viewModel.navigateInto(entry)
                                } else {
                                    runCatching {
                                        context.startActivity(ShareUtils.openIntentFor(context, File(entry.path)))
                                    }
                                }
                            },
                            onLongClick = { viewModel.toggleSelection(entry) }
                        )
                    }
                }
            }
        }
    }

    if (showNewFolderDialog) {
        TextInputDialog(
            title = "New folder",
            confirmLabel = "Create",
            onDismiss = { showNewFolderDialog = false },
            onConfirm = { name ->
                viewModel.createFolder(name)
                showNewFolderDialog = false
            }
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

    state.pendingDeleteTargets?.let { targets ->
        DeleteConfirmDialog(
            itemCount = targets.size,
            totalSizeBytes = targets.sumOf { if (it.isDirectory) 0L else it.length() },
            onDismiss = viewModel::cancelDelete,
            onConfirm = viewModel::confirmDelete
        )
    }

    infoResult?.let { info ->
        FileInfoDialog(info = info, onDismiss = { infoTarget = null; infoResult = null })
    }
}

@Composable
private fun PasteBar(isMove: Boolean, onPaste: () -> Unit, onCancel: () -> Unit) {
    androidx.compose.material3.Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            androidx.compose.foundation.layout.Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(if (isMove) "Move here?" else "Copy here?", style = MaterialTheme.typography.bodyMedium)
                androidx.compose.foundation.layout.Row {
                    androidx.compose.material3.TextButton(onClick = onCancel) { Text("Cancel") }
                    androidx.compose.material3.TextButton(onClick = onPaste) { Text("Paste") }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionActionBar(
    onCopy: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit,
    onRename: () -> Unit,
    onInfo: () -> Unit,
    singleSelected: Boolean
) {
    BottomAppBar {
        if (singleSelected) {
            IconButton(onClick = onRename) {
                Icon(Icons.Filled.DriveFileRenameOutline, contentDescription = "Rename")
            }
        }
        IconButton(onClick = onCopy) {
            Icon(Icons.Filled.ContentCopy, contentDescription = "Copy")
        }
        IconButton(onClick = onMove) {
            Icon(Icons.Filled.ContentCut, contentDescription = "Move")
        }
        if (singleSelected) {
            IconButton(onClick = onShare) {
                Icon(Icons.Filled.Share, contentDescription = "Share")
            }
            IconButton(onClick = onInfo) {
                Icon(Icons.Filled.Info, contentDescription = "Info")
            }
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
        }
    }
}
