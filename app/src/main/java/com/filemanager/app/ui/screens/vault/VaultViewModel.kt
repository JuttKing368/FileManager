package com.filemanager.app.ui.screens.vault

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.filemanager.app.data.repository.VaultCredentialRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Top-level router state for the Vault tab: not set up yet, locked, or unlocked. */
@HiltViewModel
class VaultViewModel @Inject constructor(
    private val credentialRepository: VaultCredentialRepository
) : ViewModel() {

    private val _isSetUp = MutableStateFlow<Boolean?>(null)
    val isSetUp: StateFlow<Boolean?> = _isSetUp.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _isSetUp.value = credentialRepository.isSetUp()
        }
    }
}
