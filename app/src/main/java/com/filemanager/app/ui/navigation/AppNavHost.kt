package com.filemanager.app.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.filemanager.app.ui.screens.cleaner.CleanerScreen
import com.filemanager.app.ui.screens.cleaner.duplicates.DuplicateScannerScreen
import com.filemanager.app.ui.screens.cleaner.duplicatefolders.DuplicateFolderScreen
import com.filemanager.app.ui.screens.cleaner.emptyfolders.EmptyFolderScreen
import com.filemanager.app.ui.screens.cleaner.junk.JunkScreen
import com.filemanager.app.ui.screens.cleaner.largefiles.LargeFileScreen
import com.filemanager.app.ui.screens.recyclebin.RecycleBinScreen
import com.filemanager.app.ui.screens.settings.SettingsScreen
import com.filemanager.app.ui.screens.files.CategoryDetailScreen
import com.filemanager.app.ui.screens.files.FilesScreen
import com.filemanager.app.ui.screens.home.HomeScreen
import com.filemanager.app.ui.screens.vault.VaultScreen

@Composable
fun AppNavHost() {
    val navController = rememberNavController()

    Scaffold(
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                BottomNavDestination.all.forEach { destination ->
                    val selected = currentRoute == destination.route
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = if (selected) destination.selectedIcon else destination.unselectedIcon,
                                contentDescription = stringResource(destination.labelRes)
                            )
                        },
                        label = { Text(stringResource(destination.labelRes)) }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = BottomNavDestination.Home.route,
            modifier = androidx.compose.ui.Modifier.padding(innerPadding)
        ) {
            composable(BottomNavDestination.Home.route) {
                HomeScreen(navController = navController)
            }
            composable(BottomNavDestination.Files.route) {
                FilesScreen(navController = navController)
            }
            composable(BottomNavDestination.Cleaner.route) {
                CleanerScreen(navController = navController)
            }
            composable(BottomNavDestination.Vault.route) {
                VaultScreen(navController = navController)
            }
            composable(
                route = Routes.CATEGORY_DETAIL,
                arguments = listOf(navArgument("categoryName") { type = NavType.StringType })
            ) {
                CategoryDetailScreen(navController = navController)
            }
            composable(Routes.DUPLICATE_SCANNER) {
                DuplicateScannerScreen(navController = navController)
            }
            composable(Routes.DUPLICATE_FOLDER_SCANNER) {
                DuplicateFolderScreen(navController = navController)
            }
            composable(Routes.EMPTY_FOLDER_SCANNER) {
                EmptyFolderScreen(navController = navController)
            }
            composable(Routes.LARGE_FILE_SCANNER) {
                LargeFileScreen(navController = navController)
            }
            composable(Routes.RECYCLE_BIN) {
                RecycleBinScreen(navController = navController)
            }
            composable(Routes.JUNK_SCANNER) {
                JunkScreen(navController = navController)
            }
            composable(Routes.SETTINGS) {
                SettingsScreen()
            }
        }
    }
}
