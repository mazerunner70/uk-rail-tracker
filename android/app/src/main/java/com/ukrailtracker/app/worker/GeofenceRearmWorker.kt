package com.ukrailtracker.app.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ukrailtracker.app.appContainer

/**
 * Periodically refreshes the OS geofence window to the nearest stations (+ favourites/commute).
 */
class GeofenceRearmWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val ok = applicationContext.appContainer()
                .stationGeofenceController
                .armFromCurrentLocation(forceLocationRefresh = true)
            if (ok) Result.success() else Result.success() // missing location is not a hard failure
        } catch (t: Throwable) {
            Log.w(TAG, "Geofence re-arm failed: ${t.message}")
            Result.retry()
        }
    }

    companion object {
        const val UNIQUE_NAME = "station_geofence_rearm"
        private const val TAG = "UkRailTracker"
    }
}
