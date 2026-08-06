package com.ukrailtracker.app.ui.journey

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ukrailtracker.app.R
import com.ukrailtracker.app.appContainer
import com.ukrailtracker.app.domain.model.DisruptionSeverity
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
fun JourneyOptionsRoute(
    originCrs: String,
    destinationCrs: String,
    onBack: () -> Unit,
    onOpenService: (serviceId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val container = context.appContainer()
    val viewModel: JourneyOptionsViewModel = viewModel(
        factory = JourneyOptionsViewModel.factory(
            originCrs = originCrs,
            destinationCrs = destinationCrs,
            stationRepository = container.stationRepository,
            journeyRepository = container.journeyRepository,
            recentJourneysStore = container.recentJourneysStore,
            favouriteJourneysStore = container.favouriteJourneysStore,
        ),
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val title = when (val s = state) {
        is JourneyOptionsUiState.Content -> "${s.originCrs} → ${s.destinationCrs}"
        else -> stringResource(R.string.journey_options_title)
    }

    AppScreen(
        title = title,
        modifier = modifier,
        onBack = onBack,
        actions = {
            if (state is JourneyOptionsUiState.Content) {
                val content = state as JourneyOptionsUiState.Content
                AppBarIconButton(
                    imageVector = if (content.isFavourite) {
                        Icons.Filled.Star
                    } else {
                        Icons.Outlined.StarBorder
                    },
                    contentDescription = stringResource(R.string.journeys_toggle_favourite),
                    onClick = viewModel::toggleFavourite,
                    tint = if (content.isFavourite) NeonMagenta else NeonMuted,
                )
                AppBarIconButton(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = stringResource(R.string.journey_refresh),
                    onClick = viewModel::refresh,
                )
            }
        },
    ) {
        when (val s = state) {
            JourneyOptionsUiState.Loading -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator(color = NeonCyan)
                }
            }
            is JourneyOptionsUiState.Error -> {
                Text(
                    text = s.message,
                    color = NeonMagenta,
                    modifier = Modifier.padding(24.dp),
                )
            }
            is JourneyOptionsUiState.Content -> {
                JourneyOptionsContent(state = s, onOpenService = onOpenService)
            }
        }
    }
}

@Composable
private fun JourneyOptionsContent(
    state: JourneyOptionsUiState.Content,
    onOpenService: (String) -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item {
            Text(
                text = "${state.originName} → ${state.destinationName}",
                color = NeonCyan,
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.journey_options_direct_only),
                color = NeonMuted,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        item {
            RouteBanner(headline = state.routeHeadline, severity = state.routeSeverity)
        }
        state.error?.let {
            item {
                Text(text = it, color = NeonMagenta, style = MaterialTheme.typography.bodySmall)
            }
        }
        if (state.refreshing) {
            item {
                Text(
                    text = stringResource(R.string.journey_refreshing),
                    color = NeonMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        if (state.options.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.journey_options_empty),
                    color = NeonMuted,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            items(state.options, key = { it.serviceId }) { option ->
                OptionRow(option = option, onClick = { onOpenService(option.serviceId) })
            }
        }
    }
}

@Composable
private fun RouteBanner(headline: String, severity: DisruptionSeverity) {
    val color = when (severity) {
        DisruptionSeverity.Green -> NeonGreen
        DisruptionSeverity.Amber -> NeonAmber
        DisruptionSeverity.Red -> NeonMagenta
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(NeonSurface, RoundedCornerShape(12.dp))
            .padding(14.dp),
    ) {
        Text(
            text = stringResource(R.string.journey_route_status),
            color = NeonMuted,
            style = MaterialTheme.typography.labelMedium,
        )
        Text(text = headline, color = color, style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
private fun OptionRow(option: JourneyOptionRowUi, onClick: () -> Unit) {
    val statusColor = when (option.statusKind) {
        StatusKind.OnTime -> NeonGreen
        StatusKind.Delayed -> NeonAmber
        StatusKind.Cancelled -> NeonMagenta
        StatusKind.Other -> NeonMuted
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (option.flagged) NeonSurface.copy(alpha = 0.95f) else NeonSurface,
                RoundedCornerShape(12.dp),
            )
            .clickable(onClick = onClick)
            .padding(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = option.departureLabel,
                color = NeonCyan,
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = option.statusLabel,
                color = statusColor,
                style = MaterialTheme.typography.titleSmall,
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.journey_arrives, option.arrivalLabel),
            color = NeonMuted,
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = "${option.platformLabel} · ${option.operatorLabel}",
            color = NeonMuted,
            style = MaterialTheme.typography.bodySmall,
        )
        if (option.flagged) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.journey_flagged),
                color = NeonMagenta,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}
