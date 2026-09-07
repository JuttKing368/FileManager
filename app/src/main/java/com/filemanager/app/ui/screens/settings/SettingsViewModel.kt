package com.filemanager.app.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.filemanager.app.data.model.ViewMode
import com.filemanager.app.data.repository.AppSettingsRepository
import com.filemanager.app.data.repository.VaultAuthMethod
import com.filemanager.app.data.repository.VaultCredentialRepository
import com.filemanager.app.ui.theme.AppThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val defaultViewMode: ViewMode = ViewMode.LIST,
    val showHiddenFiles: Boolean = false,
    val confirmBeforeDelete: Boolean = true,
    val vaultAutoLockTimeoutSeconds: Int = 0,
    val isVaultSetUp: Boolean = false,
    val vaultAuthMethod: VaultAuthMethod? = null,
    val vaultBiometricEnabled: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: AppSettingsRepository,
    private val credentialRepository: VaultCredentialRepository
) : ViewModel() {

    private val vaultInfo = MutableStateFlow(Triple(false, null as VaultAuthMethod?, false))

    private data class BaseSettings(
        val themeMode: AppThemeMode,
        val defaultViewMode: ViewMode,
        val showHiddenFiles: Boolean,
        val confirmBeforeDelete: Boolean,
        val vaultAutoLockTimeoutSeconds: Int
    )

    private val baseSettings: kotlinx.coroutines.flow.Flow<BaseSettings> = combine(
        settingsRepository.themeMode,
        settingsRepository.defaultViewMode,
        settingsRepository.showHiddenFiles,
        settingsRepository.confirmBeforeDelete,
        settingsRepository.vaultAutoLockTimeoutSeconds
    ) { theme, view, hidden, confirm, timeout ->
        BaseSettings(theme, view, hidden, confirm, timeout)
    }

    val uiState: StateFlow<SettingsUiState> = combine(baseSettings, vaultInfo) { base, info ->
        SettingsUiState(
            themeMode = base.themeMode,
            defaultViewMode = base.defaultViewMode,
            showHiddenFiles = base.showHiddenFiles,
            confirmBeforeDelete = base.confirmBeforeDelete,
            vaultAutoLockTimeoutSeconds = base.vaultAutoLockTimeoutSeconds,
            isVaultSetUp = info.first,
            vaultAuthMethod = info.second,
            vaultBiometricEnabled = info.third
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsUiState())

    init {
        refreshVaultInfo()
    }

    fun refreshVaultInfo() {
        viewModelScope.launch {
            vaultInfo.value = Triple(
                credentialRepository.isSetUp(),
                credentialRepository.authMethod(),
                credentialRepository.biometricEnabled()
            )
        }
    }

    fun setThemeMode(mode: AppThemeMode) = viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    fun setDefaultViewMode(mode: ViewMode) = viewModelScope.launch { settingsRepository.setDefaultViewMode(mode) }
    fun setShowHiddenFiles(show: Boolean) = viewModelScope.launch { settingsRepository.setShowHiddenFiles(show) }
    fun setConfirmBeforeDelete(confirm: Boolean) = viewModelScope.launch { settingsRepository.setConfirmBeforeDelete(confirm) }
    fun setVaultAutoLockTimeout(seconds: Int) = viewModelScope.launch { settingsRepository.setVaultAutoLockTimeoutSeconds(seconds) }

    fun setVaultBiometricEnabled(enabled: Boolean) {
        viewModelScope.launch {
            credentialRepository.setBiometricEnabled(enabled)
            refreshVaultInfo()
        }
    }

    fun changeVaultCredential(currentSecret: String, newMethod: VaultAuthMethod, newSecret: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val success = credentialRepository.changeSecret(currentSecret, newMethod, newSecret)
            if (success) refreshVaultInfo()
            onResult(success)
        }
    }

    fun regenerateRecoveryCode(currentSecret: String, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            onResult(credentialRepository.regenerateRecoveryCode(currentSecret))
        }
    }
}
