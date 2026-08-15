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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ukrailtracker.app.R
import com.ukrailtracker.app.appContainer
import com.ukrailtracker.app.debug.DebugWalkUp
import com.ukrailtracker.app.domain.model.DisruptionSeverity
import com.ukrailtracker.app.domain.model.DisruptionSummary
import com.ukrailtracker.app.domain.usecase.FavouriteServiceUiModel
import com.ukrailtracker.app.domain.usecase.JourneyTimeUtils
import com.ukrailtracker.app.domain.usecase.ProgressPointUiModel
import com.ukrailtracker.app.ui.components.AppBarIconButton
import com.ukrailtracker.app.ui.components.AppScreen
import com.ukrailtracker.app.ui.station.StatusKind
import com.ukrailtracker.app.ui.theme.NeonAmber
import com.ukrailtracker.app.ui.theme.NeonBackground
import com.ukrailtracker.app.ui.theme.NeonCyan
import com.ukrailtracker.app.ui.theme.NeonGreen
import com.ukrailtracker.app.ui.theme.NeonMagenta
import com.ukrailtracker.app.ui.theme.NeonMuted
import com.ukrailtracker.app.ui.theme.NeonSurface
import kotlinx.coroutines.delay
import java.time.LocalDateTime

@Composable
fun HomeRoute(
    onStationClick: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onChangeWalkUpStation: () -> Unit,
    onJourneyServiceClick: (originCrs: String, destCrs: String, serviceId: String) -> Unit,
    pendingWalkUpOverrideCrs: String? = null,
    onWalkUpOverrideConsumed: () -> Unit = {},
    pendingFrequentLocationChecks: Boolean = false,
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
        // Geofences still arm with foreground-only; background improves reliability when idle.
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

    /** Real GPS grant, or debug simulator actively injecting locations. */
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
        // Geofences need real OS location; skip while the simulator is injecting GPS.
        if (hasLocationPermission() && !DebugWalkUp.isSimulatingLocation()) {
            armStationGeofences(context)
        }
    }

    LaunchedEffect(pendingWalkUpOverrideCrs) {
        val crs = pendingWalkUpOverrideCrs ?: return@LaunchedEffect
        viewModel.setOriginOverride(crs)
        onWalkUpOverrideConsumed()
    }

    LaunchedEffect(pendingFrequentLocationChecks) {
        if (!pendingFrequentLocationChecks) return@LaunchedEffect
        viewModel.onOpenedForFrequentChecks()
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
            HomeUiState.Loading -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator(color = NeonCyan)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(stringResource(R.string.home_loading), color = NeonMuted)
                }
            }
            is HomeUiState.Empty -> {
                EmptyHome(
                    onOpenSettings = onOpenSettings,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            is HomeUiState.Error -> {
                Text(
                    text = s.message,
                    color = NeonMagenta,
                    modifier = Modifier.padding(24.dp),
                )
            }
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

@Composable
private fun EmptyHome(
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.home_empty_title),
            style = MaterialTheme.typography.headlineMedium,
            color = NeonCyan,
        )
        Spacer(modifier.height(8.dp))
        Text(
            text = stringResource(R.string.home_empty_body),
            style = MaterialTheme.typography.bodyLarge,
            color = NeonMuted,
        )
        Spacer(modifier.height(16.dp))
        TextButton(onClick = onOpenSettings) {
            Text(stringResource(R.string.home_open_settings), color = NeonCyan)
        }
    }
}

