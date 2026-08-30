package com.ukrailtracker.app.ui.home

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ukrailtracker.app.R
import com.ukrailtracker.app.appContainer
import com.ukrailtracker.app.debug.DebugWalkUp
import com.ukrailtracker.app.ui.components.AppBarIconButton
import com.ukrailtracker.app.ui.components.AppScreen

@Composable
fun HomeRoute(
    onStationClick: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onChangeWalkUpStation: () -> Unit,
    onJourneyServiceClick: (originCrs: String, destCrs: String, serviceId: String) -> Unit,
    pendingWalkUpOverrideCrs: String? = null,
    onWalkUpOverrideConsumed: () -> Unit = {},
    pendingFrequentLocationChecks: Boolean = false,
    pendingFrequentChecksCrs: String? = null,
    onFrequentLocationChecksConsumed: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val container = context.appContainer()
    val viewModel: HomeViewModel = viewModel(
        factory = HomeViewModel.factory(
            appContext = context,
            stationRepository = container.stationRepository,
            departureRepository = container.departureRepository,
            journeyRepository = container.journeyRepository,
            journeyLogRepository = container.journeyLogRepository,
            locationProvider = container.locationProvider,
            commuteWindowStore = container.commuteWindowStore,
            favouritesStore = container.favouritesStore,
            pinnedJourneyStore = container.pinnedJourneyStore,
            latestSpeedMps = container.latestSpeedMps,
        ),
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var simErrorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(viewModel) {
        viewModel.openJourney.collect { event ->
            onJourneyServiceClick(event.originCrs, event.destinationCrs, event.serviceId)
        }
    }

    LaunchedEffect(Unit) {
        DebugWalkUp.errors.collect { message ->
            simErrorMessage = message
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.platformAppeared.collect {
            vibrateForPlatform(context)
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.arrivalBuzz.collect {
            vibrateForArrivalApproach(context)
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* ongoing / geofence notifications may still show on older APIs */ }

    val backgroundPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        armStationGeofences(context)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        val locationGranted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (locationGranted) {
            viewModel.onPermissionGranted()
            armStationGeofences(context)
            maybeRequestNotificationPermission(context, notificationPermissionLauncher)
        } else if (result.keys.any {
                it == Manifest.permission.ACCESS_FINE_LOCATION ||
                    it == Manifest.permission.ACCESS_COARSE_LOCATION
            }
        ) {
            viewModel.onPermissionDenied()
            context.appContainer().geofenceRearmScheduler.cancel()
        }
    }

    fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    fun hasUsableLocation(): Boolean =
        hasLocationPermission() || DebugWalkUp.isSimulatingLocation()

    fun hasBackgroundLocationPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return true
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_BACKGROUND_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
    }

    LaunchedEffect(Unit) {
        if (!hasUsableLocation()) {
            viewModel.onNeedsPermission()
            return@LaunchedEffect
        }
        viewModel.onPermissionGranted()
        if (hasLocationPermission() && !DebugWalkUp.isSimulatingLocation()) {
            armStationGeofences(context)
        }
    }

    LaunchedEffect(pendingWalkUpOverrideCrs) {
        val crs = pendingWalkUpOverrideCrs ?: return@LaunchedEffect
        viewModel.setOriginOverride(crs)
        onWalkUpOverrideConsumed()
    }

    LaunchedEffect(pendingFrequentLocationChecks, pendingFrequentChecksCrs) {
        if (!pendingFrequentLocationChecks) return@LaunchedEffect
        viewModel.onOpenedForFrequentChecks(pendingFrequentChecksCrs)
        onFrequentLocationChecksConsumed()
    }

    val requestLocationPermission = {
        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            ),
        )
    }

    val presence = when (val s = state) {
        is HomeUiState.Content -> s.walkUp ?: WalkUpHomeUi.NotAtStation
        else -> WalkUpHomeUi.NotAtStation
    }
    val selectedSimWatch = (presence as? WalkUpHomeUi.AtStation)?.platformWatch

    LaunchedEffect(presence) {
        if (presence is WalkUpHomeUi.OnJourney && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    if (simErrorMessage != null) {
        AlertDialog(
            onDismissRequest = { simErrorMessage = null },
            confirmButton = {
                TextButton(onClick = { simErrorMessage = null }) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            text = {
                Text(simErrorMessage.orEmpty())
            },
        )
    }

    AppScreen(
        title = stringResource(R.string.home_title),
        modifier = modifier,
        actions = {
            DebugWalkUp.HomeAppBarAction(
                stationRepository = container.stationRepository,
                favouritesStore = container.favouritesStore,
                commuteWindowStore = container.commuteWindowStore,
                journeyRepository = container.journeyRepository,
                pinnedJourneyStore = container.pinnedJourneyStore,
                selectedWatch = selectedSimWatch,
                onPreparePresence = viewModel::prepareForDebugWalkUp,
            )
            AppBarIconButton(
                imageVector = Icons.Default.Refresh,
                contentDescription = stringResource(R.string.home_refresh),
                onClick = viewModel::refresh,
            )
        },
        belowTopBar = {
            PresenceStatusBar(
                walkUp = presence,
                onAllowLocation = requestLocationPermission,
                onChangeStation = onChangeWalkUpStation,
                onClearOverride = viewModel::clearOriginOverride,
                onOriginClick = onStationClick,
                showBackgroundLocationCta = hasLocationPermission() &&
                    !hasBackgroundLocationPermission() &&
                    !DebugWalkUp.isSimulatingLocation(),
                onAllowBackgroundLocation = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        backgroundPermissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                    }
                },
            )
        },
    ) {
        when (val s = state) {
            HomeUiState.Loading -> HomeLoading()
            is HomeUiState.Empty -> EmptyHome(
                onOpenSettings = onOpenSettings,
                modifier = Modifier.fillMaxSize(),
            )
            is HomeUiState.Error -> HomeError(message = s.message)
            is HomeUiState.Content -> {
                HomeContent(
                    content = s,
                    onStationClick = onStationClick,
                    onBannerClick = viewModel::openDisruption,
                    onOpenSettings = onOpenSettings,
                    onEndOnTrainTrip = viewModel::endOnTrainTrip,
                    onOpenOnTrainDetail = viewModel::openOnTrainServiceDetail,
                    onSelectForPlatformWatch = { origin, dest, serviceId ->
                        viewModel.selectServiceForPlatformWatch(origin, dest, serviceId)
                    },
                    onClearPlatformWatch = viewModel::clearPlatformWatch,
                    onRefreshPlatformWatch = viewModel::refreshPlatformWatch,
                    onOpenWatchedService = viewModel::openWatchedService,
                    onConfirmBoarding = viewModel::confirmBoarding,
                    onRejectAssumedBoarding = viewModel::rejectAssumedBoarding,
                    onSelectBoardingCandidate = viewModel::selectBoardingCandidate,
                    onDismissBoarding = viewModel::dismissBoarding,
                    modifier = Modifier.fillMaxSize(),
                )
                s.selectedDisruption?.let { disruption ->
                    DisruptionDialog(
                        stationName = s.selectedStationName.orEmpty(),
                        disruption = disruption,
                        onDismiss = viewModel::dismissDisruption,
                    )
                }
            }
        }
    }
}

private fun armStationGeofences(context: Context) {
    context.appContainer().armStationGeofencesAsync(forceLocationRefresh = true)
}

private fun maybeRequestNotificationPermission(
    context: Context,
    notificationLauncher: androidx.activity.result.ActivityResultLauncher<String>,
) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val granted = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.POST_NOTIFICATIONS,
    ) == PackageManager.PERMISSION_GRANTED
    if (!granted) {
        notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}

private fun vibrateForPlatform(context: Context) {
    vibratePattern(context, longArrayOf(0, 180, 80, 220))
}

private fun vibrateForArrivalApproach(context: Context) {
    vibratePattern(context, longArrayOf(0, 250, 120, 250, 120, 400))
}

private fun vibratePattern(context: Context, pattern: LongArray) {
    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    } ?: return
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
    } else {
        @Suppress("DEPRECATION")
        vibrator.vibrate(pattern, -1)
    }
}
