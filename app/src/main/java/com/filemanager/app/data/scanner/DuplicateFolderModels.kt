package com.filemanager.app.data.scanner

import java.io.File

data class DuplicateFolderGroup(
    val signature: String,
    val folders: List<FolderSnapshot>
)

data class FolderSnapshot(
    val file: File,
    val fileCount: Int,
    val totalSizeBytes: Long
)

sealed class DuplicateFolderScanProgress {
    data class Scanning(val foldersScanned: Int, val currentPath: String) : DuplicateFolderScanProgress()
    data class Comparing(val comparedCount: Int, val totalCandidates: Int) : DuplicateFolderScanProgress()
    data class Complete(val groups: List<DuplicateFolderGroup>) : DuplicateFolderScanProgress()
}
