package com.filemanager.app.data.scanner

import com.filemanager.app.data.model.FileEntry

enum class LargeFileThreshold(val bytes: Long, val label: String) {
    MB_50(50L * 1024 * 1024, "50 MB"),
    MB_100(100L * 1024 * 1024, "100 MB"),
    MB_250(250L * 1024 * 1024, "250 MB"),
    MB_500(500L * 1024 * 1024, "500 MB"),
    GB_1(1024L * 1024 * 1024, "1 GB")
}

sealed class LargeFileScanProgress {
    data class Scanning(val filesScanned: Int, val currentPath: String) : LargeFileScanProgress()
    data class Complete(val results: List<FileEntry>) : LargeFileScanProgress()
}
