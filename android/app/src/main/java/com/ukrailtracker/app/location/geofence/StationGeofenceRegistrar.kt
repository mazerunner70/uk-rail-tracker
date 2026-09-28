package com.ukrailtracker.app.location.geofence

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.ukrailtracker.app.domain.usecase.SelectGeofenceStations
import kotlinx.coroutines.tasks.await

/**
 * Registers a replaceable set of OS geofences for nearby / always-on stations.
 * ENTER is a wake hint only — station presence stays on the GPS poll.
 */
class StationGeofenceRegistrar(
    context: Context,
    private val radiusMetres: Float = DEFAULT_RADIUS_METRES,
) {
    private val appContext = context.applicationContext
    private val client = LocationServices.getGeofencingClient(appContext)

    suspend fun replaceGeofences(stations: List<SelectGeofenceStations.Candidate>): Boolean {
        if (!hasFineOrCoarseLocation()) {
            Log.i(TAG, "Skip geofence arm — location permission missing")
            return false
        }
        if (stations.isEmpty()) {
            removeAll()
            return true
        }

        // Clear previous set tied to our PendingIntent, then add the new window.
        removeAll()

        val geofences = stations.map { candidate ->
            Geofence.Builder()
                .setRequestId(StationGeofenceIds.forCrs(candidate.crs))
                .setCircularRegion(candidate.latitude, candidate.longitude, radiusMetres)
                .setExpirationDuration(Geofence.NEVER_EXPIRE)
                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER)
                .build()
        }
        val request = GeofencingRequest.Builder()
            .setInitialTrigger(0) // do not fire ENTER for stations we are already inside on re-arm
            .addGeofences(geofences)
            .build()

        return try {
            @SuppressLint("MissingPermission")
            client.addGeofences(request, GeofenceIntents.transitionPendingIntent(appContext)).await()
            Log.i(TAG, "Armed ${geofences.size} station geofences (r=${radiusMetres}m)")
            true
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to add geofences: ${t.message}")
            false
        }
    }

    suspend fun removeAll() {
        try {
            client.removeGeofences(GeofenceIntents.transitionPendingIntent(appContext)).await()
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to remove geofences: ${t.message}")
        }
    }

    private fun hasFineOrCoarseLocation(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            appContext, Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            appContext, Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    companion object {
        /** Slightly larger than soft walk-up (200m) so ENTER wakes before dwell lock. */
        const val DEFAULT_RADIUS_METRES = 300f
        private const val TAG = "UkRailTracker"
    }
}
