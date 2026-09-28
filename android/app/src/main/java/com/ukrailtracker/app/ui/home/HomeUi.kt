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
 * Status-bar chrome only — never chooses the home body.
 * Permission / near / not-at-station keep [HomeBodyUi.StationList];
 * at-station / boarding / on-journey pair with a trip [HomeBodyUi].
 */
sealed interface PresenceChromeUi {
    data object NeedsPermission : PresenceChromeUi
    data object LocationUnavailable : PresenceChromeUi
    /** Not inside a station geofence — status bar shows "Not at a station". */
    data object NotAtStation : PresenceChromeUi

    data class NearStation(
        val crsCode: String,
        val stationName: String,
        val distanceLabel: String,
    ) : PresenceChromeUi

    data class AtStation(
        val crsCode: String,
        val stationName: String,
        val distanceLabel: String?,
        val manualOverride: Boolean,
    ) : PresenceChromeUi

    data class Boarding(
        val originName: String,
        val assumedDepartureLabel: String,
        val assumedDestinationName: String,
    ) : PresenceChromeUi

    data class OnJourney(
        val destinationName: String,
        val originCrs: String,
        val destinationCrs: String,
        val preDeparture: PreDepartureUi?,
        val showJourneyProgress: Boolean,
        val stoppedAtStation: Boolean,
        val currentStopName: String?,
        val nextStopName: String?,
    ) : PresenceChromeUi
}

/**
 * Exactly one home body. Station boards exist only on [StationList].
 */
sealed interface HomeBodyUi {
    data class StationList(
        val stations: List<HomeStationCardUi>,
    ) : HomeBodyUi

    data class AtStation(
        val crsCode: String,
        val stationName: String,
        val distanceLabel: String?,
        val manualOverride: Boolean,
        val destinations: List<WalkUpDestinationUi>,
        val destinationsLoading: Boolean,
        val noFavouritesConfigured: Boolean,
        val platformWatch: PlatformWatchUi? = null,
    ) : HomeBodyUi

    data class BoardingPrompt(
        val originCrs: String,
        val originName: String,
        val assumed: BoardingCandidateUi,
        val alternatives: List<BoardingCandidateUi>,
        val showPicker: Boolean,
    ) : HomeBodyUi

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
        val preDeparture: PreDepartureUi? = null,
        val showJourneyProgress: Boolean = true,
    ) : HomeBodyUi
}

/** Session + aux mapped to chrome and optional trip body (null → [HomeBodyUi.StationList]). */
data class MappedCommutePresence(
    val chrome: PresenceChromeUi,
    val tripBody: HomeBodyUi?,
)

sealed interface HomeUiState {
    data object Loading : HomeUiState
    data class Empty(val messageKey: EmptyReason) : HomeUiState
    data class Error(val message: String) : HomeUiState

    data class Ready(
        val chrome: PresenceChromeUi,
        val body: HomeBodyUi,
        val refreshing: Boolean = false,
        val selectedDisruption: DisruptionSummary? = null,
        val selectedStationName: String? = null,
    ) : HomeUiState
}

/** Station cards when the body is the list; empty otherwise. */
val HomeUiState.Ready.stationList: List<HomeStationCardUi>
    get() = (body as? HomeBodyUi.StationList)?.stations.orEmpty()

enum class EmptyReason {
    NoStationsConfigured,
}
