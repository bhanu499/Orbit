package com.orbit.assistant.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.orbit.assistant.ui.activity.ActivityScreen
import com.orbit.assistant.ui.activity.TaskDetailScreen
import com.orbit.assistant.ui.agent.AgentScreen
import com.orbit.assistant.ui.settings.SettingsScreen
import com.orbit.assistant.ui.theme.OrbitTheme

sealed class OrbitDestination(
    val route: String,
    val label: String,
    val icon: ImageVector
) {
    data object Agent : OrbitDestination("agent", "Agent", Icons.Rounded.AutoAwesome)
    data object Activity : OrbitDestination("activity", "Activity", Icons.Rounded.History)
    data object Settings : OrbitDestination("settings", "Settings", Icons.Rounded.Settings)
}

private val bottomDestinations = listOf(
    OrbitDestination.Agent,
    OrbitDestination.Activity,
    OrbitDestination.Settings
)

@Composable
fun OrbitAppScaffold() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val colors = OrbitTheme.colors

    val showBottomBar = bottomDestinations.any { dest ->
        currentDestination?.hierarchy?.any { it.route == dest.route } == true
    }

    Scaffold(
        containerColor = colors.background,
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    containerColor = colors.surface,
                    contentColor = colors.textPrimary
                ) {
                    bottomDestinations.forEach { dest ->
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
                            icon = { Icon(dest.icon, contentDescription = dest.label) },
                            label = { Text(dest.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = colors.accentPrimary,
                                selectedTextColor = colors.accentPrimary,
                                indicatorColor = colors.accentContainer,
                                unselectedIconColor = colors.textTertiary,
                                unselectedTextColor = colors.textTertiary
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = OrbitDestination.Agent.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(OrbitDestination.Agent.route) {
                AgentScreen(
                    onOpenSettings = {
                        navController.navigate(OrbitDestination.Settings.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
            composable(OrbitDestination.Activity.route) {
                ActivityScreen(
                    onTaskClick = { taskId ->
                        navController.navigate("task_detail/$taskId")
                    }
                )
            }
            composable(OrbitDestination.Settings.route) {
                SettingsScreen()
            }
            composable(
                route = "task_detail/{taskId}",
                arguments = listOf(navArgument("taskId") { type = NavType.StringType })
            ) { entry ->
                val taskId = entry.arguments?.getString("taskId") ?: return@composable
                TaskDetailScreen(
                    taskId = taskId,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
