package com.ukrailtracker.app.ui.home

import com.ukrailtracker.app.domain.model.DisruptionSummary
import com.ukrailtracker.app.domain.usecase.FavouriteServiceUiModel
import com.ukrailtracker.app.domain.usecase.ProgressPointUiModel
import com.ukrailtracker.app.ui.station.StatusKind

data class HomeDepartureChipUi(
    val timeLabel: String,
    val destinationLabel: String,
    val statusLabel: String,
    val statusKind: StatusKind,
)

data class HomeStationCardUi(
    val crsCode: String,
    val stationName: String,
    val reasonLabel: String,
    val disruption: DisruptionSummary,
    val nextTrains: List<HomeDepartureChipUi>,
    val boardError: String? = null,
    val fetchedLabel: String? = null,
    val stale: Boolean = false,
)

data class WalkUpDestinationUi(
    val destinationCrs: String,
    val destinationName: String,
    val services: List<FavouriteServiceUiModel>,
    val errorMessage: String? = null,
)

/** Selected favourite-bound service being polled until a departure platform appears. */
data class PlatformWatchUi(
    val serviceId: String,
    val originCrs: String,
    val destinationCrs: String,
    val destinationName: String,
    val scheduledDepartureLabel: String,
    val expectedDepartureLabel: String = "",
    val platform: String?,
    /** Clock time (HH:mm) of the last successful board refresh. */
    val checkedAtLabel: String? = null,
    val polling: Boolean = false,
    val errorMessage: String? = null,
)

data class BoardingCandidateUi(
    val serviceId: String,
    val originCrs: String,
    val destinationCrs: String,
    val destinationName: String,
    val originName: String,
    val operatorName: String,
    val scheduledDepartureLabel: String,
    val expectedDepartureLabel: String,
    val platform: String?,
)

/**
 * Presence used by the Home status bar.
 * Default / sensing / permission issues keep the usual home body.
 * [AtStation] / [BoardingPrompt] replace the home body with trip-focused content.
 */
sealed interface WalkUpHomeUi {
    data object NeedsPermission : WalkUpHomeUi
    data object LocationUnavailable : WalkUpHomeUi
    /** Not inside a station geofence — status bar shows "Not at a station". */
    data object NotAtStation : WalkUpHomeUi
    data class NearStation(
        val crsCode: String,
        val stationName: String,
        val distanceLabel: String,
    ) : WalkUpHomeUi

    data class AtStation(
        val crsCode: String,
        val stationName: String,
        val distanceLabel: String?,
        val manualOverride: Boolean,
        val destinations: List<WalkUpDestinationUi>,
        val destinationsLoading: Boolean,
        val noFavouritesConfigured: Boolean,
        val platformWatch: PlatformWatchUi? = null,
    ) : WalkUpHomeUi

    data class BoardingPrompt(
        val originCrs: String,
        val originName: String,
        val assumed: BoardingCandidateUi,
        val alternatives: List<BoardingCandidateUi>,
        val showPicker: Boolean,
    ) : WalkUpHomeUi

    data class OnJourney(
        val serviceId: String,
        val originCrs: String,
        val destinationCrs: String,
        val destinationName: String,
        val scheduledDepartureLabel: String,
        val minutesRemaining: Long?,
        val nextStopName: String?,
        val currentStopName: String?,
        val delayMinutes: Int?,
        val statusKind: StatusKind,
        val routeMismatch: Boolean,
        val mismatchStationName: String?,
        val expectedNextStopName: String?,
        val points: List<ProgressPointUiModel>,
        val fractionComplete: Float,
        val predictedDurationMinutes: Int? = null,
        val stoppedAtStation: Boolean = false,
        val refreshing: Boolean = false,
        val errorMessage: String? = null,
        val serviceMissing: Boolean = false,
    ) : WalkUpHomeUi
}

val WalkUpHomeUi?.focusesAtStation: Boolean
    get() = this is WalkUpHomeUi.AtStation || this is WalkUpHomeUi.BoardingPrompt

val WalkUpHomeUi?.focusesTripUi: Boolean
    get() = focusesAtStation || this is WalkUpHomeUi.OnJourney

val WalkUpHomeUi?.focusesOnTrain: Boolean
    get() = this is WalkUpHomeUi.OnJourney

sealed interface HomeUiState {
    data object Loading : HomeUiState
    data class Empty(val messageKey: EmptyReason) : HomeUiState
    data class Content(
        val walkUp: WalkUpHomeUi? = null,
        val stations: List<HomeStationCardUi>,
        val refreshing: Boolean = false,
        val selectedDisruption: DisruptionSummary? = null,
        val selectedStationName: String? = null,
    ) : HomeUiState

    data class Error(val message: String) : HomeUiState
}

enum class EmptyReason {
    NoStationsConfigured,
}
