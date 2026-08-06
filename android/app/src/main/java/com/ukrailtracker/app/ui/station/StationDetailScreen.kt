package com.ukrailtracker.app.ui.station

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.ukrailtracker.app.domain.model.StationAccessibility
import com.ukrailtracker.app.ui.components.AppBarIconButton
import com.ukrailtracker.app.ui.components.AppScreen
import com.ukrailtracker.app.ui.theme.NeonCyan
import com.ukrailtracker.app.ui.theme.NeonMagenta
import com.ukrailtracker.app.ui.theme.NeonMuted
import com.ukrailtracker.app.ui.theme.NeonSurface

@Composable
fun StationDetailRoute(
    crsCode: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val container = context.appContainer()
    val viewModel: StationDetailViewModel = viewModel(
        factory = StationDetailViewModel.factory(
            crsCode = crsCode,
            stationRepository = container.stationRepository,
            departureRepository = container.departureRepository,
        ),
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val title = when (val s = state) {
        is StationDetailUiState.Content -> s.detail.station.name
        else -> stringResource(R.string.station_detail_title)
    }

    AppScreen(
        title = title,
        modifier = modifier,
        onBack = onBack,
        actions = {
            if (state is StationDetailUiState.Content) {
                AppBarIconButton(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = stringResource(R.string.station_board_refresh),
                    onClick = viewModel::refreshBoard,
                )
            }
        },
    ) {
        when (val s = state) {
            StationDetailUiState.Loading -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator(color = NeonCyan)
                }
            }
            StationDetailUiState.NotFound -> {
                Text(
                    text = stringResource(R.string.station_not_found),
                    color = NeonMuted,
                    modifier = Modifier.padding(24.dp),
                )
            }
            is StationDetailUiState.Content -> {
                StationDetailContent(
                    detail = s.detail,
                    onOpenMap = { url ->
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    },
                )
            }
        }
    }
}

@Composable
private fun StationDetailContent(
    detail: StationDetailUi,
    onOpenMap: (String) -> Unit,
) {
    val station = detail.station
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            DetailBlock(label = stringResource(R.string.station_crs), value = station.crsCode)
        }
        item {
            DetailBlock(
                label = stringResource(R.string.station_operator),
                value = listOf(station.operatorName, station.operatorCode)
                    .filter { it.isNotBlank() }
                    .joinToString(" · ")
                    .ifBlank { "—" },
            )
        }
        item {
            val address = buildList {
                addAll(station.addressLines)
                station.postcode?.let(::add)
            }.joinToString("\n")
            DetailBlock(
                label = stringResource(R.string.station_address),
                value = address.ifBlank { "—" },
            )
        }
        item {
            AccessibilityBlock(station.accessibility)
        }
        station.stationMapUrl?.let { url ->
            item {
                TextButton(onClick = { onOpenMap(url) }) {
                    Text(stringResource(R.string.station_open_map), color = NeonCyan)
                }
            }
        }
        item {
            BoardHeader(
                fetchedLabel = detail.boardFetchedLabel,
                stale = detail.boardStale,
                loading = detail.boardLoading || detail.boardRefreshing,
            )
        }
        when {
            detail.boardLoading && detail.boardRows.isEmpty() -> {
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = NeonCyan,
                        )
                        Text(
                            text = stringResource(R.string.station_board_loading),
                            color = NeonMuted,
                        )
                    }
                }
            }
            detail.boardError != null && detail.boardRows.isEmpty() -> {
                item {
                    Text(
                        text = detail.boardError,
                        color = NeonMagenta,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            detail.boardRows.isEmpty() -> {
                item {
                    Text(
                        text = stringResource(R.string.station_board_empty),
                        color = NeonMuted,
                    )
                }
            }
            else -> {
                if (detail.boardError != null) {
                    item {
                        Text(
                            text = detail.boardError,
                            color = NeonMagenta,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                items(detail.boardRows, key = { "${it.timeLabel}-${it.destinationLabel}-${it.platformLabel}" }) { row ->
                    DepartureRow(row)
                }
            }
        }
        item {
            Text(
                text = stringResource(R.string.powered_by_national_rail),
                style = MaterialTheme.typography.bodyMedium,
                color = NeonMuted,
                modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
            )
        }
    }
}

@Composable
private fun BoardHeader(
    fetchedLabel: String?,
    stale: Boolean,
    loading: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(R.string.station_board_title),
            style = MaterialTheme.typography.headlineMedium,
            color = NeonCyan,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                    color = NeonCyan,
                )
            }
            val meta = buildString {
                if (fetchedLabel != null) append(fetchedLabel)
                if (stale) {
                    if (isNotEmpty()) append(" · ")
                    append("stale")
                }
            }
            if (meta.isNotBlank()) {
                Text(text = meta, style = MaterialTheme.typography.bodyMedium, color = NeonMuted)
            }
        }
    }
}

@Composable
private fun DepartureRow(row: DepartureRowUi) {
    val statusColor = when (row.statusKind) {
        StatusKind.OnTime -> NeonCyan
        StatusKind.Delayed -> NeonMagenta
        StatusKind.Cancelled -> NeonMagenta
        StatusKind.Other -> NeonMuted
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(NeonSurface, RoundedCornerShape(10.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = row.timeLabel,
            style = MaterialTheme.typography.bodyLarge,
            color = NeonCyan,
            modifier = Modifier.padding(end = 12.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = row.destinationLabel,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = listOf(row.platformLabel, row.operatorLabel)
                    .filter { it.isNotBlank() }
                    .joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = NeonMuted,
            )
        }
        Text(
            text = row.statusLabel,
            style = MaterialTheme.typography.bodyMedium,
            color = statusColor,
        )
    }
}

@Composable
private fun AccessibilityBlock(accessibility: StationAccessibility) {
    val lines = buildList {
        accessibility.stepFreeCategory?.let { add("Step-free: $it") }
        accessibility.rampAvailable?.let { add(if (it) "Boarding ramp available" else "No boarding ramp listed") }
        accessibility.accessibleToiletsAvailable?.let {
            add(if (it) "Accessible toilets available" else "No accessible toilets listed")
        }
        accessibility.wheelchairsAvailable?.let {
            add(if (it) "Wheelchairs available" else "No station wheelchairs listed")
        }
        accessibility.tactilePaving?.let { add(it) }
    }
    DetailBlock(
        label = stringResource(R.string.station_accessibility),
        value = lines.joinToString("\n").ifBlank { "—" },
    )
}

@Composable
private fun DetailBlock(label: String, value: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(NeonSurface, RoundedCornerShape(12.dp))
            .padding(16.dp),
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = NeonMuted)
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = value, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
    }
}
