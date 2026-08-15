package com.ukrailtracker.app.location

import com.ukrailtracker.app.domain.location.LocationProvider
import com.ukrailtracker.app.domain.model.UserLocation

/** Debug-only location override while [WalkUpLocationSimulator] is active. */
class SimulatingLocationProvider(
    private val real: LocationProvider,
    private val simulator: WalkUpLocationSimulator,
) : LocationProvider {
    override suspend fun currentLocation(forceRefresh: Boolean): UserLocation? {
        simulator.currentLocation()?.let { return it }
        return real.currentLocation(forceRefresh)
    }
}
