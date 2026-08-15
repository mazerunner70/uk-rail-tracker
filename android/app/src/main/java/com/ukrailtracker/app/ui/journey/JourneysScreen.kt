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
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ukrailtracker.app.R
import com.ukrailtracker.app.appContainer
import com.ukrailtracker.app.domain.usecase.JourneyHistoryMode
import com.ukrailtracker.app.ui.components.AppScreen
import com.ukrailtracker.app.ui.theme.NeonAmber
import com.ukrailtracker.app.ui.theme.NeonBackground
import com.ukrailtracker.app.ui.theme.NeonCyan
import com.ukrailtracker.app.ui.theme.NeonGreen
import com.ukrailtracker.app.ui.theme.NeonMagenta
import com.ukrailtracker.app.ui.theme.NeonMuted
import com.ukrailtracker.app.ui.theme.NeonSurface

@Composable
fun JourneysRoute(
    onPlanJourney: () -> Unit,
    onOpenPair: (originCrs: String, destinationCrs: String) -> Unit,
    onOpenPinned: (originCrs: String, destinationCrs: String, serviceId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val container = context.appContainer()
    val viewModel: JourneysViewModel = viewModel(
        factory = JourneysViewModel.factory(
            stationRepository = container.stationRepository,
            favouriteJourneysStore = container.favouriteJourneysStore,
            recentJourneysStore = container.recentJourneysStore,
            pinnedJourneyStore = container.pinnedJourneyStore,
            journeyLogRepository = container.journeyLogRepository,
        ),
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    AppScreen(
        title = stringResource(R.string.journeys_title),
        modifier = modifier,
    ) {
        when (val s = state) {
            JourneysUiState.Loading -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator(color = NeonCyan)
                }
            }
            is JourneysUiState.Content -> {
                JourneysContent(
                    state = s,
                    onPlanJourney = onPlanJourney,
                    onOpenPair = onOpenPair,
                    onOpenPinned = onOpenPinned,
                    onToggleFavourite = viewModel::toggleFavourite,
                    onHistoryMode = viewModel::setHistoryMode,
                )
            }
        }
    }
}

@Composable
private fun JourneysContent(
    state: JourneysUiState.Content,
    onPlanJourney: () -> Unit,
    onOpenPair: (String, String) -> Unit,
    onOpenPinned: (String, String, String) -> Unit,
    onToggleFavourite: (String, String) -> Unit,
    onHistoryMode: (JourneyHistoryMode) -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item {
            Button(
                onClick = onPlanJourney,
                colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = NeonBackground),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.journeys_plan))
            }
        }

        state.pinned?.let { pinned ->
            item {
                Text(
                    text = stringResource(R.string.journeys_my_train),
                    style = MaterialTheme.typography.titleMedium,
                    color = NeonCyan,
                )
            }
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(NeonSurface, RoundedCornerShape(12.dp))
                        .clickable {
                            onOpenPinned(pinned.originCrs, pinned.destinationCrs, pinned.serviceId)
                        }
                        .padding(16.dp),
                ) {
                    Text(pinned.title, color = NeonCyan, style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(pinned.subtitle, color = NeonMuted, style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.journeys_pinned_hint),
                        color = NeonMagenta,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        if (state.favourites.isNotEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.journeys_favourites),
                    style = MaterialTheme.typography.titleMedium,
                    color = NeonCyan,
                )
            }
            items(state.favourites, key = { "fav-${it.originCrs}-${it.destinationCrs}" }) { pair ->
                JourneyPairRow(
                    pair = pair,
                    onClick = { onOpenPair(pair.originCrs, pair.destinationCrs) },
                    onToggleFavourite = { onToggleFavourite(pair.originCrs, pair.destinationCrs) },
                )
            }
        }

        item {
            Text(
                text = stringResource(R.string.journeys_recent),
                style = MaterialTheme.typography.titleMedium,
                color = NeonCyan,
            )
        }
        if (state.recent.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.journeys_recent_empty),
                    color = NeonMuted,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            items(state.recent, key = { "rec-${it.originCrs}-${it.destinationCrs}" }) { pair ->
                JourneyPairRow(
                    pair = pair,
                    onClick = { onOpenPair(pair.originCrs, pair.destinationCrs) },
                    onToggleFavourite = { onToggleFavourite(pair.originCrs, pair.destinationCrs) },
                )
            }
        }

        item {
            Text(
                text = stringResource(R.string.journeys_history),
                style = MaterialTheme.typography.titleMedium,
                color = NeonCyan,
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HistoryFilterChip(
                    selected = state.historyMode == JourneyHistoryMode.Claimable,
                    label = stringResource(R.string.journeys_history_filter_claimable),
                    onClick = { onHistoryMode(JourneyHistoryMode.Claimable) },
                )
                HistoryFilterChip(
                    selected = state.historyMode == JourneyHistoryMode.All,
                    label = stringResource(R.string.journeys_history_filter_all),
                    onClick = { onHistoryMode(JourneyHistoryMode.All) },
                )
            }
        }
        if (state.history.isEmpty()) {
            item {
                Text(
                    text = stringResource(
                        if (state.historyMode == JourneyHistoryMode.Claimable &&
                            state.historyTotalInWindow > 0
                        ) {
                            R.string.journeys_history_empty_claimable
                        } else {
                            R.string.journeys_history_empty
                        },
                    ),
                    color = NeonMuted,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            items(state.history, key = { "hist-${it.id}" }) { trip ->
                HistoryTripRow(
                    trip = trip,
                    onClick = { onOpenPair(trip.originCrs, trip.destinationCrs) },
                )
            }
        }
    }
}

@Composable
private fun HistoryFilterChip(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = NeonCyan.copy(alpha = 0.2f),
            selectedLabelColor = NeonCyan,
            labelColor = NeonMuted,
        ),
    )
}

@Composable
private fun HistoryTripRow(
    trip: HistoryTripUi,
    onClick: () -> Unit,
) {
    val statusColor = when (trip.statusKind) {
        HistoryStatusKind.OnTime -> NeonGreen
        HistoryStatusKind.Delayed -> NeonAmber
        HistoryStatusKind.Cancelled -> NeonMagenta
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(NeonSurface, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = trip.dateLabel,
                color = NeonMuted,
                style = MaterialTheme.typography.bodySmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (trip.claimable) {
                    Text(
                        text = stringResource(R.string.journeys_history_claimable_badge),
                        color = NeonAmber,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Text(
                    text = trip.statusLabel,
                    color = statusColor,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Text(
            text = "${trip.originName} → ${trip.destinationName}",
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = listOfNotNull(
                trip.departureLabel.takeIf { it.isNotBlank() },
                trip.operatorName.takeIf { it.isNotBlank() },
            ).joinToString(" · "),
            color = NeonMuted,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun JourneyPairRow(
    pair: JourneyPairUi,
    onClick: () -> Unit,
    onToggleFavourite: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(NeonSurface, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "${pair.originName} → ${pair.destinationName}",
                color = NeonCyan,
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = "${pair.originCrs} → ${pair.destinationCrs}",
                color = NeonMuted,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        IconButton(onClick = onToggleFavourite) {
            Icon(
                imageVector = if (pair.isFavourite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                contentDescription = stringResource(R.string.journeys_toggle_favourite),
                tint = if (pair.isFavourite) NeonMagenta else NeonMuted,
            )
        }
    }
}
