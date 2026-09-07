package com.filemanager.app.ui.screens.vault.lock

import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import com.filemanager.app.data.repository.VaultAuthMethod
import com.filemanager.app.ui.screens.vault.components.PatternPad

@Composable
fun VaultLockScreen(
    onUnlocked: () -> Unit,
    viewModel: VaultLockViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var secretInput by remember { mutableStateOf("") }
    var biometricError by remember { mutableStateOf<String?>(null) }

    fun tryBiometric() {
        val activity = context as? FragmentActivity ?: return
        val executor = ContextCompat.getMainExecutor(context)
        val prompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    biometricError = null
                    onUnlocked()
                }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    // User-cancelled/negative-button dismissals aren't errors worth surfacing —
                    // just leave the PIN/password/pattern field available. Anything else
                    // (hardware unavailable, no biometrics enrolled, lockout) gets a message
                    // per spec §25 ("biometric unavailable" must be handled gracefully).
                    if (errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                        errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
                        errorCode != BiometricPrompt.ERROR_CANCELED
                    ) {
                        biometricError = errString.toString()
                    }
                }
            }
        )
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock Vault")
            .setNegativeButtonText("Use ${methodLabel(state.method)} instead")
            .build()
        prompt.authenticate(promptInfo)
    }

    LaunchedEffect(state.biometricEnabled) {
        if (state.biometricEnabled && state.recoveryStep == RecoveryStep.HIDDEN) tryBiometric()
    }

    when (state.recoveryStep) {
        RecoveryStep.HIDDEN -> Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Filled.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text("Private Vault", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 12.dp, bottom = 24.dp))

            if (state.errorMessage != null) {
                Text(state.errorMessage!!, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 12.dp))
            }
            if (biometricError != null) {
                Text(biometricError!!, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 12.dp))
            }

            if (state.method == VaultAuthMethod.PATTERN) {
                Text("Draw your pattern", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 12.dp))
                PatternPad(onPatternSubmitted = { viewModel.attemptUnlock(it, onUnlocked) })
            } else {
                OutlinedTextField(
                    value = secretInput,
                    onValueChange = { secretInput = it },
                    label = { Text("Enter ${methodLabel(state.method)}") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = if (state.method == VaultAuthMethod.PIN) KeyboardType.NumberPassword else KeyboardType.Password
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = { viewModel.attemptUnlock(secretInput, onUnlocked) },
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
                ) { Text("Unlock") }
            }

            if (state.biometricEnabled) {
                OutlinedButton(onClick = { tryBiometric() }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    Icon(Icons.Filled.Fingerprint, contentDescription = null)
                    Text(" Use biometric", modifier = Modifier.padding(start = 8.dp))
                }
            }

            TextButton(onClick = viewModel::startRecovery, modifier = Modifier.padding(top = 12.dp)) {
                Text("Forgot your ${methodLabel(state.method)}?")
            }
        }

        RecoveryStep.ENTER_CODE -> RecoveryCodeEntry(
            errorMessage = state.errorMessage,
            onSubmit = viewModel::submitRecoveryCode,
            onCancel = viewModel::cancelRecovery
        )

        RecoveryStep.ENTER_NEW_SECRET -> NewSecretEntry(
            method = state.method,
            onSubmit = { newSecret -> viewModel.submitNewSecret(newSecret) { /* stays on lock screen to unlock normally */ } },
            onCancel = viewModel::cancelRecovery
        )
    }
}

private fun methodLabel(method: VaultAuthMethod) = when (method) {
    VaultAuthMethod.PIN -> "PIN"
    VaultAuthMethod.PASSWORD -> "password"
    VaultAuthMethod.PATTERN -> "pattern"
}

@Composable
private fun RecoveryCodeEntry(errorMessage: String?, onSubmit: (String) -> Unit, onCancel: () -> Unit) {
    var code by remember { mutableStateOf("") }
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Enter your recovery code", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 12.dp))
        Text(
            "Resetting with your recovery code keeps your vault files safe. If you've also lost your recovery code, those files can't be recovered.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        if (errorMessage != null) {
            Text(errorMessage, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 12.dp))
        }
        OutlinedTextField(value = code, onValueChange = { code = it.uppercase() }, label = { Text("Recovery code") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Button(onClick = { onSubmit(code) }, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) { Text("Verify") }
        TextButton(onClick = onCancel, modifier = Modifier.padding(top = 8.dp)) { Text("Cancel") }
    }
}

@Composable
private fun NewSecretEntry(method: VaultAuthMethod, onSubmit: (String) -> Unit, onCancel: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Set a new ${methodLabel(method)}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 16.dp))
        if (method == VaultAuthMethod.PATTERN) {
            PatternPad(onPatternSubmitted = onSubmit)
        } else {
            var value by remember { mutableStateOf("") }
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
            Button(onClick = { onSubmit(value) }, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) { Text("Save") }
        }
        TextButton(onClick = onCancel, modifier = Modifier.padding(top = 8.dp)) { Text("Cancel") }
    }
}
