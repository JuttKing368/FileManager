package com.filemanager.app.data.scanner

import com.filemanager.app.data.model.FileCategoryClassifier
import com.filemanager.app.data.model.FileEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Finds files at or above a configurable size threshold (spec §9). A
 * single traversal — no hashing, no content reads, just size stats — so
 * this is cheap even at the 1GB+ range across a whole device. Never
 * deletes anything itself.
 */
@Singleton
class LargeFileScanner @Inject constructor() {

    fun scan(root: File, minSizeBytes: Long): Flow<LargeFileScanProgress> = flow {
        val results = mutableListOf<FileEntry>()
        val stack = ArrayDeque<File>()
        stack.addLast(root)
        var filesScanned = 0

        while (stack.isNotEmpty()) {
            currentCoroutineContext().ensureActive()
            val current = stack.removeLast()
            val children = runCatching { current.listFiles() }.getOrNull() ?: continue
            for (child in children) {
                if (child.isDirectory) {
                    stack.addLast(child)
                    continue
                }
                filesScanned++
                val size = runCatching { child.length() }.getOrDefault(0L)
                if (size >= minSizeBytes) {
                    results.add(
                        FileEntry(
                            name = child.name,
                            path = child.absolutePath,
                            isDirectory = false,
                            sizeBytes = size,
                            lastModified = child.lastModified(),
                            mimeType = FileCategoryClassifier.mimeTypeFor(child.name),
                            category = FileCategoryClassifier.classify(child.name, isDirectory = false)
                        )
                    )
                }
                if (filesScanned % 100 == 0) {
                    emit(LargeFileScanProgress.Scanning(filesScanned, current.absolutePath))
                }
            }
        }

        emit(LargeFileScanProgress.Complete(results.sortedByDescending { it.sizeBytes }))
    }.flowOn(Dispatchers.IO)
}
