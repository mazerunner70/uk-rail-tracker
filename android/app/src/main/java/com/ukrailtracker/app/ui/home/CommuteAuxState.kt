package com.ukrailtracker.app.ui.home

import com.ukrailtracker.app.domain.usecase.ProgressPointUiModel
import com.ukrailtracker.app.ui.station.StatusKind

/** Overlay on top of session-derived presence; not part of [com.ukrailtracker.app.domain.usecase.CommuteSession]. */
enum class PresenceOverlay {
    None,
    NeedsPermission,
    LocationUnavailable,
}

/** At-station data loaded asynchronously; keyed to the locked origin CRS. */
data class AtStationAux(
    val originCrs: String? = null,
    val destinations: List<WalkUpDestinationUi> = emptyList(),
    val destinationsLoading: Boolean = false,
    val noFavouritesConfigured: Boolean = false,
    val platformWatch: PlatformWatchUi? = null,
)

/** On-train progress and Darwin snapshot fields not stored on [com.ukrailtracker.app.domain.usecase.CommuteSession.OnTrain]. */
data class OnTrainUiAux(
    val destinationName: String? = null,
    val minutesRemaining: Long? = null,
    val nextStopName: String? = null,
    val currentStopName: String? = null,
    val delayMinutes: Int? = null,
    val statusKind: StatusKind = StatusKind.Other,
    val routeMismatch: Boolean = false,
    val mismatchStationName: String? = null,
    val expectedNextStopName: String? = null,
    val points: List<ProgressPointUiModel> = emptyList(),
    val fractionComplete: Float = 0f,
    val predictedDurationMinutes: Int? = null,
    val stoppedAtStation: Boolean = false,
    val refreshing: Boolean = true,
    val errorMessage: String? = null,
    val serviceMissing: Boolean = false,
)

/**
 * UI data that accompanies [com.ukrailtracker.app.domain.usecase.CommuteSession] but is not part of the reducer.
 * Mapped to [WalkUpHomeUi] via [mapCommuteWalkUpUi].
 */
data class CommuteAuxState(
    val overlay: PresenceOverlay = PresenceOverlay.None,
    val atStation: AtStationAux = AtStationAux(),
    val onTrain: OnTrainUiAux = OnTrainUiAux(),
)
