package com.ukrailtracker.app.ui.nearby

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ukrailtracker.app.R
import com.ukrailtracker.app.appContainer
import com.ukrailtracker.app.ui.theme.NeonCyan
import com.ukrailtracker.app.ui.theme.NeonMuted
import com.ukrailtracker.app.ui.theme.NeonSurface
import com.ukrailtracker.app.ui.theme.UkRailTrackerTheme

@Composable
fun NearbyRoute(
    onStationClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val container = context.appContainer()
    val viewModel: NearbyViewModel = viewModel(
        factory = NearbyViewModel.factory(container.getNearbyStations),
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

    NearbyScreen(
        state = state,
        onAllowLocation = {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                ),
            )
        },
        onRefresh = {
            if (hasLocationPermission()) viewModel.refresh()
            else viewModel.onNeedsPermission()
        },
        onOpenSettings = {
            val intent = Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", context.packageName, null),
            )
            context.startActivity(intent)
        },
        onStationClick = onStationClick,
        modifier = modifier,
    )
}

@Composable
fun NearbyScreen(
    state: NearbyUiState,
    onAllowLocation: () -> Unit,
    onRefresh: () -> Unit,
    onOpenSettings: () -> Unit,
    onStationClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.nearby_title),
                style = MaterialTheme.typography.headlineMedium,
                color = NeonCyan,
                modifier = Modifier.weight(1f),
            )
            if (state is NearbyUiState.Content || state is NearbyUiState.LocationUnavailable) {
                IconButton(onClick = onRefresh) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = stringResource(R.string.nearby_refresh),
                        tint = NeonCyan,
                    )
                }
            }
        }

        when (state) {
            NearbyUiState.Importing -> StatusPane(
                message = stringResource(R.string.nearby_importing),
                showSpinner = true,
            )
            NearbyUiState.Loading -> StatusPane(
                message = stringResource(R.string.nearby_loading),
                showSpinner = true,
            )
            NearbyUiState.NeedsPermission -> PermissionPane(
                title = stringResource(R.string.nearby_permission_title),
                body = stringResource(R.string.nearby_permission_body),
                primaryLabel = stringResource(R.string.nearby_permission_allow),
                onPrimary = onAllowLocation,
            )
            NearbyUiState.PermissionDenied -> PermissionPane(
                title = stringResource(R.string.nearby_permission_title),
                body = stringResource(R.string.nearby_permission_denied),
                primaryLabel = stringResource(R.string.nearby_open_settings),
                onPrimary = onOpenSettings,
            )
            NearbyUiState.LocationUnavailable -> StatusPane(
                message = stringResource(R.string.nearby_location_unavailable),
                actionLabel = stringResource(R.string.nearby_refresh),
                onAction = onRefresh,
            )
            is NearbyUiState.Error -> StatusPane(
                message = state.message ?: stringResource(R.string.nearby_error),
                actionLabel = stringResource(R.string.nearby_refresh),
                onAction = onRefresh,
            )
            is NearbyUiState.Content -> NearbyContent(
                state = state,
                onStationClick = onStationClick,
            )
        }
    }
}

@Composable
private fun NearbyContent(
    state: NearbyUiState.Content,
    onStationClick: (String) -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.nearby_closest),
                    style = MaterialTheme.typography.bodyMedium,
                    color = NeonMuted,
                )
                ClosestStationCard(
                    row = state.closest,
                    onClick = { onStationClick(state.closest.crsCode) },
                )
                if (state.isRefreshing) {
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
                            text = stringResource(R.string.nearby_loading),
                            color = NeonMuted,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
        if (state.others.isNotEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.nearby_other),
                    style = MaterialTheme.typography.bodyMedium,
                    color = NeonMuted,
                )
            }
            items(state.others, key = { it.crsCode }) { row ->
                StationRow(
                    row = row,
                    onClick = { onStationClick(row.crsCode) },
                )
            }
        }
    }
}

@Composable
private fun ClosestStationCard(
    row: NearbyStationRowUi,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(NeonSurface, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = row.name,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "${row.crsCode} · ${row.distanceLabel}",
            style = MaterialTheme.typography.bodyLarge,
            color = NeonCyan,
        )
        if (row.operator.isNotBlank()) {
            Text(
                text = row.operator,
                style = MaterialTheme.typography.bodyMedium,
                color = NeonMuted,
            )
        }
    }
}

@Composable
private fun StationRow(
    row: NearbyStationRowUi,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(NeonSurface, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = row.name,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = listOf(row.crsCode, row.operator)
                    .filter { it.isNotBlank() }
                    .joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = NeonMuted,
            )
        }
        Text(
            text = row.distanceLabel,
            style = MaterialTheme.typography.bodyLarge,
            color = NeonCyan,
        )
    }
}

@Composable
private fun StatusPane(
    message: String,
    showSpinner: Boolean = false,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(24.dp),
        ) {
            if (showSpinner) {
                CircularProgressIndicator(color = NeonCyan)
            }
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = NeonMuted,
            )
            if (actionLabel != null && onAction != null) {
                Button(onClick = onAction) {
                    Text(actionLabel)
                }
            }
        }
    }
}

@Composable
private fun PermissionPane(
    title: String,
    body: String,
    primaryLabel: String,
    onPrimary: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(24.dp),
        ) {
            Icon(
                imageVector = Icons.Default.MyLocation,
                contentDescription = null,
                tint = NeonCyan,
            )
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodyLarge,
                color = NeonMuted,
            )
            Button(onClick = onPrimary) {
                Text(primaryLabel)
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0B0F1A)
@Composable
private fun NearbyContentPreview() {
    UkRailTrackerTheme {
        NearbyScreen(
            state = NearbyUiState.Content(
                closest = NearbyStationRowUi(
                    crsCode = "PAD",
                    name = "London Paddington",
                    operator = "Network Rail",
                    distanceLabel = "120 m",
                ),
                others = listOf(
                    NearbyStationRowUi("MYB", "Marylebone", "Chiltern Railways", "1.2 km"),
                    NearbyStationRowUi("EUS", "London Euston", "Network Rail", "2.4 km"),
                ),
            ),
            onAllowLocation = {},
            onRefresh = {},
            onOpenSettings = {},
            onStationClick = {},
            modifier = Modifier.background(MaterialTheme.colorScheme.background),
        )
    }
}
