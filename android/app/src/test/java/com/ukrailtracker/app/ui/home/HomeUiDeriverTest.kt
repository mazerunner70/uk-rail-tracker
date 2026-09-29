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
    fun noStationsWithIdleSessionShowsReadyWithNotAtStation() {
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
        assertTrue(ui is HomeUiState.Ready)
        val ready = ui as HomeUiState.Ready
        assertEquals(PresenceChromeUi.NotAtStation, ready.chrome)
        assertTrue(ready.body is HomeBodyUi.StationList)
        assertTrue(ready.stationList.isEmpty())
    }

    @Test
    fun noStationsWithPermissionChromeShowsReady() {
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
        assertTrue(ui is HomeUiState.Ready)
        val ready = ui as HomeUiState.Ready
        assertEquals(PresenceChromeUi.NeedsPermission, ready.chrome)
        assertTrue(ready.body is HomeBodyUi.StationList)
        assertTrue(ready.stationList.isEmpty())
    }

    @Test
    fun boardsAndChromeCombined() {
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
        ) as HomeUiState.Ready
        assertEquals(PresenceChromeUi.NotAtStation, ui.chrome)
        assertEquals(1, ui.stationList.size)
        assertTrue(ui.body is HomeBodyUi.StationList)
    }
}
