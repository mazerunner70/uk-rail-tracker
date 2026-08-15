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
import androidx.compose.runtime.rememberCoroutineScope
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
import com.ukrailtracker.app.domain.repository.JourneyRepository
import com.ukrailtracker.app.domain.repository.StationRepository
import com.ukrailtracker.app.location.SimPhase
import com.ukrailtracker.app.location.SimulatingLocationProvider
import com.ukrailtracker.app.location.WalkUpLocationSimulator
import com.ukrailtracker.app.ui.home.PlatformWatchUi
import com.ukrailtracker.app.ui.theme.NeonGreen
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.time.DayOfWeek

/**
 * Opt-in simulator facade (compiled when `-PincludeSimulator=true`).
 *
 * App-bar icon drives Idle → Walking → AtStation → OnTrain → ReadyDisembark → Disembarking.
 */
object DebugWalkUp {
    private val simulator = WalkUpLocationSimulator()

    private val _errors = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val errors: SharedFlow<String> = _errors.asSharedFlow()

    val phase = simulator.phase

    fun wrapLocation(real: LocationProvider): LocationProvider =
        SimulatingLocationProvider(real = real, simulator = simulator)

    /** True while a non-idle phase is injecting GPS — real location permission is not required. */
    fun isSimulatingLocation(): Boolean = simulator.isActive

    @Composable
    fun HomeAppBarAction(
        stationRepository: StationRepository,
        favouritesStore: FavouritesStore,
        commuteWindowStore: CommuteWindowStore,
        journeyRepository: JourneyRepository,
        pinnedJourneyStore: PinnedJourneyStore,
        selectedWatch: PlatformWatchUi?,
        onPreparePresence: (elevatePollForMs: Long) -> Unit,
    ) {
        val scope = rememberCoroutineScope()
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
                scope.launch {
                    onIconClick(
                        context = context,
                        phase = simPhase,
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
            SimPhase.AtStation -> boardSelectedService(
                context = context,
                selectedWatch = selectedWatch,
                journeyRepository = journeyRepository,
                stationRepository = stationRepository,
                pinnedJourneyStore = pinnedJourneyStore,
            )
            SimPhase.ReadyDisembark -> {
                pinnedJourneyStore.clear()
                simulator.startDisembark()
                Log.i(TAG, "Disembark walk started")
            }
            SimPhase.Walking, SimPhase.OnTrain, SimPhase.Disembarking -> {
                // Active motion — ignore taps.
            }
        }
    }

    private suspend fun startWalk(
        stationRepository: StationRepository,
        favouritesStore: FavouritesStore,
        commuteWindowStore: CommuteWindowStore,
        onPreparePresence: (elevatePollForMs: Long) -> Unit,
    ) {
        stationRepository.ensureImported()
        val station = stationRepository.getByCrs(
            WalkUpLocationSimulator.DEFAULT_TARGET_CRS,
        ) ?: return
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
        Log.i(TAG, "Walk-up started toward APS")
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
        if (watch == null || serviceId == null) {
            _errors.tryEmit(context.getString(R.string.home_debug_sim_select_service))
            return
        }
        val option = runCatching {
            journeyRepository.getServiceDetails(
                serviceId = serviceId,
                originCrs = watch.originCrs,
                destinationCrs = watch.destinationCrs,
                destinationNameFallback = watch.destinationName,
            )
        }.onFailure { t ->
            Log.w(TAG, "GetServiceDetails failed: ${t.message}")
            _errors.tryEmit(t.message ?: "Could not load service details")
        }.getOrNull() ?: return

        val waypoints = WalkUpLocationSimulator.waypointsFromOption(option, stationRepository)
        if (waypoints.size < 2) {
            Log.w(TAG, "Board aborted — need ≥2 geocoded calling points (got ${waypoints.size})")
            _errors.tryEmit("Service has too few calling points to simulate")
            return
        }
        simulator.startOnTrain(
            waypoints = waypoints,
            dwellMs = WalkUpLocationSimulator.DEFAULT_STOP_DWELL_MS,
        )
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
                    .ifBlank { watch.scheduledDepartureLabel },
                pinnedAtEpochMs = System.currentTimeMillis(),
            ),
        )
        Log.i(TAG, "OnTrain started — ${waypoints.size} waypoints, pinned ${option.serviceId}")
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
