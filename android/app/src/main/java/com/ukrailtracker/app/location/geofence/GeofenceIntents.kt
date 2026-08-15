package com.ukrailtracker.app.location.geofence

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.ukrailtracker.app.MainActivity

object GeofenceIntents {
    const val ACTION_GEOFENCE_TRANSITION = "com.ukrailtracker.app.action.GEOFENCE_TRANSITION"
    const val ACTION_OPEN_HOME_FREQUENT_CHECKS =
        "com.ukrailtracker.app.action.OPEN_HOME_FREQUENT_CHECKS"

    const val EXTRA_STATION_CRS = "station_crs"
    const val EXTRA_STATION_NAME = "station_name"
    const val EXTRA_FREQUENT_CHECKS = "frequent_location_checks"

    fun transitionPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, GeofenceTransitionsReceiver::class.java).apply {
            action = ACTION_GEOFENCE_TRANSITION
        }
        // GeofencingClient requires a mutable PendingIntent on API 31+.
        return PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
    }

    fun openHomeFrequentChecksIntent(
        context: Context,
        crs: String,
        stationName: String,
    ): Intent =
        Intent(context, MainActivity::class.java).apply {
            action = ACTION_OPEN_HOME_FREQUENT_CHECKS
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_FREQUENT_CHECKS, true)
            putExtra(EXTRA_STATION_CRS, crs)
            putExtra(EXTRA_STATION_NAME, stationName)
        }

    fun openHomeFrequentChecksPendingIntent(
        context: Context,
        crs: String,
        stationName: String,
    ): PendingIntent {
        val requestCode = crs.hashCode()
        return PendingIntent.getActivity(
            context,
            requestCode,
            openHomeFrequentChecksIntent(context, crs, stationName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
