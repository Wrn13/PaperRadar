package com.example.relevantreasearchupdates.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.NavType
import com.example.relevantreasearchupdates.ui.feed.FeedScreen
import com.example.relevantreasearchupdates.ui.paperdetail.PaperDetailScreen
import com.example.relevantreasearchupdates.ui.pdfviewer.PdfViewerScreen
import com.example.relevantreasearchupdates.ui.settings.SettingsScreen
import com.example.relevantreasearchupdates.ui.watches.WatchesScreen

private sealed class TopLevelDestination(val route: String, val label: String) {
    data object Feed : TopLevelDestination("feed", "Feed")
    data object Watches : TopLevelDestination("watches", "Watches")
    data object Settings : TopLevelDestination("settings", "Settings")
}

private val topLevelDestinations = listOf(
    TopLevelDestination.Feed,
    TopLevelDestination.Watches,
    TopLevelDestination.Settings
)

@Composable
fun RraNavHost() {
    val navController = rememberNavController()

    Scaffold(
        bottomBar = {
            val backStackEntry by navController.currentBackStackEntryAsState()
            val currentDestination = backStackEntry?.destination
            val isTopLevel = topLevelDestinations.any { dest ->
                currentDestination?.hierarchy?.any { it.route == dest.route } == true
            }
            if (isTopLevel) {
                NavigationBar {
                    topLevelDestinations.forEach { dest ->
                        val selected = currentDestination?.hierarchy?.any { it.route == dest.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(dest.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                val icon = when (dest) {
                                    TopLevelDestination.Feed -> Icons.AutoMirrored.Filled.Article
                                    TopLevelDestination.Watches -> Icons.Filled.Bookmark
                                    TopLevelDestination.Settings -> Icons.Filled.Settings
                                }
                                Icon(icon, contentDescription = dest.label)
                            },
                            label = { Text(dest.label) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = TopLevelDestination.Feed.route,
            modifier = Modifier.padding(padding)
        ) {
            composable(TopLevelDestination.Feed.route) {
                FeedScreen(onOpenPaper = { paperId -> navController.navigate("paper/$paperId") })
            }
            composable(TopLevelDestination.Watches.route) {
                WatchesScreen()
            }
            composable(TopLevelDestination.Settings.route) {
                SettingsScreen()
            }
            composable(
                route = "paper/{paperId}",
                arguments = listOf(navArgument("paperId") { type = NavType.LongType })
            ) { backStackEntry ->
                val paperId = backStackEntry.arguments?.getLong("paperId") ?: return@composable
                PaperDetailScreen(
                    paperId = paperId,
                    onReadPdf = { id -> navController.navigate("pdf/$id") }
                )
            }
            composable(
                route = "pdf/{paperId}",
                arguments = listOf(navArgument("paperId") { type = NavType.LongType })
            ) { backStackEntry ->
                val paperId = backStackEntry.arguments?.getLong("paperId") ?: return@composable
                PdfViewerScreen(paperId = paperId)
            }
        }
    }
}