@Composable
private fun PresenceStatusBar(
    walkUp: WalkUpHomeUi,
    onAllowLocation: () -> Unit,
    onChangeStation: () -> Unit,
    onClearOverride: () -> Unit,
    onOriginClick: (String) -> Unit,
    showBackgroundLocationCta: Boolean = false,
    onAllowBackgroundLocation: () -> Unit = {},
) {
    val (label, labelColor) = when (walkUp) {
        WalkUpHomeUi.NeedsPermission,
        WalkUpHomeUi.LocationUnavailable,
        WalkUpHomeUi.NotAtStation,
        -> stringResource(R.string.home_presence_not_at_station) to NeonMuted
        is WalkUpHomeUi.NearStation ->
            stringResource(R.string.home_presence_near, walkUp.stationName) to NeonAmber
        is WalkUpHomeUi.AtStation ->
            stringResource(R.string.home_presence_at, walkUp.stationName) to NeonCyan
        is WalkUpHomeUi.BoardingPrompt ->
            stringResource(R.string.home_boarding_presence, walkUp.originName) to NeonAmber
        is WalkUpHomeUi.OnJourney ->
            stringResource(R.string.home_on_train_presence, walkUp.destinationName) to NeonCyan
    }
    val subtitle = when (walkUp) {
        is WalkUpHomeUi.NearStation -> "${walkUp.crsCode} · ${walkUp.distanceLabel}"
        is WalkUpHomeUi.AtStation -> buildString {
            append(walkUp.crsCode)
            if (walkUp.manualOverride) {
                append(" · ")
                append(stringResource(R.string.home_presence_manual))
            } else {
                walkUp.distanceLabel?.let {
                    append(" · ")
                    append(it)
                }
            }
        }
        is WalkUpHomeUi.BoardingPrompt -> stringResource(
            R.string.home_boarding_presence_subtitle,
            walkUp.assumed.scheduledDepartureLabel,
            walkUp.assumed.destinationName,
        )
        is WalkUpHomeUi.OnJourney -> when {
            walkUp.stoppedAtStation && !walkUp.currentStopName.isNullOrBlank() ->
                stringResource(R.string.home_on_train_presence_at_stop, walkUp.currentStopName)
            !walkUp.nextStopName.isNullOrBlank() ->
                stringResource(R.string.home_on_train_presence_next, walkUp.nextStopName)
            else -> walkUp.destinationCrs
        }
        WalkUpHomeUi.NotAtStation,
        WalkUpHomeUi.LocationUnavailable,
        -> if (showBackgroundLocationCta) {
            stringResource(R.string.home_presence_background_hint)
        } else {
            null
        }
        else -> null
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(NeonSurface)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .then(
                    if (walkUp is WalkUpHomeUi.AtStation) {
                        Modifier.clickable { onOriginClick(walkUp.crsCode) }
                    } else {
                        Modifier
                    },
                ),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = labelColor,
                maxLines = 1,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = NeonMuted,
                    maxLines = 1,
                )
            }
        }
        when (walkUp) {
            WalkUpHomeUi.NeedsPermission -> {
                TextButton(onClick = onAllowLocation) {
                    Text(stringResource(R.string.home_presence_allow_location), color = NeonCyan)
                }
                TextButton(onClick = onChangeStation) {
                    Text(stringResource(R.string.home_presence_set_station), color = NeonMuted)
                }
            }
            WalkUpHomeUi.LocationUnavailable,
            WalkUpHomeUi.NotAtStation,
            is WalkUpHomeUi.NearStation,
            -> {
                if (showBackgroundLocationCta) {
                    TextButton(onClick = onAllowBackgroundLocation) {
                        Text(stringResource(R.string.home_presence_allow_background), color = NeonCyan)
                    }
                }
                TextButton(onClick = onChangeStation) {
                    Text(stringResource(R.string.home_presence_set_station), color = NeonMuted)
                }
            }
            is WalkUpHomeUi.AtStation -> {
                TextButton(onClick = onChangeStation) {
                    Text(stringResource(R.string.home_presence_change_station), color = NeonCyan)
                }
                if (walkUp.manualOverride) {
                    TextButton(onClick = onClearOverride) {
                        Text(stringResource(R.string.home_presence_use_gps), color = NeonMuted)
                    }
                }
            }
            is WalkUpHomeUi.BoardingPrompt -> {
                // Actions live in the boarding prompt body.
            }
            is WalkUpHomeUi.OnJourney -> {
                // Actions live in the on-train body.
            }
        }
    }
}

