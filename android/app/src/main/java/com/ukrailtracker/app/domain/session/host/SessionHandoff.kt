package com.ukrailtracker.app.domain.session.host

import com.ukrailtracker.app.domain.model.JourneyOption
import com.ukrailtracker.app.domain.usecase.BoardingServiceSnapshot
import com.ukrailtracker.app.domain.usecase.RouteStation
import com.ukrailtracker.app.ui.home.CommuteAuxState

/**
 * Immutable bag passed [SessionHost.onExit] → [SessionHost.onEntry].
 * Grows only when a host needs to hand something to the next type.
 */
data class SessionHandoff(
    val aux: CommuteAuxState = CommuteAuxState(),
    val cachedBoardingServices: List<BoardingServiceSnapshot> = emptyList(),
    val latestOnTrainOption: JourneyOption? = null,
    val routeStations: List<RouteStation> = emptyList(),
    val permissionGranted: Boolean = false,
    val lastLoadedOriginCrs: String? = null,
    val platformNotified: Boolean = false,
    val onTrainServiceStarted: Boolean = false,
)
