package com.ukrailtracker.app.domain.session.host

import com.ukrailtracker.app.domain.geo.GeoUtils
import com.ukrailtracker.app.domain.session.SessionReduceContext
import com.ukrailtracker.app.domain.session.SessionWalkUpSupport
import com.ukrailtracker.app.domain.session.toAtSession
import com.ukrailtracker.app.domain.session.toNearSession
import com.ukrailtracker.app.domain.usecase.BoardingConfig
import com.ukrailtracker.app.domain.usecase.BoardingPhase
import com.ukrailtracker.app.domain.usecase.BoardingServiceSnapshot
import com.ukrailtracker.app.domain.usecase.CommuteEffect
import com.ukrailtracker.app.domain.usecase.CommuteEvent
import com.ukrailtracker.app.domain.usecase.CommuteSession
import com.ukrailtracker.app.domain.usecase.CommuteTransition
import com.ukrailtracker.app.domain.usecase.DetectBoarding
import com.ukrailtracker.app.domain.usecase.FavouriteDestinationServices
import com.ukrailtracker.app.domain.usecase.JourneyTimeUtils
import com.ukrailtracker.app.domain.usecase.LockedOrigin
import com.ukrailtracker.app.domain.usecase.MatchBoardingCandidates
import com.ukrailtracker.app.domain.usecase.PlatformWatchPhase
import com.ukrailtracker.app.domain.usecase.isManualOriginOverride
import com.ukrailtracker.app.domain.usecase.lockedOriginOrNull
import com.ukrailtracker.app.domain.usecase.platformWatchOrNull
import com.ukrailtracker.app.ui.home.AtStationAux
import com.ukrailtracker.app.ui.home.PlatformWatchUi
import com.ukrailtracker.app.ui.home.WalkUpDestinationUi
import com.ukrailtracker.app.ui.home.mergeAtStationAuxForArrival
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * At-station host.
 *
 * - [onEntry] merges at-station aux and loads favourite-bound departures when needed.
 * - [onExit] stops platform watch and clears at-station aux + boarding cache into the handoff.
 * - [onActiveChange] handles CRS changes and platform-watch start/stop/replace without leaving the type.
 */
