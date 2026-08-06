package com.ukrailtracker.app.ui.home

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ukrailtracker.app.R
import com.ukrailtracker.app.appContainer
import com.ukrailtracker.app.domain.model.DisruptionSeverity
import com.ukrailtracker.app.domain.model.DisruptionSummary
import com.ukrailtracker.app.domain.usecase.FavouriteServiceUiModel
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

@Composable
fun HomeRoute(
    onStationClick: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onChangeWalkUpStation: () -> Unit,
    onJourneyServiceClick: (originCrs: String, destCrs: String, serviceId: String) -> Unit,
    pendingWalkUpOverrideCrs: String? = null,
    onWalkUpOverrideConsumed: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val container = context.appContainer()
    val viewModel: HomeViewModel = viewModel(
        factory = HomeViewModel.factory(
            stationRepository = container.stationRepository,
            departureRepository = container.departureRepository,
            journeyRepository = container.journeyRepository,
            locationProvider = container.locationProvider,
            commuteWindowStore = container.commuteWindowStore,
            favouritesStore = container.favouritesStore,
        ),
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        val granted = result.values.any { it }
        if (granted) {
            viewModel.onPermissionGranted()
        } else {
            viewModel.onPermissionDenied()
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

    LaunchedEffect(Unit) {
        if (hasLocationPermission()) {
            viewModel.onPermissionGranted()
        } else {
            viewModel.onNeedsPermission()
        }
    }

    LaunchedEffect(pendingWalkUpOverrideCrs) {
        val crs = pendingWalkUpOverrideCrs ?: return@LaunchedEffect
        viewModel.setOriginOverride(crs)
        onWalkUpOverrideConsumed()
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

    AppScreen(
        title = stringResource(R.string.home_title),
        modifier = modifier,
        actions = {
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
                    onServiceClick = onJourneyServiceClick,
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
        }
    }
}

@Composable
private fun HomeContent(
    content: HomeUiState.Content,
    onStationClick: (String) -> Unit,
    onBannerClick: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onServiceClick: (originCrs: String, destCrs: String, serviceId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val atStation = content.walkUp as? WalkUpHomeUi.AtStation

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

        if (atStation != null) {
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
                    }
                    items(atStation.destinations, key = { it.destinationCrs }) { dest ->
                        FavouriteDestinationBlock(
                            destination = dest,
                            originCrs = atStation.crsCode,
                            onServiceClick = onServiceClick,
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
private fun FavouriteDestinationBlock(
    destination: WalkUpDestinationUi,
    originCrs: String,
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
    onClick: () -> Unit,
) {
    val statusColor = when {
        service.isCancelled -> NeonMagenta
        (service.delayMinutes ?: 0) > 0 -> NeonAmber
        else -> NeonCyan
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(NeonBackground, RoundedCornerShape(8.dp))
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
                text = service.platform?.let { stringResource(R.string.home_walkup_platform, it) }
                    ?: stringResource(R.string.home_walkup_platform_unknown),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyMedium,
            )
            service.durationMinutes?.let { mins ->
                Text(
                    text = stringResource(R.string.home_walkup_duration, mins),
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