@Composable
private fun HomeContent(
    content: HomeUiState.Content,
    onStationClick: (String) -> Unit,
    onBannerClick: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onEndOnTrainTrip: () -> Unit,
    onOpenOnTrainDetail: () -> Unit,
    onSelectForPlatformWatch: (originCrs: String, destCrs: String, serviceId: String) -> Unit,
    onClearPlatformWatch: () -> Unit,
    onRefreshPlatformWatch: () -> Unit,
    onOpenWatchedService: () -> Unit,
    onConfirmBoarding: () -> Unit,
    onRejectAssumedBoarding: () -> Unit,
    onSelectBoardingCandidate: (serviceId: String) -> Unit,
    onDismissBoarding: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val atStation = content.walkUp as? WalkUpHomeUi.AtStation
    val boarding = content.walkUp as? WalkUpHomeUi.BoardingPrompt
    val onJourney = content.walkUp as? WalkUpHomeUi.OnJourney

    // On-train uses its own scroll pane for calling points — avoid nested LazyColumns.
    if (onJourney != null) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (content.refreshing) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = NeonCyan,
                    )
                    Text(stringResource(R.string.home_refreshing), color = NeonMuted)
                }
            }
            OnTrainLiveBody(
                journey = onJourney,
                onEndTrip = onEndOnTrainTrip,
                onOpenDetail = onOpenOnTrainDetail,
                modifier = Modifier.weight(1f),
            )
        }
        return
    }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (content.refreshing) {
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = NeonCyan,
                    )
                    Text(stringResource(R.string.home_refreshing), color = NeonMuted)
                }
            }
        }

        if (boarding != null) {
            item {
                BoardingPromptBody(
                    prompt = boarding,
                    onConfirm = onConfirmBoarding,
                    onRejectAssumed = onRejectAssumedBoarding,
                    onSelectCandidate = onSelectBoardingCandidate,
                    onDismiss = onDismissBoarding,
                )
            }
        } else if (atStation != null) {
            // At station and not on a journey (M6): replace usual home with origin focus.
            item {
                Text(
                    text = stringResource(R.string.home_walkup_favourites_heading),
                    style = MaterialTheme.typography.bodyMedium,
                    color = NeonMuted,
                )
            }
            when {
                atStation.destinationsLoading -> {
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = NeonCyan,
                            )
                            Text(
                                stringResource(R.string.home_walkup_loading_destinations),
                                color = NeonMuted,
                            )
                        }
                    }
                }
                atStation.noFavouritesConfigured -> {
                    item {
                        Text(
                            text = stringResource(R.string.home_walkup_no_favourites),
                            color = NeonMuted,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        TextButton(onClick = onOpenSettings) {
                            Text(stringResource(R.string.home_open_settings), color = NeonCyan)
                        }
                    }
                }
                else -> {
                    val anyServices = atStation.destinations.any { it.services.isNotEmpty() }
                    if (!anyServices && atStation.destinations.all { it.errorMessage == null }) {
                        item {
                            Text(
                                text = stringResource(R.string.home_walkup_no_services),
                                color = NeonMuted,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    } else {
                        item {
                            Text(
                                text = stringResource(R.string.home_platform_watch_tap_hint),
                                color = NeonMuted,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                    atStation.platformWatch?.let { watch ->
                        item {
                            PlatformWatchBanner(
                                watch = watch,
                                onClear = onClearPlatformWatch,
                                onRefresh = onRefreshPlatformWatch,
                                onOpen = onOpenWatchedService,
                            )
                        }
                    }
                    items(atStation.destinations, key = { it.destinationCrs }) { dest ->
                        FavouriteDestinationBlock(
                            destination = dest,
                            originCrs = atStation.crsCode,
                            selectedServiceId = atStation.platformWatch?.serviceId,
                            onServiceClick = onSelectForPlatformWatch,
                        )
                    }
                }
            }
        } else if (content.stations.isNotEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.home_my_stations),
                    style = MaterialTheme.typography.bodyMedium,
                    color = NeonMuted,
                )
            }
            items(content.stations, key = { it.crsCode }) { card ->
                StationCard(
                    card = card,
                    onStationClick = { onStationClick(card.crsCode) },
                    onBannerClick = { onBannerClick(card.crsCode) },
                )
            }
        } else {
            item {
                EmptyHome(onOpenSettings = onOpenSettings)
            }
        }

        item {
            Text(
                text = stringResource(R.string.powered_by_national_rail),
                style = MaterialTheme.typography.bodyMedium,
                color = NeonMuted,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )
        }
    }
}

