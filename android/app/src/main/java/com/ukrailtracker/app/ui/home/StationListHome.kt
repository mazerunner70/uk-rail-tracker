package com.ukrailtracker.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ukrailtracker.app.R
import com.ukrailtracker.app.domain.model.DisruptionSeverity
import com.ukrailtracker.app.domain.model.DisruptionSummary
import com.ukrailtracker.app.ui.station.StatusKind
import com.ukrailtracker.app.ui.theme.NeonAmber
import com.ukrailtracker.app.ui.theme.NeonBackground
import com.ukrailtracker.app.ui.theme.NeonCyan
import com.ukrailtracker.app.ui.theme.NeonGreen
import com.ukrailtracker.app.ui.theme.NeonMagenta
import com.ukrailtracker.app.ui.theme.NeonMuted
import com.ukrailtracker.app.ui.theme.NeonSurface

internal fun LazyListScope.stationListHomeItems(
    stations: List<HomeStationCardUi>,
    onStationClick: (String) -> Unit,
    onBannerClick: (String) -> Unit,
    onOpenSettings: () -> Unit,
) {
    if (stations.isNotEmpty()) {
        item {
            Text(
                text = stringResource(R.string.home_my_stations),
                style = MaterialTheme.typography.bodyMedium,
                color = NeonMuted,
            )
        }
        items(stations, key = { it.crsCode }) { card ->
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
}

@Composable
internal fun DisruptionDialog(
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
