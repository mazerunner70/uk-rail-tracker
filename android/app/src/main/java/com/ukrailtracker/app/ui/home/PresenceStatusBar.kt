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
    chrome: PresenceChromeUi,
    onAllowLocation: () -> Unit,
    onChangeStation: () -> Unit,
    onClearOverride: () -> Unit,
    onOriginClick: (String) -> Unit,
    showBackgroundLocationCta: Boolean = false,
    onAllowBackgroundLocation: () -> Unit = {},
) {
    val (label, labelColor) = when (chrome) {
        PresenceChromeUi.NeedsPermission,
        PresenceChromeUi.LocationUnavailable,
        PresenceChromeUi.NotAtStation,
        -> stringResource(R.string.home_presence_not_at_station) to NeonMuted
        is PresenceChromeUi.NearStation ->
            stringResource(R.string.home_presence_near, chrome.stationName) to NeonAmber
        is PresenceChromeUi.AtStation ->
            stringResource(R.string.home_presence_at, chrome.stationName) to NeonCyan
        is PresenceChromeUi.Boarding ->
            stringResource(R.string.home_boarding_presence, chrome.originName) to NeonAmber
        is PresenceChromeUi.OnJourney -> when {
            chrome.preDeparture?.awaitingDeparture == true ->
                stringResource(R.string.home_on_train_awaiting_departure_title) to NeonCyan
            chrome.preDeparture != null && !chrome.showJourneyProgress ->
                stringResource(
                    R.string.home_on_train_presence_waiting,
                    chrome.preDeparture.originStationName,
                ) to NeonAmber
            else ->
                stringResource(R.string.home_on_train_presence, chrome.destinationName) to NeonCyan
        }
    }
    val subtitle = when (chrome) {
        is PresenceChromeUi.NearStation -> "${chrome.crsCode} · ${chrome.distanceLabel}"
        is PresenceChromeUi.AtStation -> buildString {
            append(chrome.crsCode)
            if (chrome.manualOverride) {
                append(" · ")
                append(stringResource(R.string.home_presence_manual))
            } else {
                chrome.distanceLabel?.let {
                    append(" · ")
                    append(it)
                }
            }
        }
        is PresenceChromeUi.Boarding -> stringResource(
            R.string.home_boarding_presence_subtitle,
            chrome.assumedDepartureLabel,
            chrome.assumedDestinationName,
        )
        is PresenceChromeUi.OnJourney -> when {
            chrome.preDeparture?.awaitingDeparture == true ->
                stringResource(R.string.home_on_train_presence_awaiting_departure)
            chrome.preDeparture != null && !chrome.showJourneyProgress -> {
                val mins = chrome.preDeparture.minutesUntilTrainArrives
                if (mins != null && mins >= 0) {
                    stringResource(R.string.home_on_train_presence_train_in, mins)
                } else {
                    chrome.originCrs
                }
            }
            chrome.stoppedAtStation && !chrome.currentStopName.isNullOrBlank() ->
                stringResource(R.string.home_on_train_presence_at_stop, chrome.currentStopName)
            !chrome.nextStopName.isNullOrBlank() ->
                stringResource(R.string.home_on_train_presence_next, chrome.nextStopName)
            else -> chrome.destinationCrs
        }
        PresenceChromeUi.NotAtStation,
        PresenceChromeUi.LocationUnavailable,
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
                    if (chrome is PresenceChromeUi.AtStation) {
                        Modifier.clickable { onOriginClick(chrome.crsCode) }
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
        when (chrome) {
            PresenceChromeUi.NeedsPermission -> {
                TextButton(onClick = onAllowLocation) {
                    Text(stringResource(R.string.home_presence_allow_location), color = NeonCyan)
                }
                TextButton(onClick = onChangeStation) {
                    Text(stringResource(R.string.home_presence_set_station), color = NeonMuted)
                }
            }
            PresenceChromeUi.LocationUnavailable,
            PresenceChromeUi.NotAtStation,
            is PresenceChromeUi.NearStation,
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
            is PresenceChromeUi.AtStation -> {
                TextButton(onClick = onClearOverride) {
                    Text(stringResource(R.string.home_presence_cancel_station), color = NeonCyan)
                }
            }
            is PresenceChromeUi.Boarding -> {
                // Actions live in the boarding prompt body.
            }
            is PresenceChromeUi.OnJourney -> {
                // Actions live in the on-train body.
            }
        }
    }
}