@Composable
private fun BoardingPromptBody(
    prompt: WalkUpHomeUi.BoardingPrompt,
    onConfirm: () -> Unit,
    onRejectAssumed: () -> Unit,
    onSelectCandidate: (serviceId: String) -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(NeonSurface, RoundedCornerShape(12.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.home_boarding_heading),
            style = MaterialTheme.typography.bodyMedium,
            color = NeonMuted,
        )
        Text(
            text = stringResource(
                R.string.home_boarding_question,
                prompt.assumed.scheduledDepartureLabel,
                prompt.assumed.destinationName,
            ),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        BoardingCandidateMeta(prompt.assumed)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.home_boarding_confirm), color = NeonCyan)
            }
            TextButton(onClick = onRejectAssumed) {
                Text(stringResource(R.string.home_boarding_not_this), color = NeonAmber)
            }
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.home_boarding_dismiss), color = NeonMuted)
            }
        }
        if (prompt.showPicker) {
            Text(
                text = stringResource(R.string.home_boarding_picker_heading),
                style = MaterialTheme.typography.bodyMedium,
                color = NeonMuted,
            )
            if (prompt.alternatives.isEmpty()) {
                Text(
                    text = stringResource(R.string.home_boarding_no_alternatives),
                    style = MaterialTheme.typography.bodyMedium,
                    color = NeonMuted,
                )
            } else {
                prompt.alternatives.forEach { candidate ->
                    BoardingCandidateRow(
                        candidate = candidate,
                        onClick = { onSelectCandidate(candidate.serviceId) },
                    )
                }
            }
        }
    }
}

@Composable
private fun BoardingCandidateMeta(candidate: BoardingCandidateUi) {
    val meta = when {
        candidate.operatorName.isNotBlank() && !candidate.platform.isNullOrBlank() ->
            stringResource(
                R.string.home_boarding_operator_platform,
                candidate.operatorName,
                candidate.platform,
            )
        candidate.operatorName.isNotBlank() ->
            stringResource(R.string.home_boarding_operator_only, candidate.operatorName)
        !candidate.platform.isNullOrBlank() ->
            stringResource(R.string.home_walkup_platform, candidate.platform)
        else -> stringResource(R.string.home_walkup_platform_unknown)
    }
    Text(
        text = meta,
        style = MaterialTheme.typography.bodyMedium,
        color = NeonMuted,
    )
}

@Composable
private fun BoardingCandidateRow(
    candidate: BoardingCandidateUi,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(NeonBackground, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = candidate.scheduledDepartureLabel,
            color = NeonCyan,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(end = 10.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = candidate.destinationName,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyMedium,
            )
            BoardingCandidateMeta(candidate)
        }
    }
}

