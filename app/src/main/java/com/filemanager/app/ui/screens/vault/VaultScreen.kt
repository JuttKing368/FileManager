package com.filemanager.app.ui.screens.vault

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.filemanager.app.data.repository.VaultSessionManager
import com.filemanager.app.ui.screens.vault.content.VaultContentScreen
import com.filemanager.app.ui.screens.vault.lock.VaultLockScreen
import com.filemanager.app.ui.screens.vault.setup.VaultSetupScreen

/**
 * Routes the Vault tab to one of three states: not set up yet, locked,
 * or unlocked. [VaultSessionManager] tracks unlock state for the process
 * lifetime only — it's reset to locked automatically whenever the whole
 * app backgrounds (see FileManagerApplication), and always starts locked
 * on a fresh process.
 */
@Composable
fun VaultScreen(navController: NavHostController, viewModel: VaultViewModel = hiltViewModel()) {
    val isSetUp by viewModel.isSetUp.collectAsState()
    val isUnlocked by VaultSessionManager.isUnlocked.collectAsState()

    when {
        isSetUp == null -> Box(modifier = Modifier.fillMaxSize()) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }
        isSetUp == false -> VaultSetupScreen(onSetupComplete = {
            VaultSessionManager.unlock()
            viewModel.refresh()
        })
        !isUnlocked -> VaultLockScreen(onUnlocked = { VaultSessionManager.unlock() })
        else -> VaultContentScreen()
    }
}
