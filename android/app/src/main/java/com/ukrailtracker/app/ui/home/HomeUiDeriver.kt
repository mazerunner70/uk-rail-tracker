package com.ukrailtracker.app.ui.home

import com.ukrailtracker.app.domain.model.DisruptionSummary
import com.ukrailtracker.app.domain.usecase.CommuteSession

/** Station board list and refresh flags; separate from commute session (stage 2). */
data class StationBoardsState(
    val stations: List<HomeStationCardUi> = emptyList(),
    val refreshing: Boolean = false,
    val awaitingFirstLoad: Boolean = true,
    val noStationsConfigured: Boolean = false,
)

data class DisruptionModalState(
    val disruption: DisruptionSummary,
    val stationName: String,
)

fun deriveHomeUiState(
    session: CommuteSession,
    aux: CommuteAuxState,
    lastLoadedOriginCrs: String?,
    boards: StationBoardsState,
    disruptionModal: DisruptionModalState?,
): HomeUiState {
    if (boards.awaitingFirstLoad) return HomeUiState.Loading

    val walkUp = mapCommuteWalkUpUi(session, aux, lastLoadedOriginCrs)
    if (boards.noStationsConfigured && boards.stations.isEmpty()) {
        return if (walkUp != null) {
            HomeUiState.Content(
                walkUp = walkUp,
                stations = emptyList(),
                refreshing = boards.refreshing,
                selectedDisruption = disruptionModal?.disruption,
                selectedStationName = disruptionModal?.stationName,
            )
        } else {
            HomeUiState.Empty(EmptyReason.NoStationsConfigured)
        }
    }

    return HomeUiState.Content(
        walkUp = walkUp,
        stations = boards.stations,
        refreshing = boards.refreshing,
        selectedDisruption = disruptionModal?.disruption,
        selectedStationName = disruptionModal?.stationName,
    )
}
