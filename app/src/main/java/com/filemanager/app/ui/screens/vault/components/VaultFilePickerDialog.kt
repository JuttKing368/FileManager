package com.filemanager.app.ui.screens.vault.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import com.filemanager.app.ui.screens.files.components.iconFor
import java.io.File

/** Browse-and-multi-select-files dialog used to add files to the Vault. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultFilePickerDialog(
    onDismiss: () -> Unit,
    onFilesSelected: (List<File>) -> Unit,
    viewModel: VaultFilePickerViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BackHandler { if (!viewModel.goUp()) onDismiss() }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(state.currentDir.name.ifEmpty { "Storage" }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = {
                        IconButton(onClick = { if (!viewModel.goUp()) onDismiss() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
            },
            bottomBar = {
                if (state.selectedPaths.isNotEmpty()) {
                    Button(
                        onClick = { onFilesSelected(viewModel.selectedFiles()) },
                        modifier = Modifier.fillMaxWidth().padding(16.dp)
                    ) { Text("Add ${state.selectedPaths.size} to Vault") }
                }
            }
        ) { padding ->
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                items(state.entries, key = { it.path }) { entry ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (entry.isDirectory) viewModel.openFolder(File(entry.path))
                                else viewModel.toggleFile(entry.path)
                            }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (!entry.isDirectory) {
                            Checkbox(
                                checked = state.selectedPaths.contains(entry.path),
                                onCheckedChange = { viewModel.toggleFile(entry.path) }
                            )
                        }
                        Icon(iconFor(entry.category), contentDescription = null, modifier = Modifier.padding(horizontal = 8.dp))
                        Text(entry.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}
