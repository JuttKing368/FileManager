package com.filemanager.app.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.filemanager.app.BuildConfig
import com.filemanager.app.data.model.ViewMode
import com.filemanager.app.data.repository.VaultAuthMethod
import com.filemanager.app.ui.theme.AppThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    var showChangeCredentialDialog by remember { mutableStateOf(false) }
    var showRecoveryDialog by remember { mutableStateOf(false) }
    var recoveryCodeResult by remember { mutableStateOf<String?>(null) }

    Scaffold(topBar = { TopAppBar(title = { Text("Settings") }) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxWidth().padding(padding),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            item { SectionHeader("Appearance") }
            item {
                SettingRow(label = "Theme") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppThemeMode.entries.forEach { mode ->
                            FilterChip(
                                selected = state.themeMode == mode,
                                onClick = { viewModel.setThemeMode(mode) },
                                label = { Text(mode.name.lowercase().replaceFirstChar { it.uppercase() }) }
                            )
                        }
                    }
                }
            }

            item { SectionHeader("File Manager") }
            item {
                SettingRow(label = "Default view") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ViewMode.entries.forEach { mode ->
                            FilterChip(
                                selected = state.defaultViewMode == mode,
                                onClick = { viewModel.setDefaultViewMode(mode) },
                                label = { Text(mode.name.lowercase().replaceFirstChar { it.uppercase() }) }
                            )
                        }
                    }
                }
            }
            item {
                SwitchRow(
                    label = "Show hidden files",
                    checked = state.showHiddenFiles,
                    onCheckedChange = viewModel::setShowHiddenFiles
                )
            }

            item { SectionHeader("Cleaner") }
            item {
                SwitchRow(
                    label = "Confirm before deleting",
                    subtitle = "Deleted items always go to the Recycle Bin either way",
                    checked = state.confirmBeforeDelete,
                    onCheckedChange = viewModel::setConfirmBeforeDelete
                )
            }

            item { SectionHeader("Vault") }
            if (state.isVaultSetUp) {
                item {
                    SettingRow(label = "Unlock method") {
                        Text(
                            state.vaultAuthMethod?.name?.lowercase()?.replaceFirstChar { it.uppercase() } ?: "-",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                item {
                    ClickableRow(label = "Change PIN / password / pattern", onClick = { showChangeCredentialDialog = true })
                }
                item {
                    SwitchRow(
                        label = "Biometric unlock",
                        checked = state.vaultBiometricEnabled,
                        onCheckedChange = viewModel::setVaultBiometricEnabled
                    )
                }
                item {
                    SettingRow(label = "Auto-lock timeout") {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(0 to "Immediately", 30 to "30s", 60 to "1 min", 300 to "5 min").forEach { (seconds, label) ->
                                FilterChip(
                                    selected = state.vaultAutoLockTimeoutSeconds == seconds,
                                    onClick = { viewModel.setVaultAutoLockTimeout(seconds) },
                                    label = { Text(label) }
                                )
                            }
                        }
                    }
                }
                item {
                    ClickableRow(label = "Generate new recovery code", onClick = { showRecoveryDialog = true })
                }
            } else {
                item {
                    Text(
                        "Set up the Vault from the Vault tab first",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }

            item { SectionHeader("About") }
            item {
                SettingRow(label = "Version") {
                    Text(BuildConfig.VERSION_NAME, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item {
                Text(
                    "All scanning, cleaning, and vault encryption happen entirely on your device. Nothing is ever uploaded anywhere.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }

    if (showChangeCredentialDialog) {
        ChangeCredentialDialog(
            currentMethod = state.vaultAuthMethod ?: VaultAuthMethod.PIN,
            onDismiss = { showChangeCredentialDialog = false },
            onSubmit = { current, newMethod, newSecret, onError ->
                viewModel.changeVaultCredential(current, newMethod, newSecret) { success ->
                    if (success) showChangeCredentialDialog = false else onError()
                }
            }
        )
    }

    if (showRecoveryDialog) {
        RegenerateRecoveryDialog(
            resultCode = recoveryCodeResult,
            onDismiss = { showRecoveryDialog = false; recoveryCodeResult = null },
            onSubmit = { current, onError ->
                viewModel.regenerateRecoveryCode(current) { code ->
                    if (code != null) recoveryCodeResult = code else onError()
                }
            }
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
    )
}

@Composable
private fun SettingRow(label: String, content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(bottom = 6.dp))
        content()
    }
}

@Composable
private fun SwitchRow(label: String, subtitle: String? = null, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ClickableRow(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(onClick = onClick) { Text(label) }
    }
}

@Composable
private fun ChangeCredentialDialog(
    currentMethod: VaultAuthMethod,
    onDismiss: () -> Unit,
    onSubmit: (current: String, newMethod: VaultAuthMethod, newSecret: String, onError: () -> Unit) -> Unit
) {
    var current by remember { mutableStateOf("") }
    var newSecret by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Change Vault credential") },
        text = {
            Column {
                if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 8.dp))
                OutlinedTextField(
                    value = current,
                    onValueChange = { current = it },
                    label = { Text("Current ${currentMethod.name.lowercase()}") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                )
                OutlinedTextField(
                    value = newSecret,
                    onValueChange = { newSecret = it },
                    label = { Text("New ${currentMethod.name.lowercase()}") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSubmit(current, currentMethod, newSecret) { error = "Current credential is incorrect" }
            }) { Text("Change") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun RegenerateRecoveryDialog(
    resultCode: String?,
    onDismiss: () -> Unit,
    onSubmit: (current: String, onError: () -> Unit) -> Unit
) {
    var current by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Generate new recovery code") },
        text = {
            if (resultCode != null) {
                Column {
                    Text("Save this now — it won't be shown again:", modifier = Modifier.padding(bottom = 8.dp))
                    Text(resultCode, style = MaterialTheme.typography.titleMedium)
                }
            } else {
                Column {
                    Text("Your old recovery code will stop working.", modifier = Modifier.padding(bottom = 8.dp))
                    if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 8.dp))
                    OutlinedTextField(
                        value = current,
                        onValueChange = { current = it },
                        label = { Text("Confirm current credential") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            if (resultCode == null) {
                TextButton(onClick = { onSubmit(current) { error = "Current credential is incorrect" } }) { Text("Generate") }
            } else {
                TextButton(onClick = onDismiss) { Text("Done") }
            }
        },
        dismissButton = {
            if (resultCode == null) TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
