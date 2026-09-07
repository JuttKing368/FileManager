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
 * Detects duplicate files using the process described in spec §6:
 * 1. Walk the tree, bucket files by size (free — just a stat, no I/O read).
 * 2. Only size buckets with 2+ files are candidates for hashing — this is
 *    the key performance win, since hashing every file on a large device
 *    would be far too slow (spec §24: "filter by file size before
 *    calculating hashes to reduce CPU usage").
 * 3. Hash each candidate (SHA-256, streamed — never loads a whole file
 *    into memory) and group by hash within its size bucket.
 * 4. Any hash group with 2+ files is a confirmed duplicate group.
 *
 * Never deletes anything itself — it only reports groups for the UI to
 * present for manual review, per spec §6 ("Do not automatically delete
 * duplicates").
 */
@Singleton
class DuplicateFileScanner @Inject constructor() {

    fun scan(root: File): Flow<DuplicateScanProgress> = flow {
        val sizeBuckets = HashMap<Long, MutableList<File>>()
        val stack = ArrayDeque<File>()
        stack.addLast(root)
        var filesScanned = 0

        // Phase 1: cheap traversal, bucket by size only.
        while (stack.isNotEmpty()) {
            currentCoroutineContext().ensureActive() // lets scan cancellation (Cancel button) take effect promptly
            val current = stack.removeLast()
            val children = runCatching { current.listFiles() }.getOrNull() ?: continue
            for (child in children) {
                if (child.isDirectory) {
                    stack.addLast(child)
                    continue
                }
                val size = runCatching { child.length() }.getOrNull() ?: continue
                if (size <= 0L) continue // zero-byte files aren't meaningful "duplicates" here
                sizeBuckets.getOrPut(size) { mutableListOf() }.add(child)
                filesScanned++
                if (filesScanned % 50 == 0) {
                    emit(DuplicateScanProgress.Scanning(filesScanned, current.absolutePath))
                }
            }
        }
        emit(DuplicateScanProgress.Scanning(filesScanned, root.absolutePath))

        // Phase 2: only hash within size buckets that have 2+ files.
        val candidates = sizeBuckets.values.filter { it.size >= 2 }
        val totalCandidates = candidates.sumOf { it.size }
        var hashed = 0
        val groups = mutableListOf<DuplicateGroup>()

        for (bucket in candidates) {
            currentCoroutineContext().ensureActive()
            val bySizeThenHash = HashMap<String, MutableList<File>>()
            for (file in bucket) {
                currentCoroutineContext().ensureActive()
                val hash = runCatching { HashUtils.sha256Of(file) }.getOrNull()
                hashed++
                if (hashed % 10 == 0) emit(DuplicateScanProgress.Hashing(hashed, totalCandidates))
                if (hash != null) {
                    bySizeThenHash.getOrPut(hash) { mutableListOf() }.add(file)
                }
                // Files that fail to hash (permission error, deleted mid-scan) are
                // silently skipped — never crash the whole scan for one bad file.
            }
            bySizeThenHash.filter { it.value.size >= 2 }.forEach { (hash, matched) ->
                groups.add(DuplicateGroup(hash = hash, sizeBytes = matched.first().length(), files = matched))
            }
        }

        emit(DuplicateScanProgress.Complete(groups.sortedByDescending { it.recoverableBytes }))
    }.flowOn(Dispatchers.IO)
}