@Composable
private fun PlatformWatchBanner(
    watch: PlatformWatchUi,
    onClear: () -> Unit,
    onRefresh: () -> Unit,
    onOpen: () -> Unit,
) {
    val platform = watch.platform
    var now by remember(watch.serviceId) { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(watch.serviceId) {
        while (true) {
            now = LocalDateTime.now()
            delay(1_000L)
        }
    }
    val departureLabel = JourneyTimeUtils.effectiveDepartureLabel(
        scheduledDepartureLabel = watch.scheduledDepartureLabel,
        expectedDepartureLabel = watch.expectedDepartureLabel,
    )
    val minutesLeft = JourneyTimeUtils.minutesUntilDeparture(departureLabel, now)
    val countdownText = when {
        minutesLeft == null -> stringResource(R.string.home_platform_watch_countdown_unknown)
        minutesLeft > 0L -> stringResource(
            R.string.home_platform_watch_countdown,
            minutesLeft.toInt(),
        )
        minutesLeft == 0L -> stringResource(R.string.home_platform_watch_countdown_due)
        else -> stringResource(R.string.home_platform_watch_countdown_departed)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(NeonSurface, RoundedCornerShape(12.dp))
            .border(1.dp, if (platform != null) NeonGreen else NeonAmber, RoundedCornerShape(12.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            watch.checkedAtLabel?.takeIf { it.isNotBlank() }?.let { checkedAt ->
                Text(
                    text = stringResource(R.string.home_platform_watch_checked, checkedAt),
                    color = NeonMuted,
                    fontSize = 12.sp,
                    modifier = Modifier.weight(1f),
                )
            } ?: Spacer(modifier = Modifier.weight(1f))
            if (watch.polling) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .padding(end = 4.dp)
                        .size(18.dp),
                    strokeWidth = 2.dp,
                    color = NeonCyan,
                )
            } else {
                IconButton(onClick = onRefresh) {
                    Icon(
                        imageVector = Icons.Default.Repeat,
                        contentDescription = stringResource(R.string.home_platform_watch_refresh),
                        tint = NeonCyan,
                    )
                }
            }
        }
        if (platform == null) {
            Text(
                text = stringResource(R.string.home_platform_watch_heading),
                style = MaterialTheme.typography.bodyMedium,
                color = NeonMuted,
            )
        }
        Text(
            text = stringResource(
                R.string.home_platform_watch_train,
                watch.scheduledDepartureLabel,
                watch.destinationName,
            ),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = countdownText,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = when {
                minutesLeft == null -> NeonMuted
                minutesLeft > 0L -> NeonCyan
                else -> NeonAmber
            },
        )
        if (platform != null) {
            Text(
                text = stringResource(R.string.home_platform_watch_found, platform),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = NeonGreen,
            )
        } else {
            Text(
                text = if (watch.polling) {
                    stringResource(R.string.home_platform_watch_polling)
                } else {
                    stringResource(R.string.home_platform_watch_waiting)
                },
                style = MaterialTheme.typography.bodyLarge,
                color = NeonAmber,
            )
        }
        watch.errorMessage?.let { err ->
            Text(text = err, color = NeonMagenta, style = MaterialTheme.typography.bodyMedium)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onOpen) {
                Text(stringResource(R.string.home_platform_watch_open), color = NeonCyan)
            }
            TextButton(onClick = onClear) {
                Text(stringResource(R.string.home_platform_watch_clear), color = NeonMuted)
            }
        }
    }
}

