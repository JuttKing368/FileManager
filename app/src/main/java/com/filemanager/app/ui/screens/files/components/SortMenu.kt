package com.filemanager.app.ui.screens.files.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.filemanager.app.data.model.SortDirection
import com.filemanager.app.data.model.SortField
import com.filemanager.app.data.model.SortOption

private val fieldLabels = mapOf(
    SortField.NAME to "Name",
    SortField.SIZE to "Size",
    SortField.DATE_MODIFIED to "Date modified",
    SortField.TYPE to "Type"
)

@Composable
fun SortMenu(
    expanded: Boolean,
    current: SortOption,
    onDismiss: () -> Unit,
    onSelect: (SortOption) -> Unit
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        fieldLabels.forEach { (field, label) ->
            val isCurrentField = current.field == field
            DropdownMenuItem(
                text = { Text(label) },
                leadingIcon = { if (isCurrentField) Icon(Icons.Filled.Check, contentDescription = null) },
                onClick = {
                    val newDirection = if (isCurrentField) {
                        if (current.direction == SortDirection.ASCENDING) SortDirection.DESCENDING else SortDirection.ASCENDING
                    } else SortDirection.ASCENDING
                    onSelect(SortOption(field, newDirection))
                    onDismiss()
                }
            )
        }
    }
}
