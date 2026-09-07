package com.filemanager.app.data.repository

import android.content.Context
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import com.filemanager.app.data.model.FileCategory
import com.filemanager.app.data.model.FileCategoryClassifier
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class StorageOverview(
    val totalBytes: Long,
    val usedBytes: Long,
    val freeBytes: Long
)

/**
 * Reads real device storage numbers. Uses StatFs for the overall
 * used/free/total (instant, no file traversal) and a single lightweight
 * MediaStore.Files query for the per-category breakdown shown on the
 * dashboard — this avoids a full recursive filesystem walk just to
 * populate the home screen, per the performance requirements
 * (no full traversal on every app open; the heavier scanners run
 * on demand as background jobs).
 */
@Singleton
class StorageStatsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {

    fun getStorageOverview(path: File = Environment.getExternalStorageDirectory()): StorageOverview {
        val statFs = StatFs(path.path)
        val total = statFs.blockCountLong * statFs.blockSizeLong
        val free = statFs.availableBlocksLong * statFs.blockSizeLong
        return StorageOverview(
            totalBytes = total,
            usedBytes = total - free,
            freeBytes = free
        )
    }

    /** Returns bytes used per [FileCategory], derived from MediaStore's file index. */
    suspend fun getCategoryBreakdown(): Map<FileCategory, Long> = withContext(Dispatchers.IO) {
        val result = mutableMapOf<FileCategory, Long>().withDefault { 0L }
        val collection = MediaStore.Files.getContentUri("external")
        val projection = arrayOf(
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.SIZE
        )

        runCatching {
            context.contentResolver.query(collection, projection, null, null, null)?.use { cursor ->
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
                while (cursor.moveToNext()) {
                    val name = cursor.getString(nameCol) ?: continue
                    val size = cursor.getLong(sizeCol)
                    val category = FileCategoryClassifier.classify(name, isDirectory = false)
                    result[category] = result.getValue(category) + size
                }
            }
        }

        result
    }
}
