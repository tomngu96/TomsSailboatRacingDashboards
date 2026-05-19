package com.sailboatracing.ui.navigation

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.sailboatracing.ui.screens.ChartsScreen
import com.sailboatracing.ui.screens.CourseScreen
import com.sailboatracing.ui.screens.DashboardScreen
import com.sailboatracing.ui.screens.OfflineMapsScreen
import com.sailboatracing.ui.screens.SessionsScreen
import com.sailboatracing.ui.screens.SettingsScreen
import com.sailboatracing.ui.screens.TimerSetupScreen
import com.sailboatracing.ui.theme.PrimaryColor
import com.sailboatracing.ui.theme.SurfaceColor
import com.sailboatracing.viewmodel.RaceViewModel

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object Dashboard : Screen("dashboard", "Dashboard", Icons.Filled.Dashboard)
    object Timer : Screen("timer", "Timer", Icons.Filled.Timer)
    object Course : Screen("course", "Course", Icons.Filled.Route)
    object Charts : Screen("charts", "Charts", Icons.Filled.Analytics)
    object Settings : Screen("settings", "Settings", Icons.Filled.Settings)
}

private val bottomNavItems = listOf(
    Screen.Dashboard,
    Screen.Timer,
    Screen.Course,
    Screen.Charts,
    Screen.Settings
)

@Composable
fun NavGraph(viewModel: RaceViewModel) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    Scaffold(
        containerColor = Color(0xFF0A0A0F),
        bottomBar = {
            val showBottomBar = currentDestination?.route !in setOf("offline_maps", "sessions")
            if (!showBottomBar) return@Scaffold
            NavigationBar(
                containerColor = SurfaceColor,
                modifier = Modifier.height(48.dp),
                tonalElevation = 0.dp
            ) {
                bottomNavItems.forEach { screen ->
                    val selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true
                    NavigationBarItem(
                        icon = {
                            Icon(
                                imageVector = screen.icon,
                                contentDescription = screen.label,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        label = {
                            Text(
                                text = screen.label,
                                fontSize = 9.sp,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.offset(y = (-6).dp)
                            )
                        },
                        selected = selected,
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = PrimaryColor,
                            selectedTextColor = PrimaryColor,
                            unselectedIconColor = Color(0xFF888888),
                            unselectedTextColor = Color(0xFF888888),
                            indicatorColor = Color(0xFF1A1A2E)
                        ),
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Dashboard.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Dashboard.route) {
                DashboardScreen(viewModel = viewModel)
            }
            composable(Screen.Timer.route) {
                TimerSetupScreen(viewModel = viewModel)
            }
            composable(Screen.Course.route) {
                CourseScreen(viewModel = viewModel)
            }
            composable(Screen.Charts.route) {
                ChartsScreen(viewModel = viewModel)
            }
            composable(Screen.Settings.route) {
                SettingsScreen(
                    viewModel = viewModel,
                    onOpenOfflineMaps = { navController.navigate("offline_maps") },
                    onOpenSessions  = { navController.navigate("sessions") }
                )
            }
            composable("offline_maps") {
                OfflineMapsScreen(viewModel = viewModel, onBack = {
                    navController.popBackStack()
                })
            }
            composable("sessions") {
                SessionsScreen(viewModel = viewModel, onBack = {
                    navController.popBackStack()
                })
            }
        }
    }
}
