package com.filemanager.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One row per file currently stored in the Private Vault (spec §10).
 * The actual bytes on disk at [vaultFileName] (inside the app's private
 * `filesDir/vault/` directory) are AES-GCM encrypted — this row only
 * holds metadata needed to decrypt and, if the user removes it, restore
 * the file to its original location. [ivBase64] is the per-file AES-GCM
 * initialization vector, unique per file, required to decrypt.
 */
@Entity(tableName = "vault_items")
data class VaultItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val originalPath: String,
    val vaultFileName: String,
    val ivBase64: String,
    val displayName: String,
    val sizeBytes: Long,
    val addedAtEpochMillis: Long
)
