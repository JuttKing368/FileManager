package com.filemanager.app.ui.screens.files.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.filemanager.app.data.repository.FileInfo
import com.filemanager.app.util.FormatUtils

@Composable
fun TextInputDialog(
    title: String,
    initialValue: String = "",
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf(initialValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                modifier = Modifier.padding(top = 4.dp)
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (text.isNotBlank()) onConfirm(text.trim()) },
                enabled = text.isNotBlank()
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun DeleteConfirmDialog(
    itemCount: Int,
    totalSizeBytes: Long,
    toRecycleBin: Boolean = true,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (itemCount == 1) "Delete this item?" else "Delete $itemCount items?") },
        text = {
            Text(
                if (toRecycleBin) "Total size: ${FormatUtils.formatBytes(totalSizeBytes)}. It will be moved to the Recycle Bin."
                else "Total size: ${FormatUtils.formatBytes(totalSizeBytes)}. This can't be undone."
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    if (toRecycleBin) "Move to Recycle Bin" else "Delete",
                    color = if (toRecycleBin) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun FileInfoDialog(info: FileInfo, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(info.name) },
        text = {
            Column {
                InfoRow("Path", info.path)
                InfoRow("Type", if (info.isDirectory) "Folder" else (info.mimeType ?: "Unknown"))
                InfoRow("Size", FormatUtils.formatBytes(info.sizeBytes))
                if (info.itemCount != null) InfoRow("Items", info.itemCount.toString())
                InfoRow("Modified", FormatUtils.formatDate(info.lastModified))
                InfoRow("Permissions", buildString {
                    append(if (info.canRead) "Read " else "")
                    append(if (info.canWrite) "Write" else "")
                }.ifBlank { "None" })
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
