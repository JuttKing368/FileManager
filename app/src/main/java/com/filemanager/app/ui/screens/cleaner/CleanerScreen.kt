package com.filemanager.app.ui.screens.cleaner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.filemanager.app.ui.components.FeatureCard
import com.filemanager.app.ui.navigation.Routes
import com.filemanager.app.ui.theme.CategoryApps
import com.filemanager.app.ui.theme.CategoryArchives
import com.filemanager.app.ui.theme.CategoryAudio
import com.filemanager.app.ui.theme.CategoryVideos

private data class CleanerTool(
    val title: String,
    val subtitle: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val color: androidx.compose.ui.graphics.Color,
    val route: String?
)

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun CleanerScreen(navController: NavHostController) {
    val tools = listOf(
        CleanerTool("Duplicate Files", "Find and remove duplicate files", Icons.Filled.ContentCopy, MaterialTheme.colorScheme.primary, Routes.DUPLICATE_SCANNER),
        CleanerTool("Duplicate Folders", "Find and remove duplicate folders", Icons.Filled.Folder, CategoryArchives, Routes.DUPLICATE_FOLDER_SCANNER),
        CleanerTool("Empty Folders", "Find and remove empty folders", Icons.Filled.FolderOff, CategoryApps, Routes.EMPTY_FOLDER_SCANNER),
        CleanerTool("Large Files", "Find and manage large files", Icons.Filled.Storage, MaterialTheme.colorScheme.secondary, Routes.LARGE_FILE_SCANNER),
        CleanerTool("Junk Cleaner", "Free up space safely", Icons.Filled.CleaningServices, CategoryAudio, Routes.JUNK_SCANNER),
        CleanerTool("Recycle Bin", "Restore or permanently delete", Icons.Filled.Restore, CategoryVideos, Routes.RECYCLE_BIN),
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cleaner") },
                actions = {
                    IconButton(onClick = { navController.navigate(Routes.SETTINGS) }) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(tools) { tool ->
                FeatureCard(
                    title = tool.title,
                    subtitle = tool.subtitle,
                    icon = tool.icon,
                    accentColor = tool.color,
                    onClick = { tool.route?.let { navController.navigate(it) } }
                )
            }
        }
    }
}
