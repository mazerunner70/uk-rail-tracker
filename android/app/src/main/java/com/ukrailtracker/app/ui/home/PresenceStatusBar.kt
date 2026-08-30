package com.ukrailtracker.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ukrailtracker.app.R
import com.ukrailtracker.app.ui.theme.NeonAmber
import com.ukrailtracker.app.ui.theme.NeonCyan
import com.ukrailtracker.app.ui.theme.NeonMuted
import com.ukrailtracker.app.ui.theme.NeonSurface

@Composable
internal fun PresenceStatusBar(
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
                TextButton(onClick = onClearOverride) {
                    Text(stringResource(R.string.home_presence_cancel_station), color = NeonCyan)
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
