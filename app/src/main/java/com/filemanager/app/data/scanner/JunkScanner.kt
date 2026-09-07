package com.filemanager.app.data.scanner

import android.content.Context
import android.content.pm.PackageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

private val tempExtensions = setOf("tmp", "temp", "log", "bak", "old")
private val incompleteDownloadExtensions = setOf("crdownload", "part", "download", "partial")

/**
 * Finds safely-removable junk (spec §14, §15). Deliberately scoped to
 * what a third-party app can actually see and is confident is disposable
 * — per spec §28, this never pretends to reach another app's private
 * cache, which Android has walled off from other apps since Android 11
 * (not even "All Files Access" reaches another app's Android/data).
 * What's real and covered here:
 *   - Temp/log/backup files by extension.
 *   - Incomplete/interrupted downloads (browser partial-download extensions).
 *   - Zero-byte files (corrupted/incomplete writes).
 *   - APK installers in Download whose app is already installed — checked
 *     against PackageManager, not guessed.
 *   - Legacy .thumbnails cache folders.
 *   - This app's own cache directories (cacheDir / externalCacheDir),
 *     which are unambiguously safe to clear.
 * Never touches anything not on this list — no guessing at "probably junk".
 */
@Singleton
class JunkScanner @Inject constructor(
    @ApplicationContext private val context: Context
) {

    fun scan(root: File): Flow<JunkScanProgress> = flow {
        val results = mutableListOf<JunkItem>()
        val pm = context.packageManager
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
                classify(child, pm)?.let { results.add(it) }
                if (filesScanned % 100 == 0) emit(JunkScanProgress.Scanning(filesScanned, current.absolutePath))
            }
        }

        // This app's own cache — always unambiguously safe to remove.
        listOfNotNull(context.cacheDir, context.externalCacheDir).forEach { cacheRoot ->
            collectAppCache(cacheRoot, results)
        }

        emit(JunkScanProgress.Complete(results))
    }.flowOn(Dispatchers.IO)

    private fun collectAppCache(dir: File, into: MutableList<JunkItem>) {
        val stack = ArrayDeque<File>()
        stack.addLast(dir)
        while (stack.isNotEmpty()) {
            val current = stack.removeLast()
            val children = runCatching { current.listFiles() }.getOrNull() ?: continue
            for (child in children) {
                if (child.isDirectory) stack.addLast(child)
                else into.add(JunkItem(child, JunkCategory.APP_CACHE, child.length(), "This app's cache"))
            }
        }
    }

    private fun classify(file: File, pm: PackageManager): JunkItem? {
        val path = file.absolutePath
        val ext = file.name.substringAfterLast('.', "").lowercase()
        val size = runCatching { file.length() }.getOrDefault(0L)

        return when {
            path.contains("/.thumbnails/") ->
                JunkItem(file, JunkCategory.THUMBNAIL_CACHE, size, "Cached image thumbnail")

            ext in tempExtensions ->
                JunkItem(file, JunkCategory.TEMP_FILE, size, "Temporary file (.$ext)")

            ext in incompleteDownloadExtensions ->
                JunkItem(file, JunkCategory.INCOMPLETE_DOWNLOAD, size, "Interrupted download")

            ext == "apk" && isAlreadyInstalled(file, pm) ->
                JunkItem(file, JunkCategory.LEFTOVER_APK, size, "App is already installed - installer no longer needed")

            size == 0L ->
                JunkItem(file, JunkCategory.EMPTY_FILE, 0L, "Empty file (0 bytes)")

            else -> null
        }
    }

    /** Only true when PackageManager confirms the exact package from this APK is currently installed — never guessed. */
    private fun isAlreadyInstalled(apkFile: File, pm: PackageManager): Boolean {
        return runCatching {
            @Suppress("DEPRECATION")
            val archiveInfo = pm.getPackageArchiveInfo(apkFile.absolutePath, 0) ?: return false
            val packageName = archiveInfo.packageName
            pm.getPackageInfo(packageName, 0) // throws if not installed
            true
        }.getOrDefault(false)
    }
}
