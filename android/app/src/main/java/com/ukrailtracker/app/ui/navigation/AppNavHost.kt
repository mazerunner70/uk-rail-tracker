package com.ukrailtracker.app.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ukrailtracker.app.R
import com.ukrailtracker.app.ui.nearby.NearbyRoute
import com.ukrailtracker.app.ui.station.StationDetailRoute
import com.ukrailtracker.app.ui.theme.NeonBackground
import com.ukrailtracker.app.ui.theme.NeonCyan
import com.ukrailtracker.app.ui.theme.NeonMuted
import com.ukrailtracker.app.ui.theme.NeonSurface

object Routes {
    const val Home = "home"
    const val Nearby = "nearby"
    const val Journeys = "journeys"
    const val Settings = "settings"
    const val StationDetail = "station/{crs}"
    fun stationDetail(crs: String) = "station/$crs"
}

private data class TopLevelDest(
    val route: String,
    val labelRes: Int,
    val icon: ImageVector,
)

@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    val topLevel = listOf(
        TopLevelDest(Routes.Home, R.string.nav_home, Icons.Default.Home),
        TopLevelDest(Routes.Nearby, R.string.nav_nearby, Icons.Default.NearMe),
        TopLevelDest(Routes.Journeys, R.string.nav_journeys, Icons.Default.Route),
        TopLevelDest(Routes.Settings, R.string.nav_settings, Icons.Default.Settings),
    )
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val showBottomBar = topLevel.any { dest ->
        currentDestination?.hierarchy?.any { it.route == dest.route } == true
    }

    Scaffold(
        containerColor = NeonBackground,
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(containerColor = NeonSurface) {
                    topLevel.forEach { dest ->
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
                                Icon(dest.icon, contentDescription = stringResource(dest.labelRes))
                            },
                            label = { Text(stringResource(dest.labelRes)) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = NeonCyan,
                                selectedTextColor = NeonCyan,
                                unselectedIconColor = NeonMuted,
                                unselectedTextColor = NeonMuted,
                                indicatorColor = NeonBackground,
                            ),
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.Nearby,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Routes.Home) {
                PlaceholderScreen(stringResource(R.string.placeholder_home))
            }
            composable(Routes.Nearby) {
                NearbyRoute(
                    onStationClick = { crs ->
                        navController.navigate(Routes.stationDetail(crs))
                    },
                )
            }
            composable(Routes.Journeys) {
                PlaceholderScreen(stringResource(R.string.placeholder_journeys))
            }
            composable(Routes.Settings) {
                PlaceholderScreen(stringResource(R.string.placeholder_settings))
            }
            composable(
                route = Routes.StationDetail,
                arguments = listOf(navArgument("crs") { type = NavType.StringType }),
            ) { entry ->
                val crs = entry.arguments?.getString("crs").orEmpty()
                StationDetailRoute(
                    crsCode = crs,
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}

@Composable
private fun PlaceholderScreen(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = NeonMuted,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
    }
}
