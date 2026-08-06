package com.ukrailtracker.app.ui.navigation

import android.net.Uri
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ukrailtracker.app.R
import com.ukrailtracker.app.ui.home.HomeRoute
import com.ukrailtracker.app.ui.journey.JourneyDetailRoute
import com.ukrailtracker.app.ui.journey.JourneyOptionsRoute
import com.ukrailtracker.app.ui.journey.JourneyPlannerRoute
import com.ukrailtracker.app.ui.journey.JourneysRoute
import com.ukrailtracker.app.ui.nearby.NearbyRoute
import com.ukrailtracker.app.ui.search.SearchRoute
import com.ukrailtracker.app.ui.settings.SettingsRoute
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
    const val Search = "search"
    const val PickFavourite = "pick_favourite"
    const val PickCommuteStation = "pick_commute_station"
    const val PickWalkUpStation = "pick_walkup_station"
    const val PickJourneyOrigin = "pick_journey_origin"
    const val PickJourneyDestination = "pick_journey_destination"
    const val JourneyPlan = "journey_plan"
    const val JourneyOptions = "journey_options/{origin}/{dest}"
    const val JourneyDetail = "journey_detail/{origin}/{dest}/{serviceId}"
    const val StationDetail = "station/{crs}"

    fun stationDetail(crs: String) = "station/$crs"
    fun journeyOptions(origin: String, dest: String) =
        "journey_options/${origin.uppercase()}/${dest.uppercase()}"

    fun journeyDetail(origin: String, dest: String, serviceId: String) =
        "journey_detail/${origin.uppercase()}/${dest.uppercase()}/${Uri.encode(serviceId)}"
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
            startDestination = Routes.Home,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Routes.Home) { entry ->
                val pendingWalkUp by entry.savedStateHandle
                    .getStateFlow<String?>("pending_walkup_crs", null)
                    .collectAsStateWithLifecycle()
                HomeRoute(
                    onStationClick = { crs ->
                        navController.navigate(Routes.stationDetail(crs))
                    },
                    onOpenSettings = {
                        navController.navigate(Routes.Settings) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onChangeWalkUpStation = {
                        navController.navigate(Routes.PickWalkUpStation)
                    },
                    pendingWalkUpOverrideCrs = pendingWalkUp,
                    onWalkUpOverrideConsumed = {
                        entry.savedStateHandle["pending_walkup_crs"] = null
                    },
                    onJourneyServiceClick = { origin, dest, serviceId ->
                        navController.navigate(Routes.journeyDetail(origin, dest, serviceId))
                    },
                )
            }
            composable(Routes.Nearby) {
                NearbyRoute(
                    onStationClick = { crs ->
                        navController.navigate(Routes.stationDetail(crs))
                    },
                    onSearchClick = {
                        navController.navigate(Routes.Search)
                    },
                )
            }
            composable(Routes.Journeys) {
                JourneysRoute(
                    onPlanJourney = { navController.navigate(Routes.JourneyPlan) },
                    onOpenPair = { origin, dest ->
                        navController.navigate(Routes.journeyOptions(origin, dest))
                    },
                    onOpenPinned = { origin, dest, serviceId ->
                        navController.navigate(Routes.journeyDetail(origin, dest, serviceId))
                    },
                )
            }
            composable(Routes.JourneyPlan) { entry ->
                val pendingOrigin by entry.savedStateHandle
                    .getStateFlow<String?>("pending_journey_origin_crs", null)
                    .collectAsStateWithLifecycle()
                val pendingDest by entry.savedStateHandle
                    .getStateFlow<String?>("pending_journey_destination_crs", null)
                    .collectAsStateWithLifecycle()
                JourneyPlannerRoute(
                    pendingOriginCrs = pendingOrigin,
                    pendingDestinationCrs = pendingDest,
                    onConsumePendingOrigin = {
                        entry.savedStateHandle["pending_journey_origin_crs"] = null
                    },
                    onConsumePendingDestination = {
                        entry.savedStateHandle["pending_journey_destination_crs"] = null
                    },
                    onPickOrigin = { navController.navigate(Routes.PickJourneyOrigin) },
                    onPickDestination = { navController.navigate(Routes.PickJourneyDestination) },
                    onSearch = { origin, dest ->
                        navController.navigate(Routes.journeyOptions(origin, dest))
                    },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                route = Routes.JourneyOptions,
                arguments = listOf(
                    navArgument("origin") { type = NavType.StringType },
                    navArgument("dest") { type = NavType.StringType },
                ),
            ) { entry ->
                val origin = entry.arguments?.getString("origin").orEmpty()
                val dest = entry.arguments?.getString("dest").orEmpty()
                JourneyOptionsRoute(
                    originCrs = origin,
                    destinationCrs = dest,
                    onBack = { navController.popBackStack() },
                    onOpenService = { serviceId ->
                        navController.navigate(Routes.journeyDetail(origin, dest, serviceId))
                    },
                )
            }
            composable(
                route = Routes.JourneyDetail,
                arguments = listOf(
                    navArgument("origin") { type = NavType.StringType },
                    navArgument("dest") { type = NavType.StringType },
                    navArgument("serviceId") { type = NavType.StringType },
                ),
            ) { entry ->
                val origin = entry.arguments?.getString("origin").orEmpty()
                val dest = entry.arguments?.getString("dest").orEmpty()
                val serviceId = Uri.decode(entry.arguments?.getString("serviceId").orEmpty())
                JourneyDetailRoute(
                    originCrs = origin,
                    destinationCrs = dest,
                    serviceId = serviceId,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.Settings) { entry ->
                val pendingFav by entry.savedStateHandle
                    .getStateFlow<String?>("pending_favourite_crs", null)
                    .collectAsStateWithLifecycle()
                val pendingWindow by entry.savedStateHandle
                    .getStateFlow<String?>("pending_window_station_crs", null)
                    .collectAsStateWithLifecycle()
                SettingsRoute(
                    pendingFavouriteCrs = pendingFav,
                    pendingWindowStationCrs = pendingWindow,
                    onConsumePendingFavourite = {
                        entry.savedStateHandle["pending_favourite_crs"] = null
                    },
                    onConsumePendingWindowStation = {
                        entry.savedStateHandle["pending_window_station_crs"] = null
                    },
                    onPickFavouriteStation = {
                        navController.navigate(Routes.PickFavourite)
                    },
                    onPickWindowStation = {
                        navController.navigate(Routes.PickCommuteStation)
                    },
                )
            }
            composable(Routes.Search) {
                SearchRoute(
                    onBack = { navController.popBackStack() },
                    onStationClick = { crs ->
                        navController.navigate(Routes.stationDetail(crs))
                    },
                )
            }
            composable(Routes.PickFavourite) {
                SearchRoute(
                    onBack = { navController.popBackStack() },
                    onStationClick = { crs ->
                        navController.previousBackStackEntry
                            ?.savedStateHandle
                            ?.set("pending_favourite_crs", crs)
                        navController.popBackStack()
                    },
                )
            }
            composable(Routes.PickWalkUpStation) {
                SearchRoute(
                    onBack = { navController.popBackStack() },
                    onStationClick = { crs ->
                        navController.previousBackStackEntry
                            ?.savedStateHandle
                            ?.set("pending_walkup_crs", crs)
                        navController.popBackStack()
                    },
                )
            }
            composable(Routes.PickCommuteStation) {
                SearchRoute(
                    onBack = { navController.popBackStack() },
                    onStationClick = { crs ->
                        navController.previousBackStackEntry
                            ?.savedStateHandle
                            ?.set("pending_window_station_crs", crs)
                        navController.popBackStack()
                    },
                )
            }
            composable(Routes.PickJourneyOrigin) {
                SearchRoute(
                    onBack = { navController.popBackStack() },
                    onStationClick = { crs ->
                        navController.previousBackStackEntry
                            ?.savedStateHandle
                            ?.set("pending_journey_origin_crs", crs)
                        navController.popBackStack()
                    },
                )
            }
            composable(Routes.PickJourneyDestination) {
                SearchRoute(
                    onBack = { navController.popBackStack() },
                    onStationClick = { crs ->
                        navController.previousBackStackEntry
                            ?.savedStateHandle
                            ?.set("pending_journey_destination_crs", crs)
                        navController.popBackStack()
                    },
                )
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
