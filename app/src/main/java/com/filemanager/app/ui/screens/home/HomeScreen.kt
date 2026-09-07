package com.filemanager.app.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.filemanager.app.data.model.FileCategory
import com.filemanager.app.ui.components.CategoryCard
import com.filemanager.app.ui.components.FeatureCard
import com.filemanager.app.ui.components.StorageRing
import com.filemanager.app.ui.navigation.Routes
import com.filemanager.app.ui.theme.CategoryApps
import com.filemanager.app.ui.theme.CategoryArchives
import com.filemanager.app.ui.theme.CategoryAudio
import com.filemanager.app.ui.theme.CategoryDocuments
import com.filemanager.app.ui.theme.CategoryImages
import com.filemanager.app.ui.theme.CategoryOther
import com.filemanager.app.ui.theme.CategoryVideos
import com.filemanager.app.util.FormatUtils

private data class CategoryUiItem(
    val category: FileCategory,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val color: androidx.compose.ui.graphics.Color
)

private val categoryDisplayOrder = listOf(
    CategoryUiItem(FileCategory.IMAGE, "Images", Icons.Filled.Image, CategoryImages),
    CategoryUiItem(FileCategory.VIDEO, "Videos", Icons.Filled.Videocam, CategoryVideos),
    CategoryUiItem(FileCategory.AUDIO, "Audio", Icons.Filled.AudioFile, CategoryAudio),
    CategoryUiItem(FileCategory.DOCUMENT, "Documents", Icons.Filled.Description, CategoryDocuments),
    CategoryUiItem(FileCategory.ARCHIVE, "Archives", Icons.Filled.Inventory2, CategoryArchives),
    CategoryUiItem(FileCategory.APK, "Apps", Icons.Filled.Android, CategoryApps),
    CategoryUiItem(FileCategory.OTHER, "Other", Icons.Filled.MoreHoriz, CategoryOther),
)

@Composable
fun HomeScreen(
    navController: NavHostController,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            Text("Storage Overview", style = MaterialTheme.typography.headlineSmall)
        }

        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                StorageRing(
                    percentUsed = FormatUtils.formatPercent(state.overview.usedBytes, state.overview.totalBytes)
                )
                Text(
                    text = "${FormatUtils.formatBytes(state.overview.usedBytes)} / ${FormatUtils.formatBytes(state.overview.totalBytes)} used",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 12.dp)
                )
                Text(
                    text = "${FormatUtils.formatBytes(state.overview.freeBytes)} free",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            Text("Categories", style = MaterialTheme.typography.headlineSmall)
        }

        item {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.height(280.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(categoryDisplayOrder) { item ->
                    CategoryCard(
                        label = item.label,
                        sizeLabel = FormatUtils.formatBytes(state.categoryBreakdown[item.category] ?: 0L),
                        icon = item.icon,
                        accentColor = item.color,
                        onClick = {
                            navController.navigate(Routes.categoryDetail(item.category.name))
                        }
                    )
                }
            }
        }

        item {
            Text("Tools", style = MaterialTheme.typography.headlineSmall)
        }

        item {
            FeatureCard(
                title = "Duplicate Files",
                subtitle = state.duplicateFilesRecoverableBytes
                    ?.let { "${FormatUtils.formatBytes(it)} can be recovered" }
                    ?: "Scan for duplicate files",
                icon = Icons.Filled.ContentCopy,
                accentColor = MaterialTheme.colorScheme.primary,
                onClick = { navController.navigate(Routes.DUPLICATE_SCANNER) }
            )
        }
        item {
            FeatureCard(
                title = "Large Files",
                subtitle = state.largeFilesCount?.let { "$it files found" } ?: "Find space-hogging files",
                icon = Icons.Filled.Storage,
                accentColor = MaterialTheme.colorScheme.secondary,
                onClick = { navController.navigate(Routes.LARGE_FILE_SCANNER) }
            )
        }
        item {
            FeatureCard(
                title = "Empty Folders",
                subtitle = state.emptyFoldersCount?.let { "$it empty folders" } ?: "Clean up empty folders",
                icon = Icons.Filled.FolderOff,
                accentColor = CategoryArchives,
                onClick = { navController.navigate(Routes.EMPTY_FOLDER_SCANNER) }
            )
        }
        item {
            FeatureCard(
                title = "Junk Cleaner",
                subtitle = state.junkBytes?.let { "${FormatUtils.formatBytes(it)} removable" } ?: "Free up space",
                icon = Icons.Filled.CleaningServices,
                accentColor = CategoryAudio,
                onClick = { navController.navigate(Routes.JUNK_SCANNER) }
            )
        }
        item {
            FeatureCard(
                title = "Private Vault",
                subtitle = if (state.vaultLocked) "Locked" else "Unlocked",
                icon = Icons.Filled.Lock,
                accentColor = CategoryVideos,
                onClick = { /* navigates via bottom nav Vault tab */ }
            )
        }
        item {
            FeatureCard(
                title = "SD Card",
                subtitle = "Not available",
                icon = Icons.Filled.SdStorage,
                accentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                onClick = { }
            )
        }
    }
}
