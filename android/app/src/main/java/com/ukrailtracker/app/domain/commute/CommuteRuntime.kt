package com.ukrailtracker.app.domain.commute

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.ukrailtracker.app.debug.DebugWalkUp
import com.ukrailtracker.app.domain.location.PresenceSample
import com.ukrailtracker.app.domain.model.JourneyOption
import com.ukrailtracker.app.domain.model.PinnedJourney
import com.ukrailtracker.app.domain.repository.JourneyRepository
import com.ukrailtracker.app.domain.repository.StationRepository
import com.ukrailtracker.app.domain.session.SessionEngine
import com.ukrailtracker.app.domain.session.SessionReduceContext
import com.ukrailtracker.app.domain.session.host.AtStationHost
import com.ukrailtracker.app.domain.session.host.ConfirmingHost
import com.ukrailtracker.app.domain.session.host.IdleHost
import com.ukrailtracker.app.domain.session.host.NearHost
import com.ukrailtracker.app.domain.session.host.OnTrainHost
import com.ukrailtracker.app.domain.session.host.SessionHandoff
import com.ukrailtracker.app.domain.session.host.SessionHost
import com.ukrailtracker.app.domain.session.host.SessionHostRegistry
import com.ukrailtracker.app.domain.session.host.SessionHostServices
import com.ukrailtracker.app.domain.session.host.SessionTypeId
import com.ukrailtracker.app.domain.session.host.typeId
import com.ukrailtracker.app.domain.usecase.BoardingConfig
import com.ukrailtracker.app.domain.usecase.CommuteEffect
import com.ukrailtracker.app.domain.usecase.CommuteEvent
import com.ukrailtracker.app.domain.usecase.CommuteSession
import com.ukrailtracker.app.domain.usecase.CommuteTransition
import com.ukrailtracker.app.domain.usecase.LoadFavouriteBoundDepartures
import com.ukrailtracker.app.domain.usecase.LockedOrigin
import com.ukrailtracker.app.domain.usecase.PlatformWatchPhase
import com.ukrailtracker.app.domain.usecase.StationStopConfig
import com.ukrailtracker.app.domain.usecase.onTrainOrNull
import com.ukrailtracker.app.service.OnTrainTrackingService
import com.ukrailtracker.app.ui.home.AtStationAux
import com.ukrailtracker.app.ui.home.CommuteAuxState
import com.ukrailtracker.app.ui.home.HomeBodyUi
import com.ukrailtracker.app.ui.home.OnTrainUiAux
import com.ukrailtracker.app.ui.home.PlatformWatchUi
import com.ukrailtracker.app.ui.home.PresenceOverlay
import com.ukrailtracker.app.ui.home.mapCommutePresence
import com.ukrailtracker.app.ui.home.mapPlatformWatchUi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Coordinates commute session dispatch, host enter/exit, and effect interpretation.
 *
 * GPS polling lives in [com.ukrailtracker.app.domain.commute.CommuteSessionController].
 * This class reduces the events that poll produces.
 */
