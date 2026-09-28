package com.ukrailtracker.app.debug

import android.content.Context
import android.util.Log
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Train
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ukrailtracker.app.R
import com.ukrailtracker.app.data.local.datastore.CommuteWindowStore
import com.ukrailtracker.app.data.local.datastore.FavouritesStore
import com.ukrailtracker.app.data.local.datastore.PinnedJourneyStore
import com.ukrailtracker.app.domain.location.LocationProvider
import com.ukrailtracker.app.domain.model.CommuteWindow
import com.ukrailtracker.app.domain.model.PinnedJourney
import com.ukrailtracker.app.domain.usecase.JourneyTimeUtils
import com.ukrailtracker.app.domain.repository.JourneyRepository
import com.ukrailtracker.app.domain.repository.StationRepository
import com.ukrailtracker.app.location.SimPhase
import com.ukrailtracker.app.location.SimulatingLocationProvider
import com.ukrailtracker.app.location.WalkUpLocationSimulator
import com.ukrailtracker.app.ui.home.HomeBodyUi
import com.ukrailtracker.app.ui.home.PlatformWatchUi
import com.ukrailtracker.app.ui.home.PresenceChromeUi
import com.ukrailtracker.app.ui.theme.NeonGreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import java.time.DayOfWeek

/**
 * Opt-in simulator facade (compiled when `-PincludeSimulator=true`).
 *
 * App-bar icon drives Idle → Walking → AtStation → OnTrain → ReadyDisembark → Disembarking.
 */
object DebugWalkUp {
    private val simulator = WalkUpLocationSimulator()

    /** Outlives Compose recomposition — do not use [rememberCoroutineScope] for sim I/O. */
    private val simScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _errors = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val errors: SharedFlow<String> = _errors.asSharedFlow()

    /** Avoid restarting the same on-train simulation on every recomposition. */
    private var lastSyncedServiceId: String? = null

    val phase = simulator.phase

    fun wrapLocation(real: LocationProvider): LocationProvider =
        SimulatingLocationProvider(real = real, simulator = simulator)

    /** True while a non-idle phase is injecting GPS — real location permission is not required. */
    fun isSimulatingLocation(): Boolean = simulator.isActive

    /**
     * Keep simulator phase aligned with commute presence.
     * When the app boards via the normal flow (confirm / auto-accept / restore pin) while the
     * simulator is still at [SimPhase.AtStation], start the on-train GPS route automatically.
     */
    suspend fun syncWithAppPresence(
        chrome: PresenceChromeUi,
        body: HomeBodyUi?,
        journeyRepository: JourneyRepository,
        stationRepository: StationRepository,
        pinnedJourneyStore: PinnedJourneyStore,
    ) {
        when (val journey = body as? HomeBodyUi.OnJourney) {
            null -> {
                // Permission / unavailable overlays are not a trip exit — do not tear down GPS sim
                // (that falls through to fused GPS, which can stall the on-train poll loop).
                if (chrome is PresenceChromeUi.NeedsPermission ||
                    chrome is PresenceChromeUi.LocationUnavailable
                ) {
                    return
                }
                when (simulator.phase.value) {
                    SimPhase.OnTrain, SimPhase.ReadyDisembark -> {
                        simulator.stop()
                        lastSyncedServiceId = null
                        Log.i(TAG, "Simulator stopped — app left on-train")
                    }
                    else -> Unit
                }
            }
            else -> {
                val awaitingDeparture = isAwaitingSimulatedDeparture(journey)
                if (!journey.showJourneyProgress && !awaitingDeparture) {
                    return
                }
                val simPhase = simulator.phase.value
                if (simPhase == SimPhase.OnTrain || simPhase == SimPhase.ReadyDisembark) {
                    if (lastSyncedServiceId == journey.serviceId && !awaitingDeparture) return
                }
                if (simPhase == SimPhase.Idle) return
                startOnTrainForJourney(
                    journey = journey,
                    journeyRepository = journeyRepository,
                    stationRepository = stationRepository,
                    pinnedJourneyStore = pinnedJourneyStore,
                    writePin = false,
                    forceRestart = awaitingDeparture,
                )
            }
        }
    }

