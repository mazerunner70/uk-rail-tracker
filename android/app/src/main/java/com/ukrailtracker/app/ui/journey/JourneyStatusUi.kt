package com.ukrailtracker.app.ui.journey

import com.ukrailtracker.app.domain.model.TrainStatus
import com.ukrailtracker.app.ui.station.StatusKind

internal fun statusKind(status: TrainStatus): StatusKind = when (status) {
    TrainStatus.OnTime -> StatusKind.OnTime
    TrainStatus.Delayed -> StatusKind.Delayed
    TrainStatus.Cancelled -> StatusKind.Cancelled
    else -> StatusKind.Other
}

internal fun statusLabel(status: TrainStatus, expectedLabel: String, delayMinutes: Int?): String =
    when (status) {
        TrainStatus.OnTime -> "On time"
        TrainStatus.Cancelled -> "Cancelled"
        TrainStatus.Delayed -> {
            val delay = delayMinutes
            if (delay != null && delay > 0) "+$delay min" else expectedLabel
        }
        TrainStatus.Departed -> "Departed"
        TrainStatus.Unknown -> expectedLabel.ifBlank { "—" }
    }
