package com.filemanager.app.ui.screens.files.components

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.filemanager.app.data.model.FileCategory
import com.filemanager.app.data.model.FileEntry
import com.filemanager.app.ui.theme.CategoryApps
import com.filemanager.app.ui.theme.CategoryArchives
import com.filemanager.app.ui.theme.CategoryAudio
import com.filemanager.app.ui.theme.CategoryDocuments
import com.filemanager.app.ui.theme.CategoryImages
import com.filemanager.app.ui.theme.CategoryOther
import com.filemanager.app.ui.theme.CategoryVideos
import com.filemanager.app.util.FormatUtils

fun iconFor(category: FileCategory): ImageVector = when (category) {
    FileCategory.FOLDER -> Icons.Filled.Folder
    FileCategory.IMAGE -> Icons.Filled.Image
    FileCategory.VIDEO -> Icons.Filled.Videocam
    FileCategory.AUDIO -> Icons.Filled.AudioFile
    FileCategory.DOCUMENT -> Icons.Filled.Description
    FileCategory.ARCHIVE -> Icons.Filled.Inventory2
    FileCategory.APK -> Icons.Filled.Android
    FileCategory.WEB -> Icons.Filled.Language
    FileCategory.OTHER -> Icons.Filled.InsertDriveFile
}

fun colorFor(category: FileCategory, fallback: Color): Color = when (category) {
    FileCategory.FOLDER -> fallback
    FileCategory.IMAGE -> CategoryImages
    FileCategory.VIDEO -> CategoryVideos
    FileCategory.AUDIO -> CategoryAudio
    FileCategory.DOCUMENT -> CategoryDocuments
    FileCategory.ARCHIVE -> CategoryArchives
    FileCategory.APK -> CategoryApps
    FileCategory.WEB -> CategoryDocuments
    FileCategory.OTHER -> CategoryOther
}

@Composable
fun FileListRow(
    entry: FileEntry,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = colorFor(entry.category, MaterialTheme.colorScheme.primary)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isSelectionMode) {
            Icon(
                imageVector = if (isSelected) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 12.dp)
            )
        }
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(accent.copy(alpha = 0.15f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(iconFor(entry.category), contentDescription = null, tint = accent)
        }
        Column(modifier = Modifier.padding(start = 14.dp).weight(1f)) {
            Text(entry.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                text = if (entry.isDirectory) FormatUtils.formatDate(entry.lastModified)
                else "${FormatUtils.formatBytes(entry.sizeBytes)} · ${FormatUtils.formatDate(entry.lastModified)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun FileGridItem(
    entry: FileEntry,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = colorFor(entry.category, MaterialTheme.colorScheme.primary)
    Card(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) accent.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .background(accent.copy(alpha = 0.15f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(iconFor(entry.category), contentDescription = null, tint = accent)
            }
            Text(
                entry.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            if (!entry.isDirectory) {
                Text(
                    FormatUtils.formatBytes(entry.sizeBytes),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
