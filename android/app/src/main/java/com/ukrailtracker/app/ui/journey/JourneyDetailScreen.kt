package com.ukrailtracker.app.ui.journey

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
fun JourneyDetailRoute(
    originCrs: String,
    destinationCrs: String,
    serviceId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val container = context.appContainer()
    val viewModel: JourneyDetailViewModel = viewModel(
        factory = JourneyDetailViewModel.factory(
            originCrs = originCrs,
            destinationCrs = destinationCrs,
            serviceId = serviceId,
            stationRepository = container.stationRepository,
            journeyRepository = container.journeyRepository,
            pinnedJourneyStore = container.pinnedJourneyStore,
            journeyLogRepository = container.journeyLogRepository,
        ),
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    AppScreen(
        title = stringResource(R.string.journey_detail_title),
        modifier = modifier,
        onBack = onBack,
        actions = {
            if (state is JourneyDetailUiState.Content) {
                AppBarIconButton(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = stringResource(R.string.journey_refresh),
                    onClick = viewModel::refresh,
                )
            }
        },
    ) {
        when (val s = state) {
            JourneyDetailUiState.Loading -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator(color = NeonCyan)
                }
            }
            is JourneyDetailUiState.Error -> {
                Text(
                    text = s.message,
                    color = NeonMagenta,
                    modifier = Modifier.padding(24.dp),
                )
            }
            is JourneyDetailUiState.Content -> {
                JourneyDetailContent(
                    detail = s.detail,
                    onTogglePin = viewModel::togglePin,
                )
            }
        }
    }
}

@Composable
private fun JourneyDetailContent(
    detail: JourneyDetailUi,
    onTogglePin: () -> Unit,
) {
    val statusColor = when (detail.statusKind) {
        StatusKind.OnTime -> NeonGreen
        StatusKind.Delayed -> NeonAmber
        StatusKind.Cancelled -> NeonMagenta
        StatusKind.Other -> NeonMuted
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item {
            Text(
                text = "${detail.originName} → ${detail.destinationName}",
                color = NeonCyan,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "${detail.originCrs} → ${detail.destinationCrs}",
                color = NeonMuted,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(NeonSurface, RoundedCornerShape(12.dp))
                    .padding(16.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text(stringResource(R.string.journey_departs), color = NeonMuted, style = MaterialTheme.typography.labelMedium)
                        Text(detail.departureScheduled, color = NeonCyan, style = MaterialTheme.typography.headlineSmall)
                        Text(detail.departureExpected, color = statusColor, style = MaterialTheme.typography.bodyMedium)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(stringResource(R.string.journey_arrives_label), color = NeonMuted, style = MaterialTheme.typography.labelMedium)
                        Text(detail.arrivalScheduled, color = NeonCyan, style = MaterialTheme.typography.headlineSmall)
                        Text(detail.arrivalExpected, color = statusColor, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text("${detail.platformLabel} · ${detail.operatorLabel}", color = NeonMuted)
                Text(detail.statusLabel, color = statusColor, style = MaterialTheme.typography.titleSmall)
                if (detail.flagged) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.journey_flagged),
                        color = NeonMagenta,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                detail.lastUpdatedLabel?.let {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(it, color = NeonMuted, style = MaterialTheme.typography.bodySmall)
                }
                detail.error?.let {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(it, color = NeonMagenta, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        item {
            Button(
                onClick = onTogglePin,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (detail.isPinned) NeonMagenta else NeonCyan,
                    contentColor = NeonBackground,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = if (detail.isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                    contentDescription = null,
                )
                Spacer(modifier = Modifier.padding(horizontal = 6.dp))
                Text(
                    text = stringResource(
                        if (detail.isPinned) R.string.journey_unpin else R.string.journey_pin,
                    ),
                )
            }
        }

        item {
            Text(
                text = stringResource(R.string.journey_calling_points),
                color = NeonCyan,
                style = MaterialTheme.typography.titleMedium,
            )
        }

        if (detail.callingPoints.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.journey_calling_points_empty),
                    color = NeonMuted,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            items(detail.callingPoints, key = { "${it.crs}-${it.name}-${it.scheduledLabel}" }) { point ->
                CallingPointRow(point)
            }
        }
    }
}

@Composable
private fun CallingPointRow(point: CallingPointRowUi) {
    val nameColor = when {
        point.isCancelled -> NeonMagenta
        point.isDestination -> NeonCyan
        else -> NeonMuted
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(NeonSurface, RoundedCornerShape(10.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(point.name, color = nameColor, style = MaterialTheme.typography.bodyLarge)
            point.crs?.let {
                Text(it, color = NeonMuted, style = MaterialTheme.typography.bodySmall)
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(point.scheduledLabel, color = NeonCyan, style = MaterialTheme.typography.bodyMedium)
            Text(
                point.expectedLabel,
                color = if (point.isCancelled) NeonMagenta else NeonMuted,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
