package com.filemanager.app.data.scanner

import java.io.File

enum class JunkCategory(val displayName: String) {
    TEMP_FILE("Temporary files"),
    INCOMPLETE_DOWNLOAD("Incomplete downloads"),
    EMPTY_FILE("Empty files"),
    LEFTOVER_APK("Leftover installers"),
    THUMBNAIL_CACHE("Thumbnail cache"),
    APP_CACHE("App cache")
}

data class JunkItem(
    val file: File,
    val category: JunkCategory,
    val sizeBytes: Long,
    val reason: String
)

sealed class JunkScanProgress {
    data class Scanning(val filesScanned: Int, val currentPath: String) : JunkScanProgress()
    data class Complete(val items: List<JunkItem>) : JunkScanProgress()
}
