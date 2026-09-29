package com.ukrailtracker.app.domain.location

import com.ukrailtracker.app.domain.commute.LOCATION_POLL_MS
import com.ukrailtracker.app.domain.model.UserLocation
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * Fixed-interval GPS poll. Cadence does not depend on the commute session.
 */
class GpsPoller(
    private val locationProvider: LocationProvider,
    private val intervalMs: Long = LOCATION_POLL_MS,
) {
    suspend fun loop(onFix: suspend (UserLocation?) -> Unit) {
        onFix(locationProvider.currentLocation(forceRefresh = true))
        while (currentCoroutineContext().isActive) {
            delay(intervalMs)
            if (!currentCoroutineContext().isActive) return
            onFix(locationProvider.currentLocation(forceRefresh = false))
        }
    }
}
