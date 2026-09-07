package com.filemanager.app.data.scanner

import java.io.File

data class DuplicateGroup(
    val hash: String,
    val sizeBytes: Long,
    val files: List<File>
) {
    /** Oldest file by last-modified, shown as the likely "original" per spec §6. */
    val oldestFile: File get() = files.minBy { it.lastModified() }
    val recoverableBytes: Long get() = sizeBytes * (files.size - 1)
}

sealed class DuplicateScanProgress {
    data class Scanning(val filesScanned: Int, val currentPath: String) : DuplicateScanProgress()
    data class Hashing(val candidatesHashed: Int, val totalCandidates: Int) : DuplicateScanProgress()
    data class Complete(val groups: List<DuplicateGroup>) : DuplicateScanProgress()
    data class Error(val message: String) : DuplicateScanProgress()
}
