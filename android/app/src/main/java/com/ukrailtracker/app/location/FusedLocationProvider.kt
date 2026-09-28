package com.ukrailtracker.app.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.ukrailtracker.app.domain.location.LocationProvider
import com.ukrailtracker.app.domain.model.UserLocation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

class FusedLocationProvider(
    private val context: Context,
) : LocationProvider {

    private val client = LocationServices.getFusedLocationProviderClient(context)

    override suspend fun currentLocation(forceRefresh: Boolean): UserLocation? {
        if (!hasLocationPermission()) return null

        if (forceRefresh) {
            return requestCurrentLocation() ?: requestFreshLocationUpdates() ?: readLastKnown()
        }

        val lastKnown = readLastKnown()
        if (lastKnown != null && (lastKnown.accuracyMetres == null || lastKnown.accuracyMetres <= 2_000f)) {
            return lastKnown
        }
        return requestCurrentLocation() ?: requestFreshLocationUpdates() ?: lastKnown
    }

    private fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    @SuppressLint("MissingPermission")
    private suspend fun readLastKnown(): UserLocation? {
        return try {
            val location = client.lastLocation.await() ?: return null
            location.toUserLocation()
        } catch (_: SecurityException) {
            null
        } catch (_: Exception) {
            null
        }
    }

    /** One-shot fresh fix — preferred path for refresh / emulator geo fix. */
    @SuppressLint("MissingPermission")
    private suspend fun requestCurrentLocation(): UserLocation? {
        val cancellation = CancellationTokenSource()
        return try {
            val request = CurrentLocationRequest.Builder()
                .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                .setDurationMillis(10_000L)
                .setMaxUpdateAgeMillis(0L) // do not accept a cached fix
                .build()
            val location = client.getCurrentLocation(request, cancellation.token).await()
            location?.toUserLocation()
        } catch (_: SecurityException) {
            null
        } catch (_: Exception) {
            null
        } finally {
            cancellation.cancel()
        }
    }

    /**
     * Fallback one-shot update request.
     *
     * Must never hang: Play Services may expire [LocationRequest] duration without calling
     * [LocationCallback.onLocationResult], which previously stalled the on-train GPS poll loop
     * (frozen speed + progress after the first few fixes).
     */
    @SuppressLint("MissingPermission")
    private suspend fun requestFreshLocationUpdates(): UserLocation? =
        withTimeoutOrNull(LOCATION_UPDATE_TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 500L)
                    .setMinUpdateIntervalMillis(0L)
                    .setMaxUpdates(1)
                    .setDurationMillis(LOCATION_UPDATE_DURATION_MS)
                    .setMaxUpdateAgeMillis(0L)
                    .build()

                val callback = object : LocationCallback() {
                    override fun onLocationResult(result: LocationResult) {
                        client.removeLocationUpdates(this)
                        if (cont.isActive) {
                            cont.resume(result.lastLocation?.toUserLocation())
                        }
                    }
                }

                try {
                    client.requestLocationUpdates(request, callback, Looper.getMainLooper())
                } catch (_: SecurityException) {
                    if (cont.isActive) cont.resume(null)
                    return@suspendCancellableCoroutine
                }

                cont.invokeOnCancellation {
                    client.removeLocationUpdates(callback)
                }
            }
        }

    companion object {
        private const val LOCATION_UPDATE_DURATION_MS = 10_000L
        /** Slightly above request duration so expiry can complete before we give up. */
        private const val LOCATION_UPDATE_TIMEOUT_MS = 11_000L
    }

    private fun android.location.Location.toUserLocation(): UserLocation =
        UserLocation(
            latitude = latitude,
            longitude = longitude,
            accuracyMetres = if (hasAccuracy()) accuracy else null,
            speedMetresPerSecond = if (hasSpeed()) speed else null,
            epochMs = if (time > 0L) time else System.currentTimeMillis(),
        )
}
