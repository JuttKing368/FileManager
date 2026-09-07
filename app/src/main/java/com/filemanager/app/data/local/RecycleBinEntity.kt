package com.filemanager.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One row per item currently sitting in the Recycle Bin (spec §19).
 * [recycleBinPath] is where the actual file/folder physically lives
 * right now (inside the app's private storage); [originalPath] is
 * where it came from, used to offer restoring it back to that exact
 * location.
 */
@Entity(tableName = "recycle_bin")
data class RecycleBinEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val originalPath: String,
    val recycleBinPath: String,
    val fileName: String,
    val sizeBytes: Long,
    val deletedAtEpochMillis: Long,
    val isDirectory: Boolean
)
