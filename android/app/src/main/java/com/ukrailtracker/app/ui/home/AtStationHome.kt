package com.ukrailtracker.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ukrailtracker.app.R
import com.ukrailtracker.app.domain.usecase.FavouriteServiceUiModel
import com.ukrailtracker.app.domain.usecase.JourneyTimeUtils
import com.ukrailtracker.app.ui.theme.NeonAmber
import com.ukrailtracker.app.ui.theme.NeonBackground
import com.ukrailtracker.app.ui.theme.NeonCyan
import com.ukrailtracker.app.ui.theme.NeonGreen
import com.ukrailtracker.app.ui.theme.NeonMagenta
import com.ukrailtracker.app.ui.theme.NeonMuted
import com.ukrailtracker.app.ui.theme.NeonSurface
import kotlinx.coroutines.delay
import java.time.LocalDateTime

internal fun LazyListScope.atStationHomeItems(
    atStation: HomeBodyUi.AtStation,
    onOpenSettings: () -> Unit,
    onSelectForPlatformWatch: (originCrs: String, destCrs: String, serviceId: String) -> Unit,
    onClearPlatformWatch: () -> Unit,
    onRefreshPlatformWatch: () -> Unit,
    onOpenWatchedService: () -> Unit,
) {
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
