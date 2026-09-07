package com.filemanager.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.ui.graphics.vector.ImageVector
import com.filemanager.app.R

/** The four bottom-nav tabs, per spec section 22: Home | Files | Cleaner | Vault. */
sealed class BottomNavDestination(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    data object Home : BottomNavDestination(
        route = "home",
        labelRes = R.string.nav_home,
        selectedIcon = Icons.Filled.Home,
        unselectedIcon = Icons.Outlined.Home
    )

    data object Files : BottomNavDestination(
        route = "files",
        labelRes = R.string.nav_files,
        selectedIcon = Icons.Filled.Folder,
        unselectedIcon = Icons.Outlined.Folder
    )

    data object Cleaner : BottomNavDestination(
        route = "cleaner",
        labelRes = R.string.nav_cleaner,
        selectedIcon = Icons.Filled.CleaningServices,
        unselectedIcon = Icons.Outlined.CleaningServices
    )

    data object Vault : BottomNavDestination(
        route = "vault",
        labelRes = R.string.nav_vault,
        selectedIcon = Icons.Filled.Lock,
        unselectedIcon = Icons.Outlined.Lock
    )

    companion object {
        val all = listOf(Home, Files, Cleaner, Vault)
    }
}

/** Secondary (non-tab) destinations pushed on top of the graph. */
object Routes {
    const val CATEGORY_DETAIL = "category/{categoryName}"
    fun categoryDetail(categoryName: String) = "category/$categoryName"

    const val FILE_BROWSER = "browser/{encodedPath}"
    fun fileBrowser(encodedPath: String) = "browser/$encodedPath"

    const val DUPLICATE_SCANNER = "scanner/duplicates"
    const val DUPLICATE_FOLDER_SCANNER = "scanner/duplicate_folders"
    const val EMPTY_FOLDER_SCANNER = "scanner/empty_folders"
    const val LARGE_FILE_SCANNER = "scanner/large_files"
    const val JUNK_SCANNER = "scanner/junk"
    const val RECYCLE_BIN = "recycle_bin"
    const val SETTINGS = "settings"
}
