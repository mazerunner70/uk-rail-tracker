package com.ukrailtracker.app.domain.location

import com.ukrailtracker.app.domain.model.UserLocation

interface LocationProvider {
    /**
     * @param forceRefresh when true, ignore last-known cache and request a fresh fix
     * (needed after emulator `geo fix` / user pull-to-refresh).
     */
    suspend fun currentLocation(forceRefresh: Boolean = false): UserLocation?
}
