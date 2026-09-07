package com.filemanager.app.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.ContextCompat
import android.Manifest

/**
 * Centralizes the "does this app currently have the storage access it
 * needs" check across Android versions, and builds the correct system
 * intent to request it — so callers never sprinkle SDK_INT checks
 * throughout the UI layer.
 */
object PermissionUtils {

    fun hasFullStorageAccess(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
    }

    /** Legacy runtime permissions to request on API < 30 (handled via ActivityResult contracts). */
    fun legacyStoragePermissions(): Array<String> = arrayOf(
        Manifest.permission.READ_EXTERNAL_STORAGE,
        Manifest.permission.WRITE_EXTERNAL_STORAGE
    )

    /** Media permissions to request on API 33+ as a fallback if All Files Access is declined. */
    fun mediaPermissions(): Array<String> = arrayOf(
        Manifest.permission.READ_MEDIA_IMAGES,
        Manifest.permission.READ_MEDIA_VIDEO,
        Manifest.permission.READ_MEDIA_AUDIO
    )

    /** Builds the system settings intent for granting "All files access" (API 30+). */
    fun manageAllFilesIntent(context: Context): Intent {
        return Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
            data = Uri.parse("package:${context.packageName}")
        }
    }
}
