package com.filemanager.app.data.scanner

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
 * Finds folders containing neither files nor subfolders (spec §8). A
 * single cheap traversal — just `listFiles()` per directory, no content
 * reads — so this is fast even on large storage. Never treats a folder
 * that merely contains other empty folders as itself empty; only exact
 * zero-children directories qualify, per spec ("no files, no
 * subfolders") and never deletes anything itself.
 */
@Singleton
class EmptyFolderScanner @Inject constructor() {

    fun scan(root: File): Flow<EmptyFolderScanProgress> = flow {
        val results = mutableListOf<EmptyFolderEntry>()
        val stack = ArrayDeque<File>()
        stack.addLast(root)
        var foldersScanned = 0

        while (stack.isNotEmpty()) {
            currentCoroutineContext().ensureActive()
            val dir = stack.removeLast()
            val children = runCatching { dir.listFiles() }.getOrNull()

            if (children == null) {
                // Inaccessible directory (permission denied) — skip, don't crash the scan.
                continue
            }

            foldersScanned++
            if (dir != root && children.isEmpty()) {
                results.add(EmptyFolderEntry(dir, dir.lastModified()))
            } else {
                for (child in children) {
                    if (child.isDirectory) stack.addLast(child)
                }
            }

            if (foldersScanned % 30 == 0) {
                emit(EmptyFolderScanProgress.Scanning(foldersScanned, dir.absolutePath))
            }
        }

        emit(EmptyFolderScanProgress.Complete(results.sortedBy { it.file.absolutePath }))
    }.flowOn(Dispatchers.IO)
}
