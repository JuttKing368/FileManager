package com.filemanager.app.ui.screens.vault.setup

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.filemanager.app.data.repository.VaultAuthMethod
import com.filemanager.app.data.repository.VaultCredentialRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SetupStep { CHOOSE_METHOD, ENTER_SECRET, CONFIRM_SECRET, SHOW_RECOVERY }

data class VaultSetupUiState(
    val step: SetupStep = SetupStep.CHOOSE_METHOD,
    val method: VaultAuthMethod = VaultAuthMethod.PIN,
    val firstEntry: String = "",
    val errorMessage: String? = null,
    val recoveryCode: String? = null,
    val biometricAvailable: Boolean = false,
    val enableBiometric: Boolean = false
)

@HiltViewModel
class VaultSetupViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val credentialRepository: VaultCredentialRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        VaultSetupUiState(biometricAvailable = isBiometricAvailable())
    )
    val uiState: StateFlow<VaultSetupUiState> = _uiState.asStateFlow()

    private fun isBiometricAvailable(): Boolean {
        val manager = BiometricManager.from(context)
        return manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
            BiometricManager.BIOMETRIC_SUCCESS
    }

    fun selectMethod(method: VaultAuthMethod) {
        _uiState.value = _uiState.value.copy(method = method)
    }

    fun proceedToEnterSecret() {
        _uiState.value = _uiState.value.copy(step = SetupStep.ENTER_SECRET, errorMessage = null)
    }

    fun submitFirstEntry(value: String) {
        if (!isValidSecret(value)) {
            _uiState.value = _uiState.value.copy(errorMessage = minLengthMessage())
            return
        }
        _uiState.value = _uiState.value.copy(firstEntry = value, step = SetupStep.CONFIRM_SECRET, errorMessage = null)
    }

    fun submitConfirmEntry(value: String) {
        if (value != _uiState.value.firstEntry) {
            _uiState.value = _uiState.value.copy(
                errorMessage = "Didn't match - try again",
                step = SetupStep.ENTER_SECRET,
                firstEntry = ""
            )
            return
        }
        viewModelScope.launch {
            val recoveryCode = credentialRepository.setup(_uiState.value.method, value)
            _uiState.value = _uiState.value.copy(step = SetupStep.SHOW_RECOVERY, recoveryCode = recoveryCode)
        }
    }

    fun toggleBiometric(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(enableBiometric = enabled)
    }

    fun finishSetup(onComplete: () -> Unit) {
        viewModelScope.launch {
            credentialRepository.setBiometricEnabled(_uiState.value.enableBiometric)
            onComplete()
        }
    }

    private fun isValidSecret(value: String): Boolean = when (_uiState.value.method) {
        VaultAuthMethod.PIN -> value.length >= 4 && value.all { it.isDigit() }
        VaultAuthMethod.PASSWORD -> value.length >= 6
        VaultAuthMethod.PATTERN -> value.split(",").filter { it.isNotBlank() }.size >= 4
    }

    private fun minLengthMessage(): String = when (_uiState.value.method) {
        VaultAuthMethod.PIN -> "PIN must be at least 4 digits"
        VaultAuthMethod.PASSWORD -> "Password must be at least 6 characters"
        VaultAuthMethod.PATTERN -> "Connect at least 4 dots"
    }
}