    @Composable
    fun HomeAppBarAction(
        stationRepository: StationRepository,
        favouritesStore: FavouritesStore,
        commuteWindowStore: CommuteWindowStore,
        journeyRepository: JourneyRepository,
        pinnedJourneyStore: PinnedJourneyStore,
        body: HomeBodyUi?,
        selectedWatch: PlatformWatchUi?,
        onPreparePresence: (elevatePollForMs: Long) -> Unit,
    ) {
        val simPhase by simulator.phase.collectAsStateWithLifecycle()
        val blinking = simPhase == SimPhase.Walking ||
            simPhase == SimPhase.OnTrain ||
            simPhase == SimPhase.Disembarking
        val transition = rememberInfiniteTransition(label = "simBlink")
        val animatedAlpha by transition.animateFloat(
            initialValue = 0.35f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1_200),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "simBlinkAlpha",
        )
        val alpha = if (blinking) animatedAlpha else 1f
        val (icon, tint, description) = iconForPhase(simPhase)
        val context = LocalContext.current
        IconButton(
            onClick = {
                simScope.launch {
                    onIconClick(
                        context = context,
                        phase = simPhase,
                        body = body,
                        stationRepository = stationRepository,
                        favouritesStore = favouritesStore,
                        commuteWindowStore = commuteWindowStore,
                        journeyRepository = journeyRepository,
                        pinnedJourneyStore = pinnedJourneyStore,
                        selectedWatch = selectedWatch,
                        onPreparePresence = onPreparePresence,
                    )
                }
            },
        ) {
            Icon(
                imageVector = icon,
                contentDescription = description,
                tint = tint,
                modifier = Modifier.alpha(alpha),
            )
        }
    }

    @Composable
    private fun iconForPhase(phase: SimPhase): Triple<ImageVector, Color, String> {
        val green = NeonGreen
        val blue = Color(0xFF42A5F5)
        return when (phase) {
            SimPhase.Idle -> Triple(
                Icons.AutoMirrored.Filled.DirectionsWalk,
                green,
                stringResource(R.string.home_debug_sim_start_walk),
            )
            SimPhase.Walking -> Triple(
                Icons.AutoMirrored.Filled.DirectionsWalk,
                green,
                stringResource(R.string.home_debug_sim_walking),
            )
            SimPhase.AtStation -> Triple(
                Icons.Default.Train,
                green,
                stringResource(R.string.home_debug_sim_board_train),
            )
            SimPhase.OnTrain -> Triple(
                Icons.Default.Train,
                green,
                stringResource(R.string.home_debug_sim_on_train),
            )
            SimPhase.ReadyDisembark -> Triple(
                Icons.AutoMirrored.Filled.DirectionsWalk,
                blue,
                stringResource(R.string.home_debug_sim_disembark),
            )
            SimPhase.Disembarking -> Triple(
                Icons.AutoMirrored.Filled.DirectionsWalk,
                blue,
                stringResource(R.string.home_debug_sim_disembarking),
            )
        }
    }

    private suspend fun onIconClick(
        context: Context,
        phase: SimPhase,
        body: HomeBodyUi?,
        stationRepository: StationRepository,
        favouritesStore: FavouritesStore,
        commuteWindowStore: CommuteWindowStore,
        journeyRepository: JourneyRepository,
        pinnedJourneyStore: PinnedJourneyStore,
        selectedWatch: PlatformWatchUi?,
        onPreparePresence: (elevatePollForMs: Long) -> Unit,
    ) {
        when (phase) {
            SimPhase.Idle -> startWalk(
                stationRepository = stationRepository,
                favouritesStore = favouritesStore,
                commuteWindowStore = commuteWindowStore,
                onPreparePresence = onPreparePresence,
            )
            SimPhase.AtStation -> {
                when (val journey = body as? HomeBodyUi.OnJourney) {
                    null -> boardSelectedService(
                        context = context,
                        selectedWatch = selectedWatch,
                        journeyRepository = journeyRepository,
                        stationRepository = stationRepository,
                        pinnedJourneyStore = pinnedJourneyStore,
                    )
                    else -> startOnTrainForJourney(
                        journey = journey,
                        journeyRepository = journeyRepository,
                        stationRepository = stationRepository,
                        pinnedJourneyStore = pinnedJourneyStore,
                        writePin = false,
                        forceRestart = true,
                    )
                }
            }
            SimPhase.OnTrain -> {
                val journey = body as? HomeBodyUi.OnJourney
                if (journey != null && isAwaitingSimulatedDeparture(journey)) {
                    startOnTrainForJourney(
                        journey = journey,
                        journeyRepository = journeyRepository,
                        stationRepository = stationRepository,
                        pinnedJourneyStore = pinnedJourneyStore,
                        writePin = false,
                        forceRestart = true,
                    )
                }
            }
            SimPhase.ReadyDisembark -> {
                pinnedJourneyStore.clear()
                lastSyncedServiceId = null
                simulator.startDisembark()
                Log.i(TAG, "Disembark walk started")
            }
            SimPhase.Walking, SimPhase.Disembarking -> {
                // Active motion — ignore taps.
            }
        }
    }

    /** App is boarded but journey progress has not started yet — sim should depart on tap. */
    private fun isAwaitingSimulatedDeparture(journey: HomeBodyUi.OnJourney): Boolean =
        journey.preDeparture?.awaitingDeparture == true &&
            !journey.showJourneyProgress

    private suspend fun startOnTrainForJourney(
        journey: HomeBodyUi.OnJourney,
        journeyRepository: JourneyRepository,
        stationRepository: StationRepository,
        pinnedJourneyStore: PinnedJourneyStore,
        writePin: Boolean,
        forceRestart: Boolean = false,
    ): Boolean = startOnTrainSimulation(
        serviceId = journey.serviceId,
        originCrs = journey.originCrs,
        destinationCrs = journey.destinationCrs,
        destinationName = journey.destinationName,
        scheduledDepartureLabel = journey.scheduledDepartureLabel,
        journeyRepository = journeyRepository,
        stationRepository = stationRepository,
        pinnedJourneyStore = pinnedJourneyStore,
        writePin = writePin,
        forceRestart = forceRestart,
    )

    private suspend fun startWalk(
        stationRepository: StationRepository,
        favouritesStore: FavouritesStore,
        commuteWindowStore: CommuteWindowStore,
        onPreparePresence: (elevatePollForMs: Long) -> Unit,
    ) {
        stationRepository.ensureImported()
        val station = stationRepository.getByCrs(CRS_APS) ?: return
        seedDemoProfile(favouritesStore, commuteWindowStore)
        simulator.startWalkUp(
            targetLatitude = station.latitude,
            targetLongitude = station.longitude,
            startOffsetMetres = WalkUpLocationSimulator.DEFAULT_START_OFFSET_METRES,
            durationMs = WalkUpLocationSimulator.DEFAULT_DURATION_MS,
        )
        onPreparePresence(
            WalkUpLocationSimulator.DEFAULT_DURATION_MS + 60_000L,
        )
        Log.i(TAG, "Walk-up started toward Apsley ($CRS_APS)")
    }

    private suspend fun boardSelectedService(
        context: Context,
        selectedWatch: PlatformWatchUi?,
        journeyRepository: JourneyRepository,
        stationRepository: StationRepository,
        pinnedJourneyStore: PinnedJourneyStore,
    ) {
        val watch = selectedWatch
        val serviceId = watch?.serviceId?.takeIf { it.isNotBlank() }
        if (watch != null && serviceId != null) {
            startOnTrainSimulation(
                serviceId = serviceId,
                originCrs = watch.originCrs,
                destinationCrs = watch.destinationCrs,
                destinationName = watch.destinationName,
                scheduledDepartureLabel = watch.scheduledDepartureLabel,
                journeyRepository = journeyRepository,
                stationRepository = stationRepository,
                pinnedJourneyStore = pinnedJourneyStore,
                writePin = true,
            )
            return
        }
        val pinned = pinnedJourneyStore.getPinned()
        if (pinned != null) {
            startOnTrainSimulation(
                serviceId = pinned.serviceId,
                originCrs = pinned.originCrs,
                destinationCrs = pinned.destinationCrs,
                destinationName = pinned.destinationName,
                scheduledDepartureLabel = pinned.scheduledDepartureLabel,
                journeyRepository = journeyRepository,
                stationRepository = stationRepository,
                pinnedJourneyStore = pinnedJourneyStore,
                writePin = false,
            )
            return
        }
        _errors.tryEmit(context.getString(R.string.home_debug_sim_select_service))
    }

    private suspend fun startOnTrainSimulation(
        serviceId: String,
        originCrs: String,
        destinationCrs: String,
        destinationName: String,
        scheduledDepartureLabel: String,
        journeyRepository: JourneyRepository,
        stationRepository: StationRepository,
        pinnedJourneyStore: PinnedJourneyStore,
        writePin: Boolean,
        forceRestart: Boolean = false,
    ): Boolean {
        if (!forceRestart &&
            lastSyncedServiceId == serviceId &&
            simulator.phase.value in setOf(SimPhase.OnTrain, SimPhase.ReadyDisembark)
        ) {
            return true
        }
        val option = runCatching {
            journeyRepository.getServiceDetails(
                serviceId = serviceId,
                originCrs = originCrs,
                destinationCrs = destinationCrs,
                destinationNameFallback = destinationName,
            )
        }.onFailure { t ->
            if (t is CancellationException) return@onFailure
            Log.w(TAG, "GetServiceDetails failed: ${t.message}")
            _errors.tryEmit(t.message ?: "Could not load service details")
        }.getOrNull() ?: return false

        val waypoints = WalkUpLocationSimulator.waypointsFromOption(option, stationRepository)
        if (waypoints.size < 2) {
            Log.w(TAG, "Board aborted — need ≥2 geocoded calling points (got ${waypoints.size})")
            _errors.tryEmit("Service has too few calling points to simulate")
            return false
        }
        simulator.startOnTrain(
            waypoints = waypoints,
            dwellMs = WalkUpLocationSimulator.DEFAULT_STOP_DWELL_MS,
            originHoldMs = msUntilScheduledDeparture(
                JourneyTimeUtils.effectiveDepartureLabel(
                    scheduledDepartureLabel = option.scheduledDepartureLabel
                        .ifBlank { scheduledDepartureLabel },
                    expectedDepartureLabel = option.expectedDepartureLabel,
                ),
            ),
        )
        lastSyncedServiceId = option.serviceId
        if (writePin) {
            val originName = stationRepository.getByCrs(option.originCrs)?.name ?: option.originCrs
            pinnedJourneyStore.pin(
                PinnedJourney(
                    serviceId = option.serviceId,
                    originCrs = option.originCrs,
                    destinationCrs = option.destinationCrs,
                    originName = originName,
                    destinationName = option.destinationName,
                    operatorName = option.operatorName,
                    scheduledDepartureLabel = option.scheduledDepartureLabel
                        .ifBlank { scheduledDepartureLabel },
                    pinnedAtEpochMs = System.currentTimeMillis(),
                ),
            )
        }
        Log.i(TAG, "OnTrain started — ${waypoints.size} waypoints, service ${option.serviceId}")
        return true
    }

    /** Milliseconds to hold at origin before simulating movement; 0 when departure time has passed. */
    private fun msUntilScheduledDeparture(departureLabel: String): Long {
        val mins = JourneyTimeUtils.minutesUntilDeparture(departureLabel) ?: return 0L
        return mins.coerceAtLeast(0L) * 60_000L
    }

    private suspend fun seedDemoProfile(
        favouritesStore: FavouritesStore,
        commuteWindowStore: CommuteWindowStore,
    ) {
        favouritesStore.replaceAll(listOf(CRS_APS, CRS_EUS))
        val weekdays = setOf(
            DayOfWeek.MONDAY,
            DayOfWeek.TUESDAY,
            DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY,
            DayOfWeek.FRIDAY,
        )
        commuteWindowStore.replaceAll(
            listOf(
                CommuteWindow(
                    id = ID_APS_MORNING,
                    stationCrs = CRS_APS,
                    days = weekdays,
                    startMinutes = 7 * 60,
                    endMinutes = 9 * 60,
                    label = "Simulator APS morning",
                ),
                CommuteWindow(
                    id = ID_EUS_EVENING,
                    stationCrs = CRS_EUS,
                    days = weekdays,
                    startMinutes = 15 * 60,
                    endMinutes = 19 * 60,
                    label = "Simulator EUS evening",
                ),
            ),
        )
    }

    private const val TAG = "UkRailTracker"
    private const val CRS_APS = "APS"
    private const val CRS_EUS = "EUS"
    private const val ID_APS_MORNING = "sim-aps-morning"
    private const val ID_EUS_EVENING = "sim-eus-evening"
}
