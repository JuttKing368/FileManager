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
 * Detects duplicate folders per spec §7: compares folder contents by file
 * names, sizes, hashes, and structure — never by folder name alone.
 *
 * 1. Walk the tree bottom-up, computing each directory's total recursive
 *    size and file count (cheap — just stats, no content reads).
 * 2. Bucket non-empty directories by total size. Only buckets with 2+
 *    directories are candidates — mirrors the file scanner's size
 *    prefilter (spec §24) so we don't deep-hash every folder on the
 *    device, only ones that could plausibly match.
 * 3. For candidates, compute a deep content signature bottom-up: a
 *    directory's signature is a hash of its sorted "childName:childHash"
 *    pairs, where a file's "hash" is its SHA-256 and a subdirectory's
 *    "hash" is its own signature (memoized, so shared subtrees between
 *    candidates are only hashed once).
 * 4. Directories sharing a signature are true duplicates — identical
 *    content and structure, regardless of location or folder name.
 *
 * Never deletes anything itself (spec §7: confirmation required before
 * any deletion, handled by the UI layer).
 */
@Singleton
class DuplicateFolderScanner @Inject constructor() {

    fun scan(root: File): Flow<DuplicateFolderScanProgress> = flow {
        val sizeCache = HashMap<String, Long>()
        val countCache = HashMap<String, Int>()
        var foldersScanned = 0

        // Phase 1: iterative post-order traversal to compute recursive size/count per directory.
        val allDirs = mutableListOf<File>()
        val visitStack = ArrayDeque<File>()
        visitStack.addLast(root)
        while (visitStack.isNotEmpty()) {
            currentCoroutineContext().ensureActive()
            val dir = visitStack.removeLast()
            allDirs.add(dir)
            val children = runCatching { dir.listFiles() }.getOrNull() ?: continue
            for (child in children) {
                if (child.isDirectory) visitStack.addLast(child)
            }
        }
        // Process deepest-first so a parent's total can sum already-computed children.
        for (dir in allDirs.sortedByDescending { it.absolutePath.count { c -> c == '/' } }) {
            currentCoroutineContext().ensureActive()
            val children = runCatching { dir.listFiles() }.getOrNull() ?: emptyArray()
            var size = 0L
            var count = 0
            for (child in children) {
                if (child.isDirectory) {
                    size += sizeCache[child.absolutePath] ?: 0L
                    count += countCache[child.absolutePath] ?: 0
                } else {
                    size += runCatching { child.length() }.getOrDefault(0L)
                    count += 1
                }
            }
            sizeCache[dir.absolutePath] = size
            countCache[dir.absolutePath] = count
            foldersScanned++
            if (foldersScanned % 20 == 0) emit(DuplicateFolderScanProgress.Scanning(foldersScanned, dir.absolutePath))
        }
        emit(DuplicateFolderScanProgress.Scanning(foldersScanned, root.absolutePath))

        // Phase 2: bucket non-empty, non-root directories by total size.
        val buckets = HashMap<Long, MutableList<File>>()
        for (dir in allDirs) {
            if (dir == root) continue
            val size = sizeCache[dir.absolutePath] ?: 0L
            val count = countCache[dir.absolutePath] ?: 0
            if (size <= 0L || count == 0) continue // empty folders aren't "duplicate content"
            buckets.getOrPut(size) { mutableListOf() }.add(dir)
        }
        val candidates = buckets.values.filter { it.size >= 2 }.flatten()
        val totalCandidates = candidates.size
        var compared = 0

        // Phase 3: deep signature, memoized so shared subtrees hash once.
        val signatureCache = HashMap<String, String>()
        fun signatureOf(dir: File): String {
            signatureCache[dir.absolutePath]?.let { return it }
            val children = runCatching { dir.listFiles() }.getOrNull()?.sortedBy { it.name } ?: emptyList()
            val parts = children.map { child ->
                val childHash = if (child.isDirectory) signatureOf(child)
                else runCatching { HashUtils.sha256Of(child) }.getOrDefault("unreadable:${child.name}")
                "${child.name}:$childHash"
            }
            val sig = HashUtils.sha256Of(parts.joinToString("|"))
            signatureCache[dir.absolutePath] = sig
            return sig
        }

        val bySizeThenSignature = HashMap<String, MutableList<File>>()
        for (bucket in buckets.values.filter { it.size >= 2 }) {
            for (dir in bucket) {
                currentCoroutineContext().ensureActive()
                val sig = runCatching { signatureOf(dir) }.getOrNull()
                compared++
                if (compared % 5 == 0) emit(DuplicateFolderScanProgress.Comparing(compared, totalCandidates))
                if (sig != null) bySizeThenSignature.getOrPut(sig) { mutableListOf() }.add(dir)
            }
        }

        val groups = bySizeThenSignature
            .filter { it.value.size >= 2 }
            .map { (sig, dirs) ->
                DuplicateFolderGroup(
                    signature = sig,
                    folders = dirs.map { d ->
                        FolderSnapshot(
                            file = d,
                            fileCount = countCache[d.absolutePath] ?: 0,
                            totalSizeBytes = sizeCache[d.absolutePath] ?: 0L
                        )
                    }
                )
            }
            .sortedByDescending { it.folders.first().totalSizeBytes }

        emit(DuplicateFolderScanProgress.Complete(groups))
    }.flowOn(Dispatchers.IO)
}
