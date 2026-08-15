package com.ukrailtracker.app.location.geofence

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.ukrailtracker.app.data.local.datastore.CommuteWindowStore
import com.ukrailtracker.app.data.local.datastore.FavouritesStore
import com.ukrailtracker.app.domain.location.LocationProvider
import com.ukrailtracker.app.domain.model.Station
import com.ukrailtracker.app.domain.repository.StationRepository
import com.ukrailtracker.app.domain.usecase.SelectGeofenceStations

/**
 * Re-arms the moving window of OS station geofences from current location + favourites/commute.
 */
class StationGeofenceController(
    private val appContext: Context,
    private val locationProvider: LocationProvider,
    private val stationRepository: StationRepository,
    private val favouritesStore: FavouritesStore,
    private val commuteWindowStore: CommuteWindowStore,
    private val registrar: StationGeofenceRegistrar = StationGeofenceRegistrar(appContext),
) {
    suspend fun armFromCurrentLocation(forceLocationRefresh: Boolean = false): Boolean {
        if (!hasForegroundLocation()) {
            Log.i(TAG, "Geofence arm skipped — no foreground location permission")
            return false
        }
        return try {
            stationRepository.ensureImported()
            val location = locationProvider.currentLocation(forceRefresh = forceLocationRefresh)
            val nearby = if (location != null) {
                stationRepository.getNearby(
                    lat = location.latitude,
                    lng = location.longitude,
                    limit = NEARBY_LIMIT,
                )
            } else {
                emptyList()
            }
            val alwaysOn = resolveAlwaysOnStations()
            val candidates = SelectGeofenceStations.select(
                nearby = nearby,
                alwaysOn = alwaysOn,
                nearbyLimit = NEARBY_LIMIT,
                maxTotal = MAX_GEOFENCES,
            )
            if (candidates.isEmpty()) {
                Log.i(TAG, "No geofence candidates (no location and no favourites/commute)")
                registrar.removeAll()
                return false
            }
            registrar.replaceGeofences(candidates)
        } catch (t: Throwable) {
            Log.w(TAG, "Geofence arm failed: ${t.message}")
            false
        }
    }

    private suspend fun resolveAlwaysOnStations(): List<Station> {
        val crsSet = LinkedHashSet<String>()
        favouritesStore.getFavourites().forEach { crsSet += it.trim().uppercase() }
        commuteWindowStore.getWindows().forEach { crsSet += it.stationCrs.trim().uppercase() }
        val out = ArrayList<Station>(crsSet.size)
        for (crs in crsSet) {
            if (crs.isEmpty()) continue
            stationRepository.getByCrs(crs)?.let { out += it }
        }
        return out
    }

    private fun hasForegroundLocation(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            appContext, Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            appContext, Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    companion object {
        private const val TAG = "UkRailTracker"
        const val NEARBY_LIMIT = 5
        const val MAX_GEOFENCES = 20
    }
}
