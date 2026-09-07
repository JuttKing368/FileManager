package com.filemanager.app.ui.screens.vault.lock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.filemanager.app.data.repository.VaultAuthMethod
import com.filemanager.app.data.repository.VaultCredentialRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class RecoveryStep { HIDDEN, ENTER_CODE, ENTER_NEW_SECRET }

data class VaultLockUiState(
    val method: VaultAuthMethod = VaultAuthMethod.PIN,
    val biometricEnabled: Boolean = false,
    val errorMessage: String? = null,
    val recoveryStep: RecoveryStep = RecoveryStep.HIDDEN,
    val recoveryVerified: Boolean = false
)

@HiltViewModel
class VaultLockViewModel @Inject constructor(
    private val credentialRepository: VaultCredentialRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(VaultLockUiState())
    val uiState: StateFlow<VaultLockUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                method = credentialRepository.authMethod() ?: VaultAuthMethod.PIN,
                biometricEnabled = credentialRepository.biometricEnabled()
            )
        }
    }

    fun attemptUnlock(secret: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            if (credentialRepository.verify(secret)) {
                _uiState.value = _uiState.value.copy(errorMessage = null)
                onSuccess()
            } else {
                _uiState.value = _uiState.value.copy(errorMessage = "Incorrect — try again")
            }
        }
    }

    fun startRecovery() {
        _uiState.value = _uiState.value.copy(recoveryStep = RecoveryStep.ENTER_CODE, errorMessage = null)
    }

    fun cancelRecovery() {
        _uiState.value = VaultLockUiState(method = _uiState.value.method, biometricEnabled = _uiState.value.biometricEnabled)
    }

    fun submitRecoveryCode(code: String) {
        viewModelScope.launch {
            if (credentialRepository.verifyRecoveryCode(code)) {
                _uiState.value = _uiState.value.copy(recoveryStep = RecoveryStep.ENTER_NEW_SECRET, errorMessage = null)
            } else {
                _uiState.value = _uiState.value.copy(errorMessage = "That recovery code doesn't match")
            }
        }
    }

    fun submitNewSecret(newSecret: String, onDone: () -> Unit) {
        viewModelScope.launch {
            credentialRepository.resetSecret(_uiState.value.method, newSecret)
            _uiState.value = VaultLockUiState(method = _uiState.value.method, biometricEnabled = _uiState.value.biometricEnabled)
            onDone()
        }
    }
}