@Composable
private fun FavouriteDestinationBlock(
    destination: WalkUpDestinationUi,
    originCrs: String,
    selectedServiceId: String?,
    onServiceClick: (originCrs: String, destCrs: String, serviceId: String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(NeonSurface, RoundedCornerShape(10.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = destination.destinationName,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = destination.destinationCrs,
            style = MaterialTheme.typography.bodyMedium,
            color = NeonMuted,
        )
        when {
            destination.errorMessage != null && destination.services.isEmpty() -> {
                Text(
                    text = destination.errorMessage,
                    color = NeonMagenta,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            destination.services.isEmpty() -> {
                Text(
                    text = stringResource(R.string.home_walkup_no_services_to_dest),
                    color = NeonMuted,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            else -> {
                destination.services.forEach { service ->
                    FavouriteServiceRow(
                        service = service,
                        selected = service.serviceId == selectedServiceId,
                        onClick = {
                            onServiceClick(originCrs, destination.destinationCrs, service.serviceId)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun FavouriteServiceRow(
    service: FavouriteServiceUiModel,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val statusColor = when {
        service.isCancelled -> NeonMagenta
        (service.delayMinutes ?: 0) > 0 -> NeonAmber
        else -> NeonCyan
    }
    val platform = service.platform?.takeIf { it.isNotBlank() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(NeonBackground, RoundedCornerShape(8.dp))
            .then(
                if (selected) {
                    Modifier.border(1.5.dp, NeonCyan, RoundedCornerShape(8.dp))
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = service.scheduledDepartureLabel,
            color = NeonCyan,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(end = 10.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = platform?.let { stringResource(R.string.home_walkup_platform, it) }
                    ?: stringResource(R.string.home_walkup_platform_unknown),
                color = if (platform != null) NeonGreen else MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (platform != null) FontWeight.Bold else FontWeight.Normal,
            )
            when {
                selected -> Text(
                    text = stringResource(R.string.home_platform_watch_selected),
                    color = NeonCyan,
                    style = MaterialTheme.typography.bodyMedium,
                )
                service.durationMinutes != null -> Text(
                    text = stringResource(R.string.home_walkup_duration, service.durationMinutes),
                    color = NeonMuted,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        Text(
            text = service.statusLabel,
            color = statusColor,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun OnTrainLiveBody(
    journey: WalkUpHomeUi.OnJourney,
    onEndTrip: () -> Unit,
    onOpenDetail: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val liveUnavailable = journey.serviceMissing ||
        (journey.errorMessage != null && journey.minutesRemaining == null)
    val listState = rememberLazyListState()
    val focusIndex = remember(journey.points, journey.currentStopName, journey.nextStopName) {
        val currentIdx = journey.points.indexOfFirst { it.isCurrent }
        if (currentIdx >= 0) currentIdx
        else journey.points.indexOfFirst { it.isNext }.takeIf { it >= 0 } ?: 0
    }
    // Re-centre when the current/next station identity changes (not on every ETA tick).
    val focusKey = journey.points.getOrNull(focusIndex)?.let { p ->
        "${p.crs.orEmpty()}|${p.name}|${p.isCurrent}|${p.isNext}"
    }.orEmpty()
    LaunchedEffect(focusKey) {
        if (journey.points.isEmpty()) return@LaunchedEffect
        listState.animateScrollToItem(focusIndex.coerceIn(0, journey.points.lastIndex))
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (journey.routeMismatch && !liveUnavailable) {
            RouteMismatchBanner(
                stationName = journey.mismatchStationName ?: "—",
                expectedNext = journey.expectedNextStopName,
            )
        }

        // Compact hero strip
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(NeonSurface, RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (liveUnavailable) {
                Text(
                    text = stringResource(R.string.home_on_train_countdown_unavailable),
                    style = MaterialTheme.typography.titleLarge,
                    color = NeonAmber,
                    fontWeight = FontWeight.Bold,
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = journey.minutesRemaining?.let { "$it" } ?: "…",
                        color = NeonCyan,
                        fontWeight = FontWeight.Bold,
                        fontSize = 28.sp,
                        lineHeight = 30.sp,
                    )
                    if (journey.minutesRemaining != null) {
                        Text(
                            text = stringResource(R.string.home_on_train_countdown_unit),
                            color = NeonCyan,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Text(
                        text = stringResource(R.string.home_on_train_hero_sep),
                        color = NeonMuted,
                    )
                    Text(
                        text = journey.destinationName,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    journey.delayMinutes?.takeIf { it > 0 }?.let { delay ->
                        Text(
                            text = stringResource(R.string.home_on_train_delay, delay),
                            color = NeonAmber,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    } ?: Text(
                        text = statusKindLabel(journey.statusKind),
                        color = statusKindColor(journey.statusKind),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (journey.refreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = NeonCyan,
                        )
                    }
                }
                val metaParts = buildList {
                    if (journey.scheduledDepartureLabel.isNotBlank()) {
                        add(
                            stringResource(
                                R.string.home_on_train_scheduled,
                                journey.scheduledDepartureLabel,
                            ),
                        )
                    }
                    journey.nextStopName?.let { next ->
                        add(stringResource(R.string.home_on_train_next_stop, next))
                    }
                    if (journey.stoppedAtStation) {
                        add(stringResource(R.string.home_on_train_refreshing_eta))
                    }
                }
                if (metaParts.isNotEmpty()) {
                    Text(
                        text = metaParts.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = NeonMuted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (liveUnavailable) {
                Text(
                    text = stringResource(R.string.home_on_train_service_missing),
                    color = NeonMagenta,
                    style = MaterialTheme.typography.bodySmall,
                )
            } else {
                journey.errorMessage?.let { err ->
                    Text(text = err, color = NeonMagenta, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(NeonSurface, RoundedCornerShape(12.dp))
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            LinearProgressIndicator(
                progress = { journey.fractionComplete },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = NeonCyan,
                trackColor = NeonBackground,
            )
            if (journey.points.isEmpty()) {
                Text(
                    text = stringResource(
                        if (liveUnavailable) {
                            R.string.home_on_train_progress_unavailable
                        } else {
                            R.string.journey_calling_points_empty
                        },
                    ),
                    color = NeonMuted,
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    contentPadding = PaddingValues(vertical = 2.dp),
                ) {
                    itemsIndexed(
                        items = journey.points,
                        key = { index, point ->
                            "${point.crs.orEmpty()}-${point.name}-$index"
                        },
                    ) { _, point ->
                        ProgressPointRow(point = point)
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (!liveUnavailable) {
                TextButton(onClick = onOpenDetail) {
                    Text(stringResource(R.string.home_on_train_open_detail), color = NeonCyan)
                }
            }
            TextButton(onClick = onEndTrip) {
                Text(stringResource(R.string.home_on_train_end), color = NeonMagenta)
            }
        }
    }
}

@Composable
private fun RouteMismatchBanner(stationName: String, expectedNext: String?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, NeonAmber, RoundedCornerShape(12.dp))
            .background(NeonSurface, RoundedCornerShape(12.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = stringResource(R.string.home_on_train_mismatch_title),
            color = NeonAmber,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = if (expectedNext.isNullOrBlank()) {
                stringResource(R.string.home_on_train_mismatch_body_no_next, stationName)
            } else {
                stringResource(R.string.home_on_train_mismatch_body, stationName, expectedNext)
            },
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun ProgressPointRow(point: ProgressPointUiModel) {
    val color = when {
        point.isCancelled -> NeonMagenta
        point.isCurrent -> NeonCyan
        point.isNext -> NeonAmber
        point.isPast -> NeonMuted
        point.isDestination -> NeonGreen
        else -> MaterialTheme.colorScheme.onSurface
    }
    val minutes = if (!point.isPast && !point.isCancelled) {
        JourneyTimeUtils.minutesUntilArrival(
            scheduledArrivalLabel = point.scheduledLabel,
            expectedArrivalLabel = point.expectedLabel,
        )
    } else {
        null
    }
    val expected = point.expectedLabel?.trim().orEmpty()
    val statusLabel = when {
        point.isCancelled || expected.equals("Cancelled", ignoreCase = true) -> "Cancelled"
        expected.equals("Delayed", ignoreCase = true) -> "Delayed"
        expected.isNotEmpty() &&
            !expected.equals("On time", ignoreCase = true) &&
            JourneyTimeUtils.parseTimeLabel(expected) == null -> expected
        else -> null
    }
    val variance = point.scheduleVarianceMinutes
    // Prefer live minute countdown; for past stops prefer actual clock when Darwin has `at`.
    val etaLabel = when {
        minutes != null && minutes >= 0 -> stringResource(R.string.home_on_train_stop_mins, minutes)
        point.actualLabel != null ->
            stringResource(R.string.home_on_train_stop_actual, point.actualLabel)
        statusLabel != null -> statusLabel
        else -> point.scheduledLabel.orEmpty()
    }
    val varianceLabel = when {
        variance == null || variance == 0 -> null
        variance > 0 -> stringResource(R.string.home_on_train_stop_variance_late, variance)
        else -> stringResource(R.string.home_on_train_stop_variance_early, -variance)
    }
    val varianceColor = when {
        variance == null || variance == 0 -> NeonMuted
        variance > 0 -> NeonAmber
        else -> NeonGreen
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(
                    color = when {
                        point.isCurrent || point.isNext -> color
                        point.isPast -> NeonMuted
                        else -> NeonBackground
                    },
                    shape = CircleShape,
                )
                .border(1.dp, color, CircleShape),
        )
        Text(
            text = point.name,
            color = color,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (point.isCurrent || point.isNext || point.isDestination) {
                FontWeight.Bold
            } else {
                FontWeight.Normal
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        point.crs?.takeIf { it.isNotBlank() }?.let { crs ->
            Text(
                text = crs,
                color = NeonMuted,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
            )
        }
        if (etaLabel.isNotBlank()) {
            Text(
                text = etaLabel,
                color = when {
                    point.isCancelled || statusLabel == "Cancelled" -> NeonMagenta
                    statusLabel == "Delayed" -> NeonAmber
                    point.actualLabel != null && (variance ?: 0) > 0 -> NeonAmber
                    point.isNext || point.isCurrent -> color
                    else -> NeonMuted
                },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (point.isNext || point.isCurrent) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
            )
        }
        if (varianceLabel != null) {
            Text(
                text = varianceLabel,
                color = varianceColor,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        }
    }
}

private fun statusKindLabel(kind: StatusKind): String = when (kind) {
    StatusKind.OnTime -> "On time"
    StatusKind.Delayed -> "Delayed"
    StatusKind.Cancelled -> "Cancelled"
    StatusKind.Other -> "Live"
}

private fun statusKindColor(kind: StatusKind): Color = when (kind) {
    StatusKind.OnTime -> NeonGreen
    StatusKind.Delayed -> NeonAmber
    StatusKind.Cancelled -> NeonMagenta
    StatusKind.Other -> NeonMuted
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

/** Distinct from platform buzz — longer triple pulse for alight alerts. */
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

@Composable
private fun StationCard(
    card: HomeStationCardUi,
    onStationClick: () -> Unit,
    onBannerClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(NeonSurface, RoundedCornerShape(12.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onStationClick),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = card.stationName,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "${card.crsCode} · ${card.reasonLabel}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = NeonMuted,
                )
            }
            card.fetchedLabel?.let {
                Text(text = it, style = MaterialTheme.typography.bodyMedium, color = NeonMuted)
            }
        }

        DisruptionBanner(
            disruption = card.disruption,
            onClick = onBannerClick,
        )

        if (card.boardError != null && card.nextTrains.isEmpty()) {
            Text(text = card.boardError, color = NeonMagenta, style = MaterialTheme.typography.bodyMedium)
        } else if (card.nextTrains.isEmpty()) {
            Text(
                text = stringResource(R.string.home_no_trains),
                color = NeonMuted,
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            Text(
                text = stringResource(R.string.home_next_trains),
                style = MaterialTheme.typography.bodyMedium,
                color = NeonMuted,
            )
            card.nextTrains.forEach { train ->
                TrainRow(train)
            }
        }
    }
}

@Composable
private fun DisruptionBanner(
    disruption: DisruptionSummary,
    onClick: () -> Unit,
) {
    val color = severityColor(disruption.severity)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(color.copy(alpha = 0.16f), RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = severityDot(disruption.severity),
            color = color,
            style = MaterialTheme.typography.bodyLarge,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = disruption.headline,
                style = MaterialTheme.typography.bodyLarge,
                color = color,
            )
            Text(
                text = stringResource(R.string.home_banner_tap_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = NeonMuted,
            )
        }
    }
}

@Composable
private fun TrainRow(train: HomeDepartureChipUi) {
    val statusColor = when (train.statusKind) {
        StatusKind.OnTime -> NeonCyan
        StatusKind.Delayed -> NeonAmber
        StatusKind.Cancelled -> NeonMagenta
        StatusKind.Other -> NeonMuted
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(NeonBackground, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = train.timeLabel,
            color = NeonCyan,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(end = 10.dp),
        )
        Text(
            text = train.destinationLabel,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = train.statusLabel,
            color = statusColor,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun DisruptionDialog(
    stationName: String,
    disruption: DisruptionSummary,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NeonSurface,
        title = {
            Text(
                text = stationName.ifBlank { stringResource(R.string.home_disruption_title) },
                color = NeonCyan,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = disruption.headline, color = severityColor(disruption.severity))
                Text(text = disruption.detail, color = MaterialTheme.colorScheme.onSurface)
                if (disruption.affectedOperators.isNotEmpty()) {
                    Text(
                        text = stringResource(
                            R.string.home_disruption_operators,
                            disruption.affectedOperators.joinToString(", "),
                        ),
                        color = NeonMuted,
                    )
                }
                Text(
                    text = stringResource(R.string.home_disruption_source),
                    color = NeonMuted,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.home_disruption_close), color = NeonCyan)
            }
        },
    )
}

private fun severityColor(severity: DisruptionSeverity): Color = when (severity) {
    DisruptionSeverity.Green -> NeonGreen
    DisruptionSeverity.Amber -> NeonAmber
    DisruptionSeverity.Red -> NeonMagenta
}

private fun severityDot(severity: DisruptionSeverity): String = when (severity) {
    DisruptionSeverity.Green -> "●"
    DisruptionSeverity.Amber -> "●"
    DisruptionSeverity.Red -> "●"
}
