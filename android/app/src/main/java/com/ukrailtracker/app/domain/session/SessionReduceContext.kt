package com.ukrailtracker.app.domain.session

import com.ukrailtracker.app.domain.model.CallingPoint
import com.ukrailtracker.app.domain.usecase.BoardingConfig
import com.ukrailtracker.app.domain.usecase.BoardingServiceSnapshot
import com.ukrailtracker.app.domain.usecase.RouteStation
import com.ukrailtracker.app.domain.usecase.StationStopConfig
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Contextual data and configuration supplied during session reduction.
 */
data class SessionReduceContext(
    val boardingConfig: BoardingConfig = BoardingConfig(),
    val stationStopConfig: StationStopConfig = StationStopConfig(),
    val cachedBoardingServices: List<BoardingServiceSnapshot> = emptyList(),
    val routeStations: List<RouteStation> = emptyList(),
    val onTrainCallingPoints: List<CallingPoint> = emptyList(),
    val zoneId: ZoneId = ZoneId.systemDefault(),
    val progressNow: LocalDateTime = LocalDateTime.now(),
)
