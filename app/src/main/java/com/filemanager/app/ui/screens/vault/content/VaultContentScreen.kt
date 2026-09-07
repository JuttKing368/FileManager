package com.filemanager.app.ui.screens.vault.content

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import com.filemanager.app.data.local.VaultItemEntity
import com.filemanager.app.data.model.FileCategoryClassifier
import com.filemanager.app.data.repository.VaultSessionManager
import com.filemanager.app.ui.screens.files.components.iconFor
import com.filemanager.app.ui.screens.vault.components.VaultFilePickerDialog
import com.filemanager.app.util.FormatUtils
import com.filemanager.app.util.ShareUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultContentScreen(viewModel: VaultContentViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showFilePicker by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

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
                title = { Text("Private Vault") },
                actions = {
                    IconButton(onClick = { VaultSessionManager.lock() }) {
                        Icon(Icons.Filled.Lock, contentDescription = "Lock now")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showFilePicker = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Add to Vault")
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when {
                state.isAdding -> Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator()
                    Text("Encrypting...", modifier = Modifier.padding(top = 12.dp))
                }
                state.items.isEmpty() -> Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Filled.LockOpen, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(48.dp))
                    Text("Your Vault is empty", modifier = Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Tap + to add files", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(state.items, key = { it.id }) { entity ->
                        VaultItemRow(
                            modifier = Modifier.animateItem(),
                            entity = entity,
                            onView = {
                                viewModel.requestView(entity) { file ->
                                    runCatching { context.startActivity(ShareUtils.openIntentFor(context, file)) }
                                }
                            },
                            onShare = {
                                viewModel.requestView(entity) { file ->
                                    context.startActivity(android.content.Intent.createChooser(ShareUtils.shareIntentFor(context, file), null))
                                }
                            },
                            onRemove = { viewModel.requestRemove(entity) }
                        )
                    }
                }
            }
        }
    }

    if (showFilePicker) {
        VaultFilePickerDialog(
            onDismiss = { showFilePicker = false },
            onFilesSelected = { files ->
                viewModel.addFiles(files)
                showFilePicker = false
            }
        )
    }

    state.pendingRemove?.let { entity ->
        AlertDialog(
            onDismissRequest = viewModel::cancelRemove,
            title = { Text("Remove from Vault?") },
            text = { Text("\"${entity.displayName}\" will be decrypted and restored to its original location.") },
            confirmButton = { TextButton(onClick = viewModel::confirmRemove) { Text("Remove") } },
            dismissButton = { TextButton(onClick = viewModel::cancelRemove) { Text("Cancel") } }
        )
    }
}

@Composable
private fun VaultItemRow(entity: VaultItemEntity, onView: () -> Unit, onShare: () -> Unit, onRemove: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val category = FileCategoryClassifier.classify(entity.displayName, false)
        Icon(
            iconFor(category),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .padding(end = 12.dp)
        )
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp).clickable(onClick = onView)) {
            Text(
                entity.displayName,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "${FormatUtils.formatBytes(entity.sizeBytes)} · Added ${FormatUtils.formatDate(entity.addedAtEpochMillis)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onShare) { Icon(Icons.Filled.Share, contentDescription = "Share") }
        IconButton(onClick = onRemove) { Icon(Icons.Filled.Delete, contentDescription = "Remove from Vault", tint = MaterialTheme.colorScheme.error) }
    }
}
