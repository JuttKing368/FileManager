package com.filemanager.app.data.model

/**
 * Lightweight, immutable representation of a filesystem entry.
 * Kept separate from java.io.File so it survives across coroutine/background
 * boundaries safely and can be cached (e.g. in Room) without touching disk.
 */
data class FileEntry(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val lastModified: Long,
    val mimeType: String?,
    val category: FileCategory
)

enum class FileCategory {
    IMAGE,
    VIDEO,
    AUDIO,
    DOCUMENT,
    ARCHIVE,
    APK,
    WEB,
    OTHER,
    FOLDER
}

/**
 * Central place mapping file extensions -> category.
 * Every scanner (categories, search, junk classifier, large files) reads from
 * this single source of truth instead of re-implementing extension lists.
 */
object FileCategoryClassifier {

    private val imageExt = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif", "svg")
    private val videoExt = setOf("mp4", "mkv", "avi", "mov", "3gp", "webm", "flv", "m4v")
    private val audioExt = setOf("mp3", "wav", "aac", "flac", "ogg", "m4a", "wma")
    private val documentExt = setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "rtf", "odt")
    private val archiveExt = setOf("zip", "rar", "7z", "tar", "gz", "bz2", "xz")
    private val apkExt = setOf("apk")
    private val webExt = setOf("html", "htm", "css", "js", "json", "xml")

    fun classify(fileName: String, isDirectory: Boolean): FileCategory {
        if (isDirectory) return FileCategory.FOLDER
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return when (ext) {
            in imageExt -> FileCategory.IMAGE
            in videoExt -> FileCategory.VIDEO
            in audioExt -> FileCategory.AUDIO
            in documentExt -> FileCategory.DOCUMENT
            in archiveExt -> FileCategory.ARCHIVE
            in apkExt -> FileCategory.APK
            in webExt -> FileCategory.WEB
            else -> FileCategory.OTHER
        }
    }

    fun mimeTypeFor(fileName: String): String? {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
    }
}
