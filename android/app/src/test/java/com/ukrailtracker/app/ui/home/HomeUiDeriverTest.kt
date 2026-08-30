package com.ukrailtracker.app.ui.home

import com.ukrailtracker.app.domain.usecase.CommuteSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeUiDeriverTest {

    @Test
    fun awaitingFirstLoadShowsLoading() {
        val ui = deriveHomeUiState(
            session = CommuteSession.Idle,
            aux = CommuteAuxState(),
            lastLoadedOriginCrs = null,
            boards = StationBoardsState(awaitingFirstLoad = true),
            disruptionModal = null,
        )
        assertEquals(HomeUiState.Loading, ui)
    }

    @Test
    fun noStationsWithIdleSessionShowsContentWithNotAtStation() {
        val ui = deriveHomeUiState(
            session = CommuteSession.Idle,
            aux = CommuteAuxState(),
            lastLoadedOriginCrs = null,
            boards = StationBoardsState(
                awaitingFirstLoad = false,
                noStationsConfigured = true,
            ),
            disruptionModal = null,
        )
        assertTrue(ui is HomeUiState.Content)
        assertEquals(WalkUpHomeUi.NotAtStation, (ui as HomeUiState.Content).walkUp)
        assertTrue(ui.stations.isEmpty())
    }

    @Test
    fun noStationsWithWalkUpShowsContent() {
        val ui = deriveHomeUiState(
            session = CommuteSession.Idle,
            aux = CommuteAuxState(overlay = PresenceOverlay.NeedsPermission),
            lastLoadedOriginCrs = null,
            boards = StationBoardsState(
                awaitingFirstLoad = false,
                noStationsConfigured = true,
            ),
            disruptionModal = null,
        )
        assertTrue(ui is HomeUiState.Content)
        assertEquals(WalkUpHomeUi.NeedsPermission, (ui as HomeUiState.Content).walkUp)
        assertTrue(ui.stations.isEmpty())
    }

    @Test
    fun boardsAndWalkUpCombined() {
        val card = HomeStationCardUi(
            crsCode = "PAD",
            stationName = "Paddington",
            reasonLabel = "Favourite",
            disruption = com.ukrailtracker.app.domain.model.DisruptionSummary(
                severity = com.ukrailtracker.app.domain.model.DisruptionSeverity.Green,
                headline = "Good service",
                detail = "",
                affectedOperators = emptyList(),
                cancelledCount = 0,
                delayedCount = 0,
                maxDelayMinutes = 0,
            ),
            nextTrains = emptyList(),
        )
        val ui = deriveHomeUiState(
            session = CommuteSession.Idle,
            aux = CommuteAuxState(),
            lastLoadedOriginCrs = null,
            boards = StationBoardsState(
                stations = listOf(card),
                awaitingFirstLoad = false,
            ),
            disruptionModal = null,
        ) as HomeUiState.Content
        assertEquals(WalkUpHomeUi.NotAtStation, ui.walkUp)
        assertEquals(1, ui.stations.size)
    }
}
