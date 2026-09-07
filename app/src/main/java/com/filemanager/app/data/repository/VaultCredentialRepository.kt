package com.filemanager.app.data.repository

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.SecureRandom
import java.security.spec.KeySpec
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.inject.Inject
import javax.inject.Singleton

enum class VaultAuthMethod { PIN, PASSWORD, PATTERN }

/**
 * Manages the vault's unlock credential per spec Sec.11/Sec.12:
 *   - Never stores the PIN/password/pattern itself - only a PBKDF2 hash
 *     with a random per-install salt.
 *   - The hash+salt live in EncryptedSharedPreferences, which is itself
 *     AES-256 encrypted using a key held in the Android Keystore - so
 *     even the hash is encrypted at rest, not just hashed.
 *   - A separate recovery code (shown once at setup) is hashed the same
 *     way, so "forgot PIN" can be verified without ever storing it in
 *     recoverable form either.
 */
@Singleton
class VaultCredentialRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "vault_credentials",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    suspend fun isSetUp(): Boolean = withContext(Dispatchers.IO) {
        prefs.contains(KEY_SECRET_HASH)
    }

    suspend fun biometricEnabled(): Boolean = withContext(Dispatchers.IO) {
        prefs.getBoolean(KEY_BIOMETRIC_ENABLED, false)
    }

    suspend fun setBiometricEnabled(enabled: Boolean) = withContext(Dispatchers.IO) {
        prefs.edit().putBoolean(KEY_BIOMETRIC_ENABLED, enabled).apply()
    }

    suspend fun authMethod(): VaultAuthMethod? = withContext(Dispatchers.IO) {
        prefs.getString(KEY_METHOD, null)?.let { runCatching { VaultAuthMethod.valueOf(it) }.getOrNull() }
    }

    /** Returns the one-time recovery code the caller must show the user immediately - it is never retrievable again. */
    suspend fun setup(method: VaultAuthMethod, secret: String): String = withContext(Dispatchers.IO) {
        val salt = randomSalt()
        val hash = pbkdf2(secret, salt)
        val recoveryCode = generateRecoveryCode()
        val recoverySalt = randomSalt()
        val recoveryHash = pbkdf2(recoveryCode, recoverySalt)

        prefs.edit()
            .putString(KEY_METHOD, method.name)
            .putString(KEY_SECRET_HASH, hash)
            .putString(KEY_SECRET_SALT, salt)
            .putString(KEY_RECOVERY_HASH, recoveryHash)
            .putString(KEY_RECOVERY_SALT, recoverySalt)
            .apply()

        recoveryCode
    }

    suspend fun verify(secret: String): Boolean = withContext(Dispatchers.IO) {
        val storedHash = prefs.getString(KEY_SECRET_HASH, null) ?: return@withContext false
        val salt = prefs.getString(KEY_SECRET_SALT, null) ?: return@withContext false
        pbkdf2(secret, salt) == storedHash
    }

    suspend fun verifyRecoveryCode(code: String): Boolean = withContext(Dispatchers.IO) {
        val storedHash = prefs.getString(KEY_RECOVERY_HASH, null) ?: return@withContext false
        val salt = prefs.getString(KEY_RECOVERY_SALT, null) ?: return@withContext false
        pbkdf2(code.uppercase(), salt) == storedHash
    }

    /** Only ever called after verifyRecoveryCode succeeds. Vault file encryption is independent of this secret (Keystore-backed), so resetting it never affects already-stored files. */
    suspend fun resetSecret(method: VaultAuthMethod, newSecret: String) = withContext(Dispatchers.IO) {
        val salt = randomSalt()
        val hash = pbkdf2(newSecret, salt)
        prefs.edit()
            .putString(KEY_METHOD, method.name)
            .putString(KEY_SECRET_HASH, hash)
            .putString(KEY_SECRET_SALT, salt)
            .apply()
    }

    /** Change flow from Settings: requires the current secret, unlike [resetSecret] which is only reachable via the recovery code. Returns false if [currentSecret] doesn't verify. */
    suspend fun changeSecret(currentSecret: String, method: VaultAuthMethod, newSecret: String): Boolean =
        withContext(Dispatchers.IO) {
            if (!verify(currentSecret)) return@withContext false
            resetSecret(method, newSecret)
            true
        }

    /** Regenerates the recovery code (invalidating the old one) without touching the main secret. Requires the current secret to prove it's really the owner. Returns the new code, or null if [currentSecret] doesn't verify. */
    suspend fun regenerateRecoveryCode(currentSecret: String): String? = withContext(Dispatchers.IO) {
        if (!verify(currentSecret)) return@withContext null
        val recoveryCode = generateRecoveryCode()
        val recoverySalt = randomSalt()
        val recoveryHash = pbkdf2(recoveryCode, recoverySalt)
        prefs.edit()
            .putString(KEY_RECOVERY_HASH, recoveryHash)
            .putString(KEY_RECOVERY_SALT, recoverySalt)
            .apply()
        recoveryCode
    }

    private fun randomSalt(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
    }

    private fun pbkdf2(secret: String, saltBase64: String): String {
        val salt = android.util.Base64.decode(saltBase64, android.util.Base64.NO_WRAP)
        val spec: KeySpec = PBEKeySpec(secret.toCharArray(), salt, 120_000, 256)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val hash = factory.generateSecret(spec).encoded
        return android.util.Base64.encodeToString(hash, android.util.Base64.NO_WRAP)
    }

    private fun generateRecoveryCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789" // no ambiguous 0/O/1/I
        val random = SecureRandom()
        return (1..16).map { chars[random.nextInt(chars.length)] }
            .chunked(4).joinToString("-") { it.joinToString("") }
    }

    companion object {
        private const val KEY_METHOD = "auth_method"
        private const val KEY_SECRET_HASH = "secret_hash"
        private const val KEY_SECRET_SALT = "secret_salt"
        private const val KEY_RECOVERY_HASH = "recovery_hash"
        private const val KEY_RECOVERY_SALT = "recovery_salt"
        private const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"
    }
}
