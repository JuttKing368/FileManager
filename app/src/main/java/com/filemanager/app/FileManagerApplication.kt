package com.filemanager.app

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.filemanager.app.data.repository.AppSettingsRepository
import com.filemanager.app.data.repository.VaultSessionManager
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@EntryPoint
@InstallIn(SingletonComponent::class)
interface AppSettingsEntryPoint {
    fun appSettingsRepository(): AppSettingsRepository
}

@HiltAndroidApp
class FileManagerApplication : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()

        val settingsRepository by lazy {
            EntryPointAccessors.fromApplication(this, AppSettingsEntryPoint::class.java).appSettingsRepository()
        }

        // Vault auto-lock (spec §10 + the §27 "Auto-lock timeout" setting):
        // record the moment the whole app backgrounds, then on return only
        // lock if the user's configured timeout has actually elapsed.
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) {
                VaultSessionManager.markBackgrounded()
            }

            override fun onStart(owner: LifecycleOwner) {
                appScope.launch {
                    val timeoutSeconds = settingsRepository.vaultAutoLockTimeoutSecondsOnce()
                    VaultSessionManager.evaluateLockOnForeground(timeoutSeconds)
                }
            }
        })
    }
}
