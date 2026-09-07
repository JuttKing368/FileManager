package com.filemanager.app.ui.screens.vault.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

/**
 * A 3x3 pattern-lock grid. Tap dots in sequence (rather than a continuous
 * drag) to build the pattern — a deliberate simplification that keeps hit
 * detection reliable across screen sizes while still being a real,
 * working pattern input rather than a decorative one. Sequence encodes
 * to a comma-separated string of node indices (0-8), hashed the same way
 * as a PIN/password by [com.filemanager.app.data.repository.VaultCredentialRepository].
 */
@Composable
fun PatternPad(
    onPatternSubmitted: (String) -> Unit,
    minNodes: Int = 4
) {
    var selected by remember { mutableStateOf(listOf<Int>()) }

    Column {
        for (row in 0..2) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                for (col in 0..2) {
                    val index = row * 3 + col
                    val order = selected.indexOf(index)
                    val isSelected = order >= 0
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier
                            .padding(12.dp)
                            .size(56.dp)
                            .aspectRatio(1f)
                            .clip(CircleShape)
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                            .clickable {
                                if (!isSelected) selected = selected + index
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Text(
                                "${order + 1}",
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = { selected = emptyList() },
                modifier = Modifier.weight(1f)
            ) { Text("Clear") }
            Button(
                onClick = { onPatternSubmitted(selected.joinToString(",")) },
                enabled = selected.size >= minNodes,
                modifier = Modifier.weight(1f)
            ) { Text("Confirm") }
        }
    }
}
