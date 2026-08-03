package com.ukrailtracker.app.domain.usecase

import com.ukrailtracker.app.domain.location.LocationProvider
import com.ukrailtracker.app.domain.model.StationWithDistance
import com.ukrailtracker.app.domain.model.UserLocation
import com.ukrailtracker.app.domain.repository.StationRepository

sealed class NearbyResult {
    data class Success(
        val location: UserLocation,
        val stations: List<StationWithDistance>,
    ) : NearbyResult()

    data object LocationUnavailable : NearbyResult()
}

class GetNearbyStationsUseCase(
    private val locationProvider: LocationProvider,
    private val stationRepository: StationRepository,
    private val limit: Int = 10,
) {
    suspend operator fun invoke(forceLocationRefresh: Boolean = false): NearbyResult {
        stationRepository.ensureImported()
        val location = locationProvider.currentLocation(forceRefresh = forceLocationRefresh)
            ?: return NearbyResult.LocationUnavailable
        val stations = stationRepository.getNearby(
            lat = location.latitude,
            lng = location.longitude,
            limit = limit,
        )
        return NearbyResult.Success(location, stations)
    }
}
