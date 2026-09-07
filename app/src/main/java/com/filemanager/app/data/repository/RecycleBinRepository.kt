package com.filemanager.app.data.repository

import android.content.Context
import com.filemanager.app.data.local.RecycleBinDao
import com.filemanager.app.data.local.RecycleBinEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

sealed class RecycleOpResult {
    data class Success(val entity: RecycleBinEntity) : RecycleOpResult()
    data class Failure(val message: String) : RecycleOpResult()
}

/**
 * Backs the Recycle Bin (spec §19): every "delete" elsewhere in the app
 * should route through here instead of touching files permanently.
 * Physically, trashed items live under the app's own external files
 * directory (same storage volume as most user files, so moves are a
 * fast rename rather than a copy; automatically cleaned up if the app
 * is ever uninstalled). [RecycleBinDao] tracks where each item came
 * from so it can be restored later.
 */
@Singleton
class RecycleBinRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: RecycleBinDao
) {
    private val binDir: File
        get() = File(context.getExternalFilesDir(null), "RecycleBin").apply { if (!exists()) mkdirs() }

    fun observeAll(): Flow<List<RecycleBinEntity>> = dao.observeAll()

    suspend fun moveToRecycleBin(file: File): RecycleOpResult = withContext(Dispatchers.IO) {
        runCatching {
            if (!file.exists()) throw IOException("\"${file.name}\" no longer exists")
            val isDir = file.isDirectory
            val originalPath = file.absolutePath
            val size = if (isDir) directorySize(file) else file.length()
            val destination = File(binDir, "${System.currentTimeMillis()}_${file.name}")

            val moved = file.renameTo(destination)
            if (!moved) {
                // Cross-volume fallback (e.g. SD card -> app-private storage).
                file.copyRecursively(destination, overwrite = false)
                if (isDir) file.deleteRecursively() else file.delete()
            }

            val entity = RecycleBinEntity(
                originalPath = originalPath,
                recycleBinPath = destination.absolutePath,
                fileName = file.name,
                sizeBytes = size,
                deletedAtEpochMillis = System.currentTimeMillis(),
                isDirectory = isDir
            )
            val id = dao.insert(entity)
            RecycleOpResult.Success(entity.copy(id = id))
        }.getOrElse { e -> RecycleOpResult.Failure(e.message ?: "Couldn't move \"${file.name}\" to Recycle Bin") }
    }

    suspend fun moveMultipleToRecycleBin(files: List<File>): List<Pair<File, RecycleOpResult>> =
        files.map { it to moveToRecycleBin(it) }

    suspend fun restore(entity: RecycleBinEntity): RecycleOpResult = withContext(Dispatchers.IO) {
        runCatching {
            val source = File(entity.recycleBinPath)
            if (!source.exists()) throw IOException("This item is no longer in the Recycle Bin")
            val originalParent = File(entity.originalPath).parentFile
                ?: throw IOException("Original location is unknown")
            if (!originalParent.exists()) originalParent.mkdirs()

            val target = uniqueTarget(originalParent, entity.fileName)
            val moved = source.renameTo(target)
            if (!moved) {
                source.copyRecursively(target, overwrite = false)
                if (entity.isDirectory) source.deleteRecursively() else source.delete()
            }

            dao.delete(entity)
            RecycleOpResult.Success(entity)
        }.getOrElse { e -> RecycleOpResult.Failure(e.message ?: "Couldn't restore \"${entity.fileName}\"") }
    }

    suspend fun permanentlyDelete(entity: RecycleBinEntity): FileOpResult = withContext(Dispatchers.IO) {
        runCatching {
            val file = File(entity.recycleBinPath)
            if (file.exists()) {
                val ok = if (entity.isDirectory) file.deleteRecursively() else file.delete()
                if (!ok) throw IOException("Couldn't delete \"${entity.fileName}\"")
            }
            dao.delete(entity)
            FileOpResult.Success
        }.getOrElse { e -> FileOpResult.Failure(e.message ?: "Couldn't delete \"${entity.fileName}\"") }
    }

    suspend fun emptyBin(entities: List<RecycleBinEntity>): FileOpResult = withContext(Dispatchers.IO) {
        runCatching {
            entities.forEach { entity ->
                val file = File(entity.recycleBinPath)
                if (file.exists()) {
                    if (entity.isDirectory) file.deleteRecursively() else file.delete()
                }
            }
            dao.deleteAll()
            FileOpResult.Success
        }.getOrElse { e -> FileOpResult.Failure(e.message ?: "Couldn't empty Recycle Bin") }
    }

    private fun directorySize(dir: File): Long {
        var total = 0L
        val stack = ArrayDeque<File>()
        stack.addLast(dir)
        while (stack.isNotEmpty()) {
            val current = stack.removeLast()
            val children = runCatching { current.listFiles() }.getOrNull() ?: continue
            for (child in children) {
                if (child.isDirectory) stack.addLast(child) else total += child.length()
            }
        }
        return total
    }

    private fun uniqueTarget(destinationDir: File, name: String): File {
        var candidate = File(destinationDir, name)
        if (!candidate.exists()) return candidate
        val dotIndex = name.lastIndexOf('.')
        val base = if (dotIndex > 0) name.substring(0, dotIndex) else name
        val ext = if (dotIndex > 0) name.substring(dotIndex) else ""
        var counter = 1
        while (candidate.exists()) {
            candidate = File(destinationDir, "$base ($counter)$ext")
            counter++
        }
        return candidate
    }
}
