package com.filemanager.app.data.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Tracks whether the vault is currently unlocked for this app session.
 * A plain singleton (not Room/DataStore-backed) on purpose - this state
 * should never survive a process restart, only a lock/unlock, and it
 * needs to be reachable from FileManagerApplication's process-lifecycle
 * observer without pulling that Application class into Hilt's object graph.
 *
 * Auto-lock timing (spec §27's "Auto-lock timeout" setting): rather than
 * always locking the instant the app backgrounds, [markBackgrounded] just
 * records when that happened; [evaluateLockOnForeground] is called when
 * the app comes back and only locks if more time has passed than the
 * user's configured timeout (0 = lock immediately, the original/default
 * behavior).
 */
object VaultSessionManager {
    private val _isUnlocked = MutableStateFlow(false)
    val isUnlocked: StateFlow<Boolean> = _isUnlocked.asStateFlow()

    private var backgroundedAtMillis: Long? = null

    fun unlock() {
        _isUnlocked.value = true
    }

    fun lock() {
        _isUnlocked.value = false
    }

    fun markBackgrounded() {
        backgroundedAtMillis = System.currentTimeMillis()
    }

    fun evaluateLockOnForeground(timeoutSeconds: Int) {
        val backgroundedAt = backgroundedAtMillis
        backgroundedAtMillis = null
        if (backgroundedAt == null) return
        val elapsedSeconds = (System.currentTimeMillis() - backgroundedAt) / 1000
        if (elapsedSeconds >= timeoutSeconds) {
            lock()
        }
    }
}
