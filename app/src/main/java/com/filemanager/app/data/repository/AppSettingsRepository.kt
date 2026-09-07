package com.filemanager.app.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.filemanager.app.data.model.ViewMode
import com.filemanager.app.ui.theme.AppThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Every persisted app preference lives here (spec §27), backed by
 * Jetpack DataStore. Each setting is exposed as a Flow so screens that
 * care (e.g. MainActivity for theme, FilesViewModel for default view)
 * react live if changed from Settings while they're open, plus a
 * one-shot suspend getter for call sites that just need the current
 * value once (e.g. a repository deciding whether to filter hidden files).
 */
@Singleton
class AppSettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DEFAULT_VIEW_MODE = stringPreferencesKey("default_view_mode")
        val SHOW_HIDDEN_FILES = booleanPreferencesKey("show_hidden_files")
        val CONFIRM_BEFORE_DELETE = booleanPreferencesKey("confirm_before_delete")
        val VAULT_AUTO_LOCK_TIMEOUT_SECONDS = intPreferencesKey("vault_auto_lock_timeout_seconds")
    }

    val themeMode: Flow<AppThemeMode> = dataStore.data.map { prefs ->
        prefs[Keys.THEME_MODE]?.let { runCatching { AppThemeMode.valueOf(it) }.getOrNull() } ?: AppThemeMode.SYSTEM
    }
    suspend fun setThemeMode(mode: AppThemeMode) {
        dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    val defaultViewMode: Flow<ViewMode> = dataStore.data.map { prefs ->
        prefs[Keys.DEFAULT_VIEW_MODE]?.let { runCatching { ViewMode.valueOf(it) }.getOrNull() } ?: ViewMode.LIST
    }
    suspend fun setDefaultViewMode(mode: ViewMode) {
        dataStore.edit { it[Keys.DEFAULT_VIEW_MODE] = mode.name }
    }
    suspend fun defaultViewModeOnce(): ViewMode = defaultViewMode.first()

    val showHiddenFiles: Flow<Boolean> = dataStore.data.map { it[Keys.SHOW_HIDDEN_FILES] ?: false }
    suspend fun setShowHiddenFiles(show: Boolean) {
        dataStore.edit { it[Keys.SHOW_HIDDEN_FILES] = show }
    }
    suspend fun showHiddenFilesOnce(): Boolean = showHiddenFiles.first()

    val confirmBeforeDelete: Flow<Boolean> = dataStore.data.map { it[Keys.CONFIRM_BEFORE_DELETE] ?: true }
    suspend fun setConfirmBeforeDelete(confirm: Boolean) {
        dataStore.edit { it[Keys.CONFIRM_BEFORE_DELETE] = confirm }
    }
    suspend fun confirmBeforeDeleteOnce(): Boolean = confirmBeforeDelete.first()

    /** 0 = lock immediately on background (the default, matching the original Vault behavior). */
    val vaultAutoLockTimeoutSeconds: Flow<Int> = dataStore.data.map { it[Keys.VAULT_AUTO_LOCK_TIMEOUT_SECONDS] ?: 0 }
    suspend fun setVaultAutoLockTimeoutSeconds(seconds: Int) {
        dataStore.edit { it[Keys.VAULT_AUTO_LOCK_TIMEOUT_SECONDS] = seconds }
    }
    suspend fun vaultAutoLockTimeoutSecondsOnce(): Int = vaultAutoLockTimeoutSeconds.first()
}