class AtStationHost(
    private val services: SessionHostServices? = null,
) : SessionHost<CommuteSession.AtStation> {
    override val typeId: SessionTypeId = SessionTypeId.AtStation

    private var liveHandoff: SessionHandoff = SessionHandoff()
    private var destinationLoadJob: Job? = null
    private var platformWatchJob: Job? = null

    override fun handle(
        session: CommuteSession.AtStation,
        event: CommuteEvent,
        context: SessionReduceContext,
    ): CommuteTransition = when (event) {
        is CommuteEvent.Location -> reduceLocation(session, event, context)
        is CommuteEvent.NearStation -> reduceStepDown(session, event.toNearSession())
        is CommuteEvent.AtStation -> reduceStillAt(session, event)
        CommuteEvent.LeftStation -> reduceLeave(session)
        is CommuteEvent.WatchPlatform -> reduceWatchPlatform(session, event)
        CommuteEvent.ClearPlatformWatch -> reduceClearPlatformWatch(session)
        is CommuteEvent.SetOriginOverride -> CommuteTransition(
            session = CommuteSession.AtStation(origin = event.origin, boarding = BoardingPhase.Idle),
        )
        is CommuteEvent.OsGeofenceEnter -> CommuteTransition(
            session = session,
            effects = listOf(
                CommuteEffect.NotifyProximity(crs = event.crs, stationName = event.stationName),
            ),
        )
        is CommuteEvent.RestorePin -> SessionWalkUpSupport.restorePin(session, event.journey)
        CommuteEvent.ClearOverride,
        CommuteEvent.PermissionLost,
        CommuteEvent.Dismiss -> CommuteTransition(CommuteSession.Idle)
        else -> CommuteTransition(session)
    }

    override fun onEntry(
        previousId: SessionTypeId,
        handoff: SessionHandoff,
        session: CommuteSession.AtStation,
    ): SessionHandoff {
        liveHandoff = handoff
        val merged = mergeAtStationAuxForArrival(
            session,
            handoff.aux.atStation,
            handoff.lastLoadedOriginCrs,
        )
        liveHandoff = liveHandoff.copy(aux = liveHandoff.aux.copy(atStation = merged))
        publish()
        val needsLoad = !session.origin.crs.equals(liveHandoff.lastLoadedOriginCrs, ignoreCase = true)
        if (needsLoad) {
            loadDestinations(originCrs = session.origin.crs, forceRefresh = session.origin.manualOverride)
        }
        val watch = session.platformWatchOrNull()
        if (watch != null) {
            startPlatformWatchFor(watch)
        }
        return liveHandoff
    }

    override fun onActiveChange(
        previous: CommuteSession.AtStation,
        next: CommuteSession.AtStation,
        handoff: SessionHandoff,
    ): SessionHandoff {
        liveHandoff = handoff
        val crsChanged = !previous.origin.crs.equals(next.origin.crs, ignoreCase = true) ||
            next.origin.manualOverride != previous.origin.manualOverride && next.origin.manualOverride
        if (crsChanged || previous != next) {
            val sameStation = liveHandoff.aux.atStation.originCrs.equals(next.origin.crs, ignoreCase = true) &&
                !next.origin.manualOverride
            if (!sameStation) {
                stopPlatformWatch()
                liveHandoff = liveHandoff.copy(platformNotified = false)
            }
            liveHandoff = liveHandoff.copy(
                aux = liveHandoff.aux.copy(
                    atStation = mergeAtStationAuxForArrival(
                        next,
                        liveHandoff.aux.atStation,
                        liveHandoff.lastLoadedOriginCrs,
                    ),
                ),
            )
            publish()
            if (!previous.origin.crs.equals(next.origin.crs, ignoreCase = true) ||
                (next.origin.manualOverride && !previous.origin.manualOverride)
            ) {
                loadDestinations(
                    originCrs = next.origin.crs,
                    forceRefresh = next.origin.manualOverride,
                )
            }
        }

        val prevWatch = previous.platformWatch as? PlatformWatchPhase.Watching
        val nextWatch = next.platformWatch as? PlatformWatchPhase.Watching
        val leftWatch = prevWatch != null && prevWatch.serviceId != nextWatch?.serviceId
        if (leftWatch) {
            stopPlatformWatch()
            liveHandoff = liveHandoff.copy(
                platformNotified = false,
                aux = liveHandoff.aux.copy(
                    atStation = liveHandoff.aux.atStation.copy(platformWatch = null),
                ),
            )
            publish()
        }
        if (nextWatch != null && prevWatch?.serviceId != nextWatch.serviceId) {
            startPlatformWatchFor(nextWatch)
        }
        return liveHandoff
    }

    override fun onExit(nextId: SessionTypeId): SessionHandoff {
        destinationLoadJob?.cancel()
        destinationLoadJob = null
        stopPlatformWatch()
        liveHandoff = liveHandoff.copy(
            platformNotified = false,
            cachedBoardingServices = emptyList(),
            aux = liveHandoff.aux.copy(atStation = AtStationAux()),
        )
        publish()
        return liveHandoff
    }

    fun reloadDestinations(originCrs: String, forceRefresh: Boolean) {
        loadDestinations(originCrs = originCrs, forceRefresh = forceRefresh)
    }

    fun refreshPlatformWatch() {
        val svc = services ?: return
        val watch = svc.currentAtStationBody()?.platformWatch ?: return
        svc.scope.launch { pingPlatform(watch) }
    }

    fun clearBoardingCache() {
        liveHandoff = liveHandoff.copy(cachedBoardingServices = emptyList())
        publish()
    }

    private fun startPlatformWatchFor(watch: PlatformWatchPhase.Watching) {
        val svc = services ?: return
        liveHandoff = liveHandoff.copy(platformNotified = false)
        val ui = svc.mapPlatformWatch(watch) ?: return
        liveHandoff = liveHandoff.copy(
            aux = liveHandoff.aux.copy(
                atStation = liveHandoff.aux.atStation.copy(platformWatch = ui),
            ),
        )
        publish()
        startPlatformWatch(ui)
        val already = ui.platform
        if (already != null) {
            liveHandoff = liveHandoff.copy(platformNotified = true)
            publish()
            svc.emitPlatformAppeared(already)
        }
    }

    private fun reduceWatchPlatform(
        session: CommuteSession.AtStation,
        event: CommuteEvent.WatchPlatform,
    ): CommuteTransition {
        if (!session.origin.crs.equals(event.originCrs, ignoreCase = true)) {
            return CommuteTransition(session)
        }
        return CommuteTransition(
            session.copy(
                platformWatch = PlatformWatchPhase.Watching(
                    serviceId = event.serviceId,
                    originCrs = event.originCrs.uppercase(),
                    destinationCrs = event.destinationCrs.uppercase(),
                    destinationName = event.destinationName,
                    scheduledDepartureLabel = event.scheduledDepartureLabel,
                    expectedDepartureLabel = event.expectedDepartureLabel,
                    platform = event.platform?.takeIf { it.isNotBlank() },
                ),
            ),
        )
    }

    private fun reduceClearPlatformWatch(session: CommuteSession.AtStation): CommuteTransition {
        if (session.platformWatch is PlatformWatchPhase.Idle) return CommuteTransition(session)
        return CommuteTransition(session.copy(platformWatch = PlatformWatchPhase.Idle))
    }

    private fun reduceLocation(
        session: CommuteSession.AtStation,
        event: CommuteEvent.Location,
        context: SessionReduceContext,
    ): CommuteTransition {
        if (session.isManualOriginOverride()) {
            return CommuteTransition(session)
        }
        val boardingNext = DetectBoarding.next(
            phase = session.boarding,
            sample = event.sample,
            originLatitude = session.origin.latitude,
            originLongitude = session.origin.longitude,
            speedMps = event.speedMps,
            config = context.boardingConfig,
        )
        if (boardingNext is BoardingPhase.Triggered && session.boarding !is BoardingPhase.Triggered) {
            return matchBoarding(
                origin = session.origin,
                triggered = boardingNext,
                cached = context.cachedBoardingServices,
                boardingConfig = context.boardingConfig,
                zoneId = context.zoneId,
            )
        }
        if (session.boarding is BoardingPhase.Sustaining && boardingNext is BoardingPhase.Idle) {
            val distance = GeoUtils.haversineMetres(
                session.origin.latitude,
                session.origin.longitude,
                event.sample.latitude,
                event.sample.longitude,
            )
            if (distance >= context.boardingConfig.leaveMetres) {
                return CommuteTransition(CommuteSession.Idle)
            }
        }
        if (boardingNext != session.boarding) {
            return CommuteTransition(session.copy(boarding = boardingNext))
        }
        return CommuteTransition(session)
    }

    private fun reduceStepDown(
        session: CommuteSession.AtStation,
        near: CommuteSession.Near,
    ): CommuteTransition {
        if (holdsPresence(session)) return CommuteTransition(session)
        return CommuteTransition(near)
    }

    private fun reduceLeave(session: CommuteSession.AtStation): CommuteTransition {
        if (holdsPresence(session)) return CommuteTransition(session)
        return CommuteTransition(CommuteSession.Idle)
    }

    private fun reduceStillAt(
        session: CommuteSession.AtStation,
        event: CommuteEvent.AtStation,
    ): CommuteTransition {
        if (session.isManualOriginOverride()) return CommuteTransition(session)
        if (holdsPresence(session)) return CommuteTransition(session)
        if (session.origin.crs.equals(event.crs, ignoreCase = true)) {
            return CommuteTransition(
                session.copy(origin = session.origin.copy(distanceMetres = event.distanceMetres)),
            )
        }
        return CommuteTransition(event.toAtSession())
    }

    private fun holdsPresence(session: CommuteSession.AtStation): Boolean =
        session.isManualOriginOverride() ||
            session.boarding is BoardingPhase.Sustaining ||
            session.boarding is BoardingPhase.Triggered

    private fun matchBoarding(
        origin: LockedOrigin,
        triggered: BoardingPhase.Triggered,
        cached: List<BoardingServiceSnapshot>,
        boardingConfig: BoardingConfig,
        zoneId: ZoneId,
    ): CommuteTransition {
        val match = MatchBoardingCandidates.match(
            cached = cached,
            boardedAtEpochMs = triggered.triggeredAtEpochMs,
            pastMinutes = boardingConfig.matchPastMinutes,
            futureMinutes = boardingConfig.matchFutureMinutes,
            zoneId = zoneId,
        ) ?: return CommuteTransition(CommuteSession.Idle)
        return CommuteTransition(
            session = CommuteSession.Confirming(
                origin = origin,
                assumed = match.assumed,
                alternatives = match.alternatives,
                triggeredAtEpochMs = triggered.triggeredAtEpochMs,
            ),
        )
    }

    private fun startPlatformWatch(initial: PlatformWatchUi) {
        val svc = services ?: return
        stopPlatformWatch()
        platformWatchJob = svc.scope.launch {
            pingPlatform(initial)
            while (isActive) {
                val watch = svc.currentAtStationBody()?.platformWatch ?: break
                if (watch.serviceId != initial.serviceId) break
                val departureLabel = JourneyTimeUtils.effectiveDepartureLabel(
                    scheduledDepartureLabel = watch.scheduledDepartureLabel,
                    expectedDepartureLabel = watch.expectedDepartureLabel,
                )
                val minutesLeft = JourneyTimeUtils.minutesUntilDeparture(departureLabel)
                delay(platformPollIntervalMs(minutesLeft))
                val still = svc.currentAtStationBody()?.platformWatch ?: break
                if (still.serviceId != initial.serviceId) break
                pingPlatform(still)
            }
        }
    }

    private suspend fun pingPlatform(watch: PlatformWatchUi) {
        val svc = services ?: return
        svc.updateAtStationAux { at ->
            val current = at.platformWatch?.takeIf { it.serviceId == watch.serviceId } ?: return@updateAtStationAux at
            at.copy(platformWatch = current.copy(polling = true, errorMessage = null))
        }
        syncFromServices()
        try {
            val option = svc.journeyRepository.getOption(
                originCrs = watch.originCrs,
                destinationCrs = watch.destinationCrs,
                serviceId = watch.serviceId,
                forceRefresh = true,
            )
            val platform = option?.platform?.trim()?.takeIf { it.isNotEmpty() }
            val checked = formatCheckedNow()
            svc.updateAtStationAux { at ->
                val current = at.platformWatch?.takeIf { it.serviceId == watch.serviceId }
                    ?: return@updateAtStationAux at
                val destinations = at.destinations.map { dest ->
                    if (!dest.destinationCrs.equals(watch.destinationCrs, ignoreCase = true)) {
                        dest
                    } else {
                        dest.copy(
                            services = dest.services.map { svcRow ->
                                if (svcRow.serviceId == watch.serviceId && platform != null) {
                                    svcRow.copy(platform = platform)
                                } else {
                                    svcRow
                                }
                            },
                        )
                    }
                }
                at.copy(
                    destinations = destinations,
                    platformWatch = current.copy(
                        platform = platform ?: current.platform,
                        scheduledDepartureLabel = option?.scheduledDepartureLabel
                            ?.takeIf { it.isNotBlank() }
                            ?: current.scheduledDepartureLabel,
                        expectedDepartureLabel = option?.expectedDepartureLabel
                            ?: current.expectedDepartureLabel,
                        checkedAtLabel = checked,
                        polling = false,
                        errorMessage = if (option == null) "Service not found on board" else null,
                    ),
                )
            }
            syncFromServices()
            if (platform != null && !liveHandoff.platformNotified) {
                liveHandoff = liveHandoff.copy(platformNotified = true)
                publish()
                svc.emitPlatformAppeared(platform)
            }
        } catch (t: Throwable) {
            svc.updateAtStationAux { at ->
                val current = at.platformWatch?.takeIf { it.serviceId == watch.serviceId }
                    ?: return@updateAtStationAux at
                at.copy(
                    platformWatch = current.copy(
                        polling = false,
                        errorMessage = t.message ?: "Could not refresh service",
                    ),
                )
            }
            syncFromServices()
        }
    }

    private fun stopPlatformWatch() {
        platformWatchJob?.cancel()
        platformWatchJob = null
    }

    private fun loadDestinations(originCrs: String, forceRefresh: Boolean) {
        val svc = services ?: return
        destinationLoadJob?.cancel()
        destinationLoadJob = svc.scope.launch {
            val favourites = svc.favouritesStore.getFavourites()
            val noFavourites = favourites.none { it.uppercase() != originCrs.uppercase() }
            svc.updateAtStationAux { current ->
                current.copy(
                    originCrs = originCrs.uppercase(),
                    destinationsLoading = true,
                    noFavouritesConfigured = noFavourites,
                )
            }
            syncFromServices()
            if (noFavourites) {
                liveHandoff = liveHandoff.copy(
                    cachedBoardingServices = emptyList(),
                    lastLoadedOriginCrs = originCrs.uppercase(),
                )
                stopPlatformWatch()
                liveHandoff = liveHandoff.copy(platformNotified = false)
                svc.updateAtStationAux { current ->
                    current.copy(
                        destinations = emptyList(),
                        destinationsLoading = false,
                        noFavouritesConfigured = true,
                        platformWatch = null,
                    )
                }
                syncFromServices()
                publish()
                return@launch
            }
            val rows = svc.loadFavouriteBoundDepartures.load(
                originCrs = originCrs,
                favouriteDestinationCrs = favourites,
                forceRefresh = forceRefresh,
            )
            liveHandoff = liveHandoff.copy(lastLoadedOriginCrs = originCrs.uppercase())
            val originName = svc.sessionFlow.value.lockedOriginOrNull()?.name ?: originCrs
            liveHandoff = liveHandoff.copy(
                cachedBoardingServices = MatchBoardingCandidates.snapshotsFromDestinations(
                    originCrs = originCrs,
                    originName = originName,
                    destinations = rows,
                    favouriteDestinationOrder = favourites,
                ),
            )
            svc.updateAtStationAux { current ->
                val mapped = rows.map { it.toUi() }
                val watch = current.platformWatch?.let { existingWatch ->
                    val refreshedPlatform = mapped
                        .flatMap { it.services }
                        .find { it.serviceId == existingWatch.serviceId }
                        ?.platform
                        ?.takeIf { it.isNotBlank() }
                    existingWatch.copy(platform = refreshedPlatform ?: existingWatch.platform)
                }
                val destinations = if (watch?.platform != null) {
                    mapped.map { dest ->
                        dest.copy(
                            services = dest.services.map { svcRow ->
                                if (svcRow.serviceId == watch.serviceId) {
                                    svcRow.copy(platform = watch.platform)
                                } else {
                                    svcRow
                                }
                            },
                        )
                    }
                } else {
                    mapped
                }
                current.copy(
                    destinations = destinations,
                    destinationsLoading = false,
                    noFavouritesConfigured = false,
                    platformWatch = watch,
                )
            }
            syncFromServices()
            publish()
        }
    }

    private fun FavouriteDestinationServices.toUi(): WalkUpDestinationUi =
        WalkUpDestinationUi(
            destinationCrs = destinationCrs,
            destinationName = destinationName,
            services = services,
            errorMessage = errorMessage,
        )

    private fun publish() {
        services?.publishHandoff(liveHandoff)
    }

    private fun syncFromServices() {
        val svc = services ?: return
        liveHandoff = svc.handoff.copy(
            cachedBoardingServices = liveHandoff.cachedBoardingServices,
            lastLoadedOriginCrs = liveHandoff.lastLoadedOriginCrs,
            platformNotified = liveHandoff.platformNotified,
        )
    }

    companion object {
        private val CHECKED_FMT: DateTimeFormatter =
            DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault())

        private fun formatCheckedNow(): String = CHECKED_FMT.format(Instant.now())

        fun platformPollIntervalMs(minutesUntilDeparture: Long?): Long = when {
            minutesUntilDeparture == null -> 20_000L
            minutesUntilDeparture > 20L -> 60_000L
            minutesUntilDeparture > 10L -> 30_000L
            minutesUntilDeparture > 5L -> 15_000L
            minutesUntilDeparture >= 0L -> 10_000L
            else -> 30_000L
        }
    }
}
