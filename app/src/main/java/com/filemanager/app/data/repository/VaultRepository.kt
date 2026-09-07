package com.filemanager.app.data.repository

import android.content.Context
import com.filemanager.app.data.local.VaultDao
import com.filemanager.app.data.local.VaultItemEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

sealed class VaultOpResult {
    data object Success : VaultOpResult()
    data class Failure(val message: String) : VaultOpResult()
}

/**
 * Orchestrates moving files in and out of the Private Vault (spec Sec.10):
 * encrypt-then-delete-original on the way in, decrypt-then-delete-vault-copy
 * on the way out. Never leaves an unencrypted copy behind after adding,
 * and never leaves the vault's encrypted copy behind after restoring.
 */
@Singleton
class VaultRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: VaultDao,
    private val crypto: VaultCryptoRepository
) {
    private val vaultDir: File
        get() = File(context.filesDir, "vault").apply { if (!exists()) mkdirs() }

    fun observeAll(): Flow<List<VaultItemEntity>> = dao.observeAll()

    suspend fun addToVault(file: File): VaultOpResult = withContext(Dispatchers.IO) {
        runCatching {
            if (!file.exists() || file.isDirectory) {
                throw IllegalArgumentException("Only individual files can be added to the Vault right now")
            }
            val vaultFileName = "${UUID.randomUUID()}.enc"
            val destination = File(vaultDir, vaultFileName)
            val iv = crypto.encryptFile(file, destination)

            dao.insert(
                VaultItemEntity(
                    originalPath = file.absolutePath,
                    vaultFileName = vaultFileName,
                    ivBase64 = android.util.Base64.encodeToString(iv, android.util.Base64.NO_WRAP),
                    displayName = file.name,
                    sizeBytes = file.length(),
                    addedAtEpochMillis = System.currentTimeMillis()
                )
            )
            file.delete() // the whole point of the vault: no unencrypted copy left behind
            VaultOpResult.Success
        }.getOrElse { e -> VaultOpResult.Failure(e.message ?: "Couldn't add \"${file.name}\" to the Vault") }
    }

    /** Decrypts back out to [destination] (defaults to the original location) and removes it from the Vault. */
    suspend fun removeFromVault(entity: VaultItemEntity, destination: File = File(entity.originalPath)): VaultOpResult =
        withContext(Dispatchers.IO) {
            runCatching {
                val encryptedFile = File(vaultDir, entity.vaultFileName)
                val iv = android.util.Base64.decode(entity.ivBase64, android.util.Base64.NO_WRAP)
                val finalDestination = uniqueTarget(destination)
                finalDestination.parentFile?.let { if (!it.exists()) it.mkdirs() }
                crypto.decryptFile(encryptedFile, iv, finalDestination)
                encryptedFile.delete()
                dao.delete(entity)
                VaultOpResult.Success
            }.getOrElse { e -> VaultOpResult.Failure(e.message ?: "Couldn't restore \"${entity.displayName}\"") }
        }

    /** Decrypts a temporary, viewable copy for opening/sharing without removing it from the vault. Caller should treat this as short-lived. */
    suspend fun decryptToCache(entity: VaultItemEntity): File = withContext(Dispatchers.IO) {
        val encryptedFile = File(vaultDir, entity.vaultFileName)
        val iv = android.util.Base64.decode(entity.ivBase64, android.util.Base64.NO_WRAP)
        val tempDir = File(context.cacheDir, "vault_temp").apply { if (!exists()) mkdirs() }
        val tempFile = File(tempDir, entity.displayName)
        crypto.decryptFile(encryptedFile, iv, tempFile)
        tempFile
    }

    private fun uniqueTarget(preferred: File): File {
        if (!preferred.exists()) return preferred
        val name = preferred.name
        val dotIndex = name.lastIndexOf('.')
        val base = if (dotIndex > 0) name.substring(0, dotIndex) else name
        val ext = if (dotIndex > 0) name.substring(dotIndex) else ""
        var counter = 1
        var candidate = preferred
        while (candidate.exists()) {
            candidate = File(preferred.parentFile, "$base ($counter)$ext")
            counter++
        }
        return candidate
    }
}
