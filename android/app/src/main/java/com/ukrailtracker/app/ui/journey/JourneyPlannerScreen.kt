package com.ukrailtracker.app.ui.journey

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ukrailtracker.app.R
import com.ukrailtracker.app.appContainer
import com.ukrailtracker.app.ui.components.AppScreen
import com.ukrailtracker.app.ui.theme.NeonBackground
import com.ukrailtracker.app.ui.theme.NeonCyan
import com.ukrailtracker.app.ui.theme.NeonMagenta
import com.ukrailtracker.app.ui.theme.NeonMuted
import com.ukrailtracker.app.ui.theme.NeonSurface

@Composable
fun JourneyPlannerRoute(
    pendingOriginCrs: String?,
    pendingDestinationCrs: String?,
    onConsumePendingOrigin: () -> Unit,
    onConsumePendingDestination: () -> Unit,
    onPickOrigin: () -> Unit,
    onPickDestination: () -> Unit,
    onSearch: (originCrs: String, destinationCrs: String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val container = context.appContainer()
    val viewModel: JourneyPlannerViewModel = viewModel(
        factory = JourneyPlannerViewModel.factory(container.stationRepository),
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(pendingOriginCrs) {
        if (pendingOriginCrs != null) {
            viewModel.setOriginCrs(pendingOriginCrs)
            onConsumePendingOrigin()
        }
    }
    LaunchedEffect(pendingDestinationCrs) {
        if (pendingDestinationCrs != null) {
            viewModel.setDestinationCrs(pendingDestinationCrs)
            onConsumePendingDestination()
        }
    }

    AppScreen(
        title = stringResource(R.string.journey_planner_title),
        modifier = modifier,
        onBack = onBack,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.journey_planner_body),
                color = NeonMuted,
                style = MaterialTheme.typography.bodyMedium,
            )

            StationPickField(
                label = stringResource(R.string.journey_origin),
                station = state.origin,
                onClick = onPickOrigin,
            )

            IconButton(onClick = viewModel::swap) {
                Icon(
                    imageVector = Icons.Default.SwapVert,
                    contentDescription = stringResource(R.string.journey_swap),
                    tint = NeonCyan,
                )
            }

            StationPickField(
                label = stringResource(R.string.journey_destination),
                station = state.destination,
                onClick = onPickDestination,
            )

            state.error?.let {
                Text(text = it, color = NeonMagenta, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    if (viewModel.validateOrError()) {
                        val current = viewModel.uiState.value
                        onSearch(current.origin!!.crsCode, current.destination!!.crsCode)
                    }
                },
                enabled = state.canSearch,
                colors = ButtonDefaults.buttonColors(
                    containerColor = NeonCyan,
                    contentColor = NeonBackground,
                    disabledContainerColor = NeonSurface,
                    disabledContentColor = NeonMuted,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.journey_find_services))
            }
        }
    }
}

@Composable
private fun StationPickField(
    label: String,
    station: PlannerStationUi?,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(NeonSurface, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
    ) {
        Text(label, color = NeonMuted, style = MaterialTheme.typography.labelMedium)
        Spacer(modifier = Modifier.height(4.dp))
        if (station == null) {
            Text(
                text = stringResource(R.string.journey_pick_station),
                color = NeonCyan,
                style = MaterialTheme.typography.titleMedium,
            )
        } else {
            Text(
                text = station.name,
                color = NeonCyan,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = station.crsCode,
                color = NeonMuted,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
