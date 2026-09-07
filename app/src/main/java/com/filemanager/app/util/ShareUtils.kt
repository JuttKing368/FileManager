package com.filemanager.app.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.filemanager.app.data.model.FileCategoryClassifier
import java.io.File

object ShareUtils {

    fun shareIntentFor(context: Context, file: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val mimeType = FileCategoryClassifier.mimeTypeFor(file.name) ?: "*/*"
        return Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun openIntentFor(context: Context, file: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val mimeType = FileCategoryClassifier.mimeTypeFor(file.name) ?: "*/*"
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
