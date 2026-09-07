package com.filemanager.app.data.repository

import android.content.Context
import com.filemanager.app.data.model.FileCategory
import com.filemanager.app.data.model.FileCategoryClassifier
import com.filemanager.app.data.model.FileEntry
import com.filemanager.app.data.model.SortDirection
import com.filemanager.app.data.model.SortField
import com.filemanager.app.data.model.SortOption
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

sealed class FileOpResult {
    data object Success : FileOpResult()
    data class Failure(val message: String) : FileOpResult()
}

data class FileInfo(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val mimeType: String?,
    val lastModified: Long,
    val canRead: Boolean,
    val canWrite: Boolean,
    val itemCount: Int? // for directories: immediate children count
)

/**
 * Every file operation here touches the real filesystem — nothing here is
 * mocked. Callers (ViewModels) run these on Dispatchers.IO via the suspend
 * functions below and never block the main thread.
 */
@Singleton
class FileOperationsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: AppSettingsRepository
) {

    suspend fun listDirectory(directory: File): List<FileEntry> = withContext(Dispatchers.IO) {
        val children = directory.listFiles() ?: return@withContext emptyList()
        val showHidden = settingsRepository.showHiddenFilesOnce()
        children
            .filter { showHidden || !it.name.startsWith(".") }
            .mapNotNull { file ->
                runCatching {
                    FileEntry(
                        name = file.name,
                        path = file.absolutePath,
                        isDirectory = file.isDirectory,
                        sizeBytes = if (file.isDirectory) 0L else file.length(),
                        lastModified = file.lastModified(),
                        mimeType = if (file.isDirectory) null else FileCategoryClassifier.mimeTypeFor(file.name),
                        category = FileCategoryClassifier.classify(file.name, file.isDirectory)
                    )
                }.getOrNull()
                // Skip entries we can't stat (permission errors, race conditions
                // where the file vanished mid-scan) instead of crashing the browse.
            }
    }

    fun sortEntries(entries: List<FileEntry>, sort: SortOption): List<FileEntry> {
        // Folders always float to the top, matching common file-manager UX,
        // then the chosen sort applies within each group.
        val comparator = when (sort.field) {
            SortField.NAME -> compareBy<FileEntry> { it.name.lowercase() }
            SortField.SIZE -> compareBy { it.sizeBytes }
            SortField.DATE_MODIFIED -> compareBy { it.lastModified }
            SortField.TYPE -> compareBy { it.category.name }
        }
        val directional = if (sort.direction == SortDirection.DESCENDING) comparator.reversed() else comparator
        return entries.sortedWith(compareByDescending<FileEntry> { it.isDirectory }.then(directional))
    }

    suspend fun searchRecursive(
        root: File,
        query: String,
        category: FileCategory? = null,
        maxResults: Int = 500
    ): List<FileEntry> = withContext(Dispatchers.IO) {
        val results = mutableListOf<FileEntry>()
        val lowerQuery = query.lowercase()
        val stack = ArrayDeque<File>()
        stack.addLast(root)

        while (stack.isNotEmpty() && results.size < maxResults) {
            val current = stack.removeLast()
            val children = runCatching { current.listFiles() }.getOrNull() ?: continue
            for (child in children) {
                if (child.isDirectory) {
                    stack.addLast(child)
                }
                val matchesName = child.name.lowercase().contains(lowerQuery)
                if (!matchesName) continue
                val cat = FileCategoryClassifier.classify(child.name, child.isDirectory)
                if (category != null && cat != category) continue
                results.add(
                    FileEntry(
                        name = child.name,
                        path = child.absolutePath,
                        isDirectory = child.isDirectory,
                        sizeBytes = if (child.isDirectory) 0L else child.length(),
                        lastModified = child.lastModified(),
                        mimeType = if (child.isDirectory) null else FileCategoryClassifier.mimeTypeFor(child.name),
                        category = cat
                    )
                )
                if (results.size >= maxResults) break
            }
        }
        results
    }

    suspend fun createFolder(parent: File, name: String): FileOpResult = withContext(Dispatchers.IO) {
        val target = File(parent, name)
        if (target.exists()) return@withContext FileOpResult.Failure("A file or folder named \"$name\" already exists")
        return@withContext if (target.mkdir()) FileOpResult.Success
        else FileOpResult.Failure("Couldn't create folder — check storage permissions")
    }

    suspend fun rename(file: File, newName: String): FileOpResult = withContext(Dispatchers.IO) {
        val target = File(file.parentFile, newName)
        if (target.exists()) return@withContext FileOpResult.Failure("A file or folder named \"$newName\" already exists")
        return@withContext if (file.renameTo(target)) FileOpResult.Success
        else FileOpResult.Failure("Rename failed")
    }

    suspend fun delete(file: File): FileOpResult = withContext(Dispatchers.IO) {
        val ok = if (file.isDirectory) file.deleteRecursively() else file.delete()
        return@withContext if (ok) FileOpResult.Success else FileOpResult.Failure("Couldn't delete \"${file.name}\"")
    }

    suspend fun deleteMultiple(files: List<File>): List<Pair<File, FileOpResult>> = withContext(Dispatchers.IO) {
        files.map { it to delete(it) }
    }

    suspend fun copy(source: File, destinationDir: File): FileOpResult = withContext(Dispatchers.IO) {
        runCatching {
            val target = uniqueTarget(destinationDir, source.name)
            if (source.isDirectory) copyDirectoryRecursive(source, target) else source.copyTo(target)
            FileOpResult.Success
        }.getOrElse { e -> FileOpResult.Failure(e.message ?: "Copy failed") }
    }

    suspend fun move(source: File, destinationDir: File): FileOpResult = withContext(Dispatchers.IO) {
        runCatching {
            val target = uniqueTarget(destinationDir, source.name)
            val movedWithRename = source.renameTo(target)
            if (!movedWithRename) {
                // renameTo fails across different storage volumes/filesystems —
                // fall back to copy-then-delete-original.
                if (source.isDirectory) copyDirectoryRecursive(source, target) else source.copyTo(target)
                if (source.isDirectory) source.deleteRecursively() else source.delete()
            }
            FileOpResult.Success
        }.getOrElse { e -> FileOpResult.Failure(e.message ?: "Move failed") }
    }

    suspend fun getFileInfo(file: File): FileInfo = withContext(Dispatchers.IO) {
        FileInfo(
            name = file.name,
            path = file.absolutePath,
            isDirectory = file.isDirectory,
            sizeBytes = if (file.isDirectory) directorySize(file) else file.length(),
            mimeType = if (file.isDirectory) null else FileCategoryClassifier.mimeTypeFor(file.name),
            lastModified = file.lastModified(),
            canRead = file.canRead(),
            canWrite = file.canWrite(),
            itemCount = if (file.isDirectory) file.listFiles()?.size else null
        )
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

    private fun copyDirectoryRecursive(source: File, target: File) {
        if (!target.exists() && !target.mkdirs()) {
            throw IOException("Couldn't create \"${target.name}\"")
        }
        source.listFiles()?.forEach { child ->
            val childTarget = File(target, child.name)
            if (child.isDirectory) copyDirectoryRecursive(child, childTarget) else child.copyTo(childTarget, overwrite = false)
        }
    }

    /** Avoids silently overwriting an existing item at the destination by appending " (1)", " (2)", etc. */
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