class CommuteRuntime(
    scope: CoroutineScope,
    override val appContext: Context,
    override val stationRepository: StationRepository,
    override val journeyRepository: JourneyRepository,
    override val favouritesStore: CommuteFavouritesAccess,
    override val pinnedJourneyStore: CommutePinStore,
    override val journeyLogRepository: CommuteJourneyLogger,
    override val loadFavouriteBoundDepartures: LoadFavouriteBoundDepartures =
        LoadFavouriteBoundDepartures(journeyRepository, stationRepository),
    override val boardingConfig: BoardingConfig = BoardingConfig(),
    private val stationStopConfig: StationStopConfig = StationStopConfig(),
    private val onLatestSpeedMps: ((Double?) -> Unit)? = null,
) : SessionHostServices {
    override val scope: CoroutineScope = scope

    private val _handoff = MutableStateFlow(SessionHandoff())
    override val handoff: SessionHandoff get() = _handoff.value

    private val hosts = SessionHostRegistry(
        idle = IdleHost(),
        near = NearHost(),
        atStation = AtStationHost(services = this),
        confirming = ConfirmingHost(services = this),
        onTrain = OnTrainHost(services = this),
    )

    private var activeHost: SessionHost<*> = hosts.idle

    private val sessionEngine = SessionEngine(
        scope = scope,
        initialSession = CommuteSession.Idle,
        contextProvider = ::buildReduceContext,
        onTransitionApplied = ::applyTransition,
        reduce = hosts::reduce,
    )
    override val sessionFlow: StateFlow<CommuteSession> = sessionEngine.sessionFlow

    private val _auxFlow = MutableStateFlow(CommuteAuxState())
    val auxFlow: StateFlow<CommuteAuxState> = _auxFlow.asStateFlow()

    private val _lastLoadedOriginCrs = MutableStateFlow<String?>(null)
    val lastLoadedOriginCrs: StateFlow<String?> = _lastLoadedOriginCrs.asStateFlow()

    private val _arrivalBuzz = MutableSharedFlow<Int>(extraBufferCapacity = 3)
    override val arrivalBuzz: SharedFlow<Int> = _arrivalBuzz.asSharedFlow()

    private val _platformAppeared = MutableSharedFlow<String>(extraBufferCapacity = 1)
    override val platformAppeared: SharedFlow<String> = _platformAppeared.asSharedFlow()

    private var pinRestoreJob: Job? = null

    init {
        activeHost = hosts.idle
        publishHandoff(hosts.idle.onEntry(SessionTypeId.Idle, SessionHandoff(), CommuteSession.Idle))

        pinRestoreJob = scope.launch {
            pinnedJourneyStore.pinnedFlow.collect { pinned ->
                val current = sessionFlow.value.onTrainOrNull()
                if (pinned == null) {
                    if (current != null) {
                        dispatch(CommuteEvent.PinCleared)
                    }
                } else if (current?.pin?.serviceId != pinned.serviceId) {
                    dispatch(CommuteEvent.RestorePin(pinned))
                }
            }
        }
    }

    override fun dispatch(event: CommuteEvent) {
        sessionEngine.dispatch(event)
    }

    fun onPermissionGranted() {
        publishHandoff(handoff.copy(permissionGranted = true))
        _auxFlow.value = _auxFlow.value.copy(overlay = PresenceOverlay.None)
    }

    fun onPermissionDenied() {
        publishHandoff(handoff.copy(permissionGranted = false))
        _auxFlow.value = _auxFlow.value.copy(overlay = PresenceOverlay.NeedsPermission)
        dispatch(CommuteEvent.PermissionLost)
    }

    fun onNeedsPermission() {
        publishHandoff(handoff.copy(permissionGranted = false))
        _auxFlow.value = _auxFlow.value.copy(overlay = PresenceOverlay.NeedsPermission)
        dispatch(CommuteEvent.PermissionLost)
    }

    @Suppress("UNUSED_PARAMETER")
    fun prepareForDebugWalkUp(elevatePollForMs: Long) {
        if (sessionFlow.value.isOnTrain()) {
            dispatch(CommuteEvent.DiscardTrip)
        }
        hosts.atStation.clearBoardingCache()
        hosts.confirming.onExit(SessionTypeId.Idle)
        _lastLoadedOriginCrs.value = null
        sessionEngine.setSessionDirect(CommuteSession.Idle)
        val reset = SessionHandoff(permissionGranted = true)
        activeHost = hosts.idle
        publishHandoff(hosts.idle.onEntry(SessionTypeId.Idle, reset, CommuteSession.Idle))
        _auxFlow.value = CommuteAuxState()
    }

    fun setOriginOverride(crs: String) {
        scope.launch {
            val normalised = crs.trim().uppercase()
            if (normalised.isBlank()) return@launch
            hosts.atStation.clearBoardingCache()
            val station = stationRepository.getByCrs(normalised)
            val name = station?.name ?: normalised
            _lastLoadedOriginCrs.value = null
            publishHandoff(handoff.copy(lastLoadedOriginCrs = null, platformNotified = false))
            dispatch(
                CommuteEvent.SetOriginOverride(
                    LockedOrigin(
                        crs = normalised,
                        name = name,
                        latitude = station?.latitude ?: 0.0,
                        longitude = station?.longitude ?: 0.0,
                        lockedAtEpochMs = System.currentTimeMillis(),
                        distanceMetres = 0.0,
                        manualOverride = true,
                    ),
                ),
            )
        }
    }

    fun clearOriginOverride() {
        _lastLoadedOriginCrs.value = null
        publishHandoff(
            handoff.copy(
                lastLoadedOriginCrs = null,
                cachedBoardingServices = emptyList(),
            ),
        )
        hosts.atStation.clearBoardingCache()
        dispatch(CommuteEvent.ClearOverride)
        if (handoff.permissionGranted) {
            _auxFlow.value = _auxFlow.value.copy(overlay = PresenceOverlay.None)
        } else {
            _auxFlow.value = _auxFlow.value.copy(overlay = PresenceOverlay.NeedsPermission)
        }
    }

    fun dismissBoarding() {
        publishHandoff(
            handoff.copy(
                cachedBoardingServices = emptyList(),
            ),
        )
        hosts.atStation.clearBoardingCache()
        dispatch(CommuteEvent.Dismiss)
    }

    fun selectServiceForPlatformWatch(
        originCrs: String,
        destinationCrs: String,
        serviceId: String,
    ) {
        val at = currentAtStationBody() ?: return
        val dest = at.destinations.find { it.destinationCrs.equals(destinationCrs, ignoreCase = true) }
        val service = dest?.services?.find { it.serviceId == serviceId } ?: return
        dispatch(
            CommuteEvent.WatchPlatform(
                serviceId = serviceId,
                originCrs = originCrs,
                destinationCrs = destinationCrs,
                destinationName = dest.destinationName,
                scheduledDepartureLabel = service.scheduledDepartureLabel,
                expectedDepartureLabel = service.expectedDepartureLabel,
                platform = service.platform?.takeIf { it.isNotBlank() },
            ),
        )
    }

    fun clearPlatformWatch() {
        dispatch(CommuteEvent.ClearPlatformWatch)
    }

    fun refreshPlatformWatch() {
        hosts.atStation.refreshPlatformWatch()
    }

    fun reloadDestinationsIfAtStation(forceRefresh: Boolean) {
        val originCrs = sessionFlow.value.destinationLoadOriginCrs() ?: return
        hosts.atStation.reloadDestinations(originCrs = originCrs, forceRefresh = forceRefresh)
    }

    fun refreshOnTrainOption() {
        hosts.onTrain.refreshOnTrainOption()
    }

    fun onLocationMissing() {
        if (sessionFlow.value.showsLocationUnavailableOverlay()) {
            _auxFlow.value = _auxFlow.value.copy(overlay = PresenceOverlay.LocationUnavailable)
        }
    }

    fun applyPresenceSample(sample: PresenceSample) {
        if (_auxFlow.value.overlay == PresenceOverlay.LocationUnavailable) {
            _auxFlow.value = _auxFlow.value.copy(overlay = PresenceOverlay.None)
        }
        onLatestSpeedMps?.invoke(sample.speedMps)
        sessionEngine.dispatchSequential(sample.events)
        val stillOnTrain = sessionFlow.value.onTrainOrNull()
        val option = hosts.onTrain.latestOption()
        if (stillOnTrain != null && option != null) {
            dispatch(CommuteEvent.ServiceDetails(option))
        }
    }

    fun onCleared() {
        pinRestoreJob?.cancel()
        activeHost.onExit(SessionTypeId.Idle)
    }

    // --- SessionHostServices ---

    override fun publishHandoff(handoff: SessionHandoff) {
        _handoff.value = handoff
        _auxFlow.value = handoff.aux
        _lastLoadedOriginCrs.value = handoff.lastLoadedOriginCrs
    }

    override fun emitPlatformAppeared(platform: String) {
        _platformAppeared.tryEmit(platform)
    }

    override fun emitArrivalBuzz(threshold: Int) {
        _arrivalBuzz.tryEmit(threshold)
    }

    override fun updateAtStationAux(transform: (AtStationAux) -> AtStationAux) {
        publishHandoff(
            handoff.copy(aux = handoff.aux.copy(atStation = transform(handoff.aux.atStation))),
        )
    }

    override fun updateOnTrainAux(transform: (OnTrainUiAux) -> OnTrainUiAux) {
        publishHandoff(
            handoff.copy(aux = handoff.aux.copy(onTrain = transform(handoff.aux.onTrain))),
        )
    }

    override fun updateAux(transform: (CommuteAuxState) -> CommuteAuxState) {
        publishHandoff(handoff.copy(aux = transform(handoff.aux)))
    }

    override fun currentAtStationBody(): HomeBodyUi.AtStation? =
        mapCommutePresence(
            sessionFlow.value,
            _auxFlow.value,
            _lastLoadedOriginCrs.value,
        ).tripBody as? HomeBodyUi.AtStation

    override fun mapPlatformWatch(watch: PlatformWatchPhase.Watching): PlatformWatchUi? =
        mapPlatformWatchUi(watch, auxWatch = null)

    override fun startOnTrainForeground(destinationName: String, minutesRemaining: Long?) {
        startOnTrainService(destinationName, minutesRemaining)
    }

    override fun updateOnTrainForeground(destinationName: String, minutesRemaining: Long?) {
        updateOnTrainService(destinationName, minutesRemaining)
    }

    override fun stopOnTrainForeground() {
        stopOnTrainService()
    }

    override fun pinJourney(journey: PinnedJourney) {
        scope.launch { pinnedJourneyStore.pin(journey) }
    }

    override fun clearPin() {
        scope.launch { pinnedJourneyStore.clear() }
    }

    override fun logJourney(option: JourneyOption) {
        scope.launch {
            runCatching { journeyLogRepository.logFromOption(option) }
        }
    }

    // --- Transition coordination ---

    private fun applyTransition(previous: CommuteSession, transition: CommuteTransition) {
        val next = transition.session
        val prevId = previous.typeId()
        val nextId = next.typeId()
        var nextHandoff = handoff

        if (prevId != nextId) {
            nextHandoff = activeHost.onExit(nextId)
            publishHandoff(nextHandoff)
            activeHost = hosts.hostFor(nextId)
            nextHandoff = enterHost(activeHost, prevId, nextHandoff, next)
            publishHandoff(nextHandoff)
        } else if (previous != next) {
            nextHandoff = activeChange(activeHost, previous, next, nextHandoff)
            publishHandoff(nextHandoff)
        }

        for (effect in transition.effects) {
            interpretEffect(effect)
        }

        // Keep on-train aux in sync after reduce when option is already loaded.
        val onTrain = sessionFlow.value.onTrainOrNull()
        val option = hosts.onTrain.latestOption()
        if (onTrain != null && option != null && nextId == SessionTypeId.OnTrain) {
            publishHandoff(
                handoff.copy(
                    aux = handoff.aux.copy(
                        onTrain = com.ukrailtracker.app.ui.home.buildOnTrainUiAux(
                            option,
                            onTrain,
                            hosts.onTrain.routeStations(),
                        ),
                    ),
                ),
            )
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun enterHost(
        host: SessionHost<*>,
        previousId: SessionTypeId,
        handoff: SessionHandoff,
        session: CommuteSession,
    ): SessionHandoff = when (host) {
        is IdleHost -> host.onEntry(previousId, handoff, session as CommuteSession.Idle)
        is NearHost -> host.onEntry(previousId, handoff, session as CommuteSession.Near)
        is AtStationHost -> host.onEntry(previousId, handoff, session as CommuteSession.AtStation)
        is ConfirmingHost -> host.onEntry(previousId, handoff, session as CommuteSession.Confirming)
        is OnTrainHost -> host.onEntry(previousId, handoff, session as CommuteSession.OnTrain)
        else -> handoff
    }

    @Suppress("UNCHECKED_CAST")
    private fun activeChange(
        host: SessionHost<*>,
        previous: CommuteSession,
        next: CommuteSession,
        handoff: SessionHandoff,
    ): SessionHandoff = when (host) {
        is IdleHost -> host.onActiveChange(
            previous as CommuteSession.Idle,
            next as CommuteSession.Idle,
            handoff,
        )
        is NearHost -> host.onActiveChange(
            previous as CommuteSession.Near,
            next as CommuteSession.Near,
            handoff,
        )
        is AtStationHost -> host.onActiveChange(
            previous as CommuteSession.AtStation,
            next as CommuteSession.AtStation,
            handoff,
        )
        is ConfirmingHost -> host.onActiveChange(
            previous as CommuteSession.Confirming,
            next as CommuteSession.Confirming,
            handoff,
        )
        is OnTrainHost -> host.onActiveChange(
            previous as CommuteSession.OnTrain,
            next as CommuteSession.OnTrain,
            handoff,
        )
        else -> handoff
    }

    private fun interpretEffect(effect: CommuteEffect) {
        when (effect) {
            is CommuteEffect.LoadFavouriteBoundDepartures -> {
                // Owned by AtStationHost entry / active change; ignore if still emitted.
            }
            is CommuteEffect.Pin -> pinJourney(effect.journey)
            is CommuteEffect.StartTracking -> {
                // Owned by OnTrainHost.onEntry; ignore if still emitted.
            }
            is CommuteEffect.UpdateTracking ->
                updateOnTrainService(effect.destinationName, effect.minutesRemaining)
            CommuteEffect.StopTracking -> stopOnTrainService()
            CommuteEffect.WriteJourneyLog -> {
                val option = hosts.onTrain.latestOption()
                if (option != null) logJourney(option)
            }
            CommuteEffect.ClearPin -> clearPin()
            is CommuteEffect.Vibrate ->
                effect.thresholds.forEach { threshold -> emitArrivalBuzz(threshold) }
            CommuteEffect.RefreshServiceDetails -> hosts.onTrain.refreshOnTrainOption()
            is CommuteEffect.NotifyProximity -> Unit
        }
    }

    private fun buildReduceContext(): SessionReduceContext =
        SessionReduceContext(
            boardingConfig = boardingConfig,
            stationStopConfig = stationStopConfig,
            cachedBoardingServices = handoff.cachedBoardingServices,
            routeStations = hosts.onTrain.routeStations(),
            onTrainCallingPoints = hosts.onTrain.latestOption()?.callingPoints.orEmpty(),
        )

    private fun startOnTrainService(destinationName: String, minutes: Long?) {
        val simulating = DebugWalkUp.isSimulatingLocation()
        if (!hasOsLocationPermission() && !simulating) {
            Log.w(TAG, "Skip on-train FGS — no location permission and not simulating")
            return
        }
        runCatching {
            OnTrainTrackingService.start(
                context = appContext,
                destinationName = destinationName,
                minutesRemaining = minutes?.toInt(),
                allowWithoutLocation = simulating,
            )
            publishHandoff(handoff.copy(onTrainServiceStarted = true))
        }
    }

    private fun updateOnTrainService(destinationName: String, minutes: Long?) {
        if (!handoff.onTrainServiceStarted) {
            startOnTrainService(destinationName, minutes)
            return
        }
        val simulating = DebugWalkUp.isSimulatingLocation()
        if (!hasOsLocationPermission() && !simulating) {
            stopOnTrainService()
            return
        }
        runCatching {
            OnTrainTrackingService.update(
                context = appContext,
                destinationName = destinationName,
                minutesRemaining = minutes?.toInt(),
                allowWithoutLocation = simulating,
            )
        }
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

    private fun stopOnTrainService() {
        if (!handoff.onTrainServiceStarted) return
        runCatching { OnTrainTrackingService.endJourney(appContext) }
        publishHandoff(handoff.copy(onTrainServiceStarted = false))
    }

    companion object {
        private const val TAG = "CommuteRuntime"
    }
}
