package com.filemanager.app.ui.screens.vault.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.filemanager.app.data.repository.VaultAuthMethod
import com.filemanager.app.ui.screens.vault.components.PatternPad

@Composable
fun VaultSetupScreen(
    onSetupComplete: () -> Unit,
    viewModel: VaultSetupViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Set Up Private Vault", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 24.dp, bottom = 16.dp))

        when (state.step) {
            SetupStep.CHOOSE_METHOD -> ChooseMethodStep(
                selected = state.method,
                onSelect = viewModel::selectMethod,
                onNext = viewModel::proceedToEnterSecret
            )
            SetupStep.ENTER_SECRET -> SecretEntryStep(
                method = state.method,
                title = "Set your ${methodLabel(state.method)}",
                errorMessage = state.errorMessage,
                onSubmit = viewModel::submitFirstEntry
            )
            SetupStep.CONFIRM_SECRET -> SecretEntryStep(
                method = state.method,
                title = "Confirm your ${methodLabel(state.method)}",
                errorMessage = state.errorMessage,
                onSubmit = viewModel::submitConfirmEntry
            )
            SetupStep.SHOW_RECOVERY -> RecoveryCodeStep(
                recoveryCode = state.recoveryCode.orEmpty(),
                biometricAvailable = state.biometricAvailable,
                enableBiometric = state.enableBiometric,
                onToggleBiometric = viewModel::toggleBiometric,
                onDone = { viewModel.finishSetup(onSetupComplete) }
            )
        }
    }
}

private fun methodLabel(method: VaultAuthMethod) = when (method) {
    VaultAuthMethod.PIN -> "PIN"
    VaultAuthMethod.PASSWORD -> "password"
    VaultAuthMethod.PATTERN -> "pattern"
}

@Composable
private fun ChooseMethodStep(selected: VaultAuthMethod, onSelect: (VaultAuthMethod) -> Unit, onNext: () -> Unit) {
    Text(
        "Choose how you'll unlock your Vault",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 16.dp)
    )
    VaultAuthMethod.entries.forEach { method ->
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(selected = selected == method, onClick = { onSelect(method) })
            Text(methodLabel(method).replaceFirstChar { it.uppercase() })
        }
    }
    Button(onClick = onNext, modifier = Modifier.fillMaxWidth().padding(top = 20.dp)) {
        Text("Continue")
    }
}

@Composable
private fun SecretEntryStep(method: VaultAuthMethod, title: String, errorMessage: String?, onSubmit: (String) -> Unit) {
    Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 16.dp))

    if (errorMessage != null) {
        Text(errorMessage, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 12.dp))
    }

    if (method == VaultAuthMethod.PATTERN) {
        PatternPad(onPatternSubmitted = onSubmit)
    } else {
        var value by remember(title) { mutableStateOf("") }
        OutlinedTextField(
            value = value,
            onValueChange = { value = it },
            label = { Text(methodLabel(method).replaceFirstChar { it.uppercase() }) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = if (method == VaultAuthMethod.PIN) KeyboardType.NumberPassword else KeyboardType.Password
            ),
            modifier = Modifier.fillMaxWidth()
        )
        Button(onClick = { onSubmit(value) }, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
            Text("Continue")
        }
    }
}

@Composable
private fun RecoveryCodeStep(
    recoveryCode: String,
    biometricAvailable: Boolean,
    enableBiometric: Boolean,
    onToggleBiometric: (Boolean) -> Unit,
    onDone: () -> Unit
) {
    Text(
        "Save your recovery code",
        style = MaterialTheme.typography.titleMedium
    )
    Text(
        "If you forget your credential, this is the only way back into your Vault. It won't be shown again.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp, bottom = 16.dp)
    )
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Text(
            recoveryCode,
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(20.dp)
        )
    }

    if (biometricAvailable) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Also allow fingerprint/face unlock")
            Switch(checked = enableBiometric, onCheckedChange = onToggleBiometric)
        }
    }

    Button(onClick = onDone, modifier = Modifier.fillMaxWidth().padding(top = 24.dp)) {
        Text("I've saved my recovery code")
    }
}
