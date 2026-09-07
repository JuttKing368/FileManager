package com.filemanager.app.data.repository

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * File-content encryption for the vault (spec Sec.10: files must be
 * genuinely encrypted, never just renamed/hidden). Uses a single
 * AES-256 key generated inside the Android Keystore — the raw key
 * material never leaves secure hardware/OS keystore and this app never
 * has direct access to its bytes, only the ability to ask the Keystore
 * to encrypt/decrypt with it. This key is intentionally independent of
 * the user's PIN/password: resetting the PIN via recovery never affects
 * already-encrypted files.
 */
@Singleton
class VaultCryptoRepository @Inject constructor() {

    private val secretKey: SecretKey by lazy { getOrCreateKey() }

    /** Encrypts [source] into [destination], returning the IV needed to decrypt it later. Deletes neither file. */
    fun encryptFile(source: File, destination: File): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val iv = cipher.iv
        source.inputStream().use { input ->
            destination.outputStream().use { rawOutput ->
                javax.crypto.CipherOutputStream(rawOutput, cipher).use { output ->
                    input.copyTo(output)
                }
            }
        }
        return iv
    }

    /** Decrypts [source] (encrypted with the given [iv]) into [destination]. */
    fun decryptFile(source: File, iv: ByteArray, destination: File) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        source.inputStream().use { rawInput ->
            javax.crypto.CipherInputStream(rawInput, cipher).use { input ->
                destination.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    companion object {
        private const val KEY_ALIAS = "vault_aes_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH_BITS = 128
    }
}
