package com.ukrailtracker.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ukrailtracker.app.R
import com.ukrailtracker.app.ui.theme.NeonMuted
import com.ukrailtracker.app.ui.theme.NeonSurface

private const val MPS_TO_MPH = 2.236936

@Composable
fun SpeedStatusBar(
    speedMps: Double?,
    engineStatus: String,
    modifier: Modifier = Modifier,
) {
    val speedText = if (speedMps != null) {
        stringResource(R.string.status_speed_mph, speedMps * MPS_TO_MPH)
    } else {
        stringResource(R.string.status_speed_unknown)
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(NeonSurface)
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = speedText,
            style = MaterialTheme.typography.bodySmall,
            color = NeonMuted,
        )
        Text(
            text = engineStatus,
            style = MaterialTheme.typography.bodySmall,
            color = NeonMuted,
        )
    }
}
