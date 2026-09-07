package com.filemanager.app.data.scanner

import java.io.File

data class EmptyFolderEntry(
    val file: File,
    val lastModified: Long
)

sealed class EmptyFolderScanProgress {
    data class Scanning(val foldersScanned: Int, val currentPath: String) : EmptyFolderScanProgress()
    data class Complete(val results: List<EmptyFolderEntry>) : EmptyFolderScanProgress()
}
