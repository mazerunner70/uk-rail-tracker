package com.ukrailtracker.app.domain.commute

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.ukrailtracker.app.domain.location.GpsPoller
import com.ukrailtracker.app.domain.location.LocationProvider
import com.ukrailtracker.app.domain.location.StationPresenceService
import com.ukrailtracker.app.domain.repository.JourneyRepository
import com.ukrailtracker.app.domain.repository.StationRepository
import com.ukrailtracker.app.domain.usecase.LoadFavouriteBoundDepartures
import com.ukrailtracker.app.service.OnTrainTrackingService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Owns the commute runtime and the always-on GPS poll.
 *
 * Background location keeps the poll running from the location foreground service,
 * including after Home is stopped. Foreground-only permission polls while Home is started.
 */
class CommuteSessionController(
    private val appContext: Context,
    private val scope: CoroutineScope,
    stationRepository: StationRepository,
    journeyRepository: JourneyRepository,
    private val locationProvider: LocationProvider,
    favouritesStore: CommuteFavouritesAccess,
    pinnedJourneyStore: CommutePinStore,
    journeyLogRepository: CommuteJourneyLogger,
    loadFavouriteBoundDepartures: LoadFavouriteBoundDepartures =
        LoadFavouriteBoundDepartures(journeyRepository, stationRepository),
    onLatestSpeedMps: ((Double?) -> Unit)? = null,
) {
    val runtime: CommuteRuntime = CommuteRuntime(
        scope = scope,
        appContext = appContext,
        stationRepository = stationRepository,
        journeyRepository = journeyRepository,
        favouritesStore = favouritesStore,
        pinnedJourneyStore = pinnedJourneyStore,
        journeyLogRepository = journeyLogRepository,
        loadFavouriteBoundDepartures = loadFavouriteBoundDepartures,
        onLatestSpeedMps = onLatestSpeedMps,
    )

    private val presence = StationPresenceService(stationRepository)
    private val poller = GpsPoller(locationProvider)
    private var pollJob: Job? = null
    private var homeHold = false
    private var backgroundHold = false

    fun onHomeStarted() {
        homeHold = true
        startPollingIfPermitted()
    }

    fun onHomeStopped() {
        homeHold = false
        if (!backgroundHold) stopPolling()
    }

    fun onPermissionGranted() {
        runtime.onPermissionGranted()
        startPollingIfPermitted()
    }

    fun onPermissionDenied() {
        homeHold = false
        backgroundHold = false
        stopPolling()
        runtime.onPermissionDenied()
        runCatching { OnTrainTrackingService.stop(appContext) }
    }

    fun onNeedsPermission() {
        homeHold = false
        backgroundHold = false
        stopPolling()
        runtime.onNeedsPermission()
    }

    /** Starts the location foreground service. The service calls [onBackgroundHoldAcquired]. */
    fun onBackgroundLocationGranted() {
        OnTrainTrackingService.startBackground(appContext)
    }

    fun onBackgroundHoldAcquired() {
        backgroundHold = true
        if (!runtime.handoff.permissionGranted && hasOsLocationPermission()) {
            runtime.onPermissionGranted()
        }
        startPollingIfPermitted()
    }

    fun onBackgroundHoldReleased() {
        backgroundHold = false
        if (!homeHold) stopPolling()
    }

    fun requestFreshFix() {
        scope.launch { sampleOnce(forceRefresh = true) }
    }

    fun prepareForDebugWalkUp(elevatePollForMs: Long) {
        runtime.prepareForDebugWalkUp(elevatePollForMs)
        homeHold = true
        startPollingIfPermitted()
        requestFreshFix()
    }

    fun clearOriginOverride() {
        runtime.clearOriginOverride()
        requestFreshFix()
    }

    private fun startPollingIfPermitted() {
        if (!runtime.handoff.permissionGranted) return
        if (!homeHold && !backgroundHold) return
        if (pollJob?.isActive == true) return
        pollJob = scope.launch {
            poller.loop { location ->
                if (location == null) {
                    runtime.onLocationMissing()
                } else {
                    runtime.applyPresenceSample(presence.onLocation(location))
                }
            }
        }
    }

    private fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
        scope.launch { presence.reset() }
    }

    private suspend fun sampleOnce(forceRefresh: Boolean) {
        if (!runtime.handoff.permissionGranted) return
        val location = locationProvider.currentLocation(forceRefresh = forceRefresh)
        if (location == null) {
            runtime.onLocationMissing()
            return
        }
        runtime.applyPresenceSample(presence.onLocation(location))
    }

    private fun hasOsLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            appContext, Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            appContext, Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }
}
