package com.filemanager.app.util

import java.text.DecimalFormat
import java.util.Locale

object FormatUtils {

    private val units = arrayOf("B", "KB", "MB", "GB", "TB")

    fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
            .coerceIn(0, units.size - 1)
        val value = bytes / Math.pow(1024.0, digitGroups.toDouble())
        val pattern = if (digitGroups == 0) "#" else "#.#"
        return "${DecimalFormat(pattern).format(value)} ${units[digitGroups]}"
    }

    fun formatPercent(used: Long, total: Long): Int {
        if (total <= 0) return 0
        return ((used.toDouble() / total.toDouble()) * 100).toInt().coerceIn(0, 100)
    }

    fun formatDate(epochMillis: Long): String {
        val sdf = java.text.SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
        return sdf.format(java.util.Date(epochMillis))
    }
}
