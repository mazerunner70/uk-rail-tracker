package com.ukrailtracker.app.location.geofence

internal object StationGeofenceIds {
    private const val PREFIX = "stn_"

    fun forCrs(crs: String): String = PREFIX + crs.trim().uppercase()

    fun crsFromRequestId(requestId: String): String? {
        val raw = requestId.trim()
        if (!raw.startsWith(PREFIX, ignoreCase = true)) return null
        return raw.substring(PREFIX.length).trim().uppercase().takeIf { it.isNotEmpty() }
    }
}
