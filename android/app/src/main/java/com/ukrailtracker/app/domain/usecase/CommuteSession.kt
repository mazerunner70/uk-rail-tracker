package com.ukrailtracker.app.domain.usecase

import com.ukrailtracker.app.domain.model.JourneyOption
import com.ukrailtracker.app.domain.model.PinnedJourney
import com.ukrailtracker.app.domain.model.StationWithDistance
import com.ukrailtracker.app.domain.session.EventPriority

/** Station the session has locked as the current origin, including coordinates for leave-station motion. */
data class LockedOrigin(
    val crs: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val lockedAtEpochMs: Long,
    val distanceMetres: Double,
    val manualOverride: Boolean = false,
)

/** Walk-up presence for one commute: away, inside the near radius, or locked at a station. */
sealed interface CommuteSession {
    /** Outside every station radius; home shows not-at-station. */
    data object Idle : CommuteSession

    /** Inside the near radius and outside the at radius. */
    data class Near(
        val crs: String,
        val name: String,
        val latitude: Double,
        val longitude: Double,
        val distanceMetres: Double,
    ) : CommuteSession

    /** Origin is locked (GPS or override); [boarding] tracks leave-station motion. */
    data class AtStation(
        val origin: LockedOrigin,
        val boarding: BoardingPhase = BoardingPhase.Idle,
        val platformWatch: PlatformWatchPhase = PlatformWatchPhase.Idle,
    ) : CommuteSession

    /** Leave-station motion matched a favourite-bound service; waiting for confirm / picker / timeout. */
    data class Confirming(
        val origin: LockedOrigin,
        val assumed: BoardingServiceSnapshot,
        val alternatives: List<BoardingServiceSnapshot>,
        val triggeredAtEpochMs: Long,
        val showPicker: Boolean = false,
    ) : CommuteSession

    /**
     * Pinned ride.
     * [gpsMotion] = raw stop detector; [ridePhase] = platform wait / EnRoute / AtCallingPoint;
     * [progressAnchorCrs] = last confirmed calling point for GPS leg progress;
     * [journeyFraction] = 0…1 from GPS (snapped at calling point), not Darwin clocks.
     * Darwin supplies expected times / ETA labels only.
     */
    data class OnTrain(
        val pin: PinnedJourney,
        val gpsMotion: GpsMotion = GpsMotion.Moving,
        val ridePhase: OnTrainRidePhase = initialWaitingAtOrigin(),
        val progressAnchorCrs: String? = null,
        val journeyFraction: Float = 0f,
        val buzzed: Set<Int> = emptySet(),
    ) : CommuteSession
}

/** Input that may change [CommuteSession]; GPS, override, and permission all go through reduce. */
sealed interface CommuteEvent {
    val priority: EventPriority

    /** One GPS fix plus nearest station and resolved speed. Does not change presence by itself. */
    data class Location(
        val sample: WalkUpSample,
        val nearest: StationWithDistance?,
        val speedMps: Double?,
    ) : CommuteEvent {
        override val priority: EventPriority get() = EventPriority.AMBIENT_SENSOR
    }

    /** Nearest station is inside the near radius and outside the at radius. */
    data class NearStation(
        val crs: String,
        val name: String,
        val latitude: Double,
        val longitude: Double,
        val distanceMetres: Double,
        val epochMs: Long,
    ) : CommuteEvent {
        override val priority: EventPriority get() = EventPriority.AMBIENT_SENSOR
    }

    /** Nearest station is inside the at radius. */
    data class AtStation(
        val crs: String,
        val name: String,
        val latitude: Double,
        val longitude: Double,
        val distanceMetres: Double,
        val epochMs: Long,
    ) : CommuteEvent {
        override val priority: EventPriority get() = EventPriority.AMBIENT_SENSOR
    }

    /** Nearest station left the near radius. */
    data object LeftStation : CommuteEvent {
        override val priority: EventPriority get() = EventPriority.AMBIENT_SENSOR
    }

    /** User (or debug) pinned an origin; GPS must not overwrite until cleared. */
    data class SetOriginOverride(val origin: LockedOrigin) : CommuteEvent {
        override val priority: EventPriority get() = EventPriority.USER_INTENT
    }

    /** Drop a manual origin lock and return to Idle so GPS can re-lock. */
    data object ClearOverride : CommuteEvent {
        override val priority: EventPriority get() = EventPriority.USER_INTENT
    }

    /** OS geofence ENTER. Wake hint only — never locks presence. */
    data class OsGeofenceEnter(val crs: String, val stationName: String) : CommuteEvent {
        override val priority: EventPriority get() = EventPriority.AMBIENT_SENSOR
    }

    /** Location permission gone; presence must reset to Idle. */
    data object PermissionLost : CommuteEvent {
        override val priority: EventPriority get() = EventPriority.SYSTEM_CRITICAL
    }

    /** Confirm the assumed boarding service and pin it. */
    data class Confirm(val nowEpochMs: Long) : CommuteEvent {
        override val priority: EventPriority get() = EventPriority.USER_INTENT
    }

    /** Pin a specific co-departing service from the boarding picker. */
    data class SelectService(val serviceId: String, val nowEpochMs: Long) : CommuteEvent {
        override val priority: EventPriority get() = EventPriority.USER_INTENT
    }

    /** Show co-departing alternatives; auto-accept still targets [CommuteSession.Confirming.assumed]. */
    data object ShowPicker : CommuteEvent {
        override val priority: EventPriority get() = EventPriority.USER_INTENT
    }

    /** Dismiss the boarding prompt and return to Idle. */
    data object Dismiss : CommuteEvent {
        override val priority: EventPriority get() = EventPriority.USER_INTENT
    }

    /** Wall-clock tick; auto-accepts when [nowEpochMs] is past triggeredAt + auto-accept window. */
    data class Tick(val nowEpochMs: Long) : CommuteEvent {
        override val priority: EventPriority get() = EventPriority.DEADLINE_TIMEOUT
    }

    /** Re-attach after DataStore pin restore or an external pin write. */
    data class RestorePin(val journey: PinnedJourney) : CommuteEvent {
        override val priority: EventPriority get() = EventPriority.AMBIENT_SENSOR
    }

    /** Pin vanished from DataStore without a local End trip (no journey_log). */
    data object PinCleared : CommuteEvent {
        override val priority: EventPriority get() = EventPriority.SYSTEM_CRITICAL
    }

    /** User tapped End trip; log and clear the pin. */
    data object EndTrip : CommuteEvent {
        override val priority: EventPriority get() = EventPriority.USER_INTENT
    }

    /** Drop the ride without writing journey_log (debug teardown). */
    data object DiscardTrip : CommuteEvent {
        override val priority: EventPriority get() = EventPriority.USER_INTENT
    }

    /** Fresh Darwin service details for the pinned ride. */
    data class ServiceDetails(val option: JourneyOption) : CommuteEvent {
        override val priority: EventPriority get() = EventPriority.AMBIENT_SENSOR
    }

    /** User tapped "Boarded" while waiting on the platform for the pinned service. */
    data object Boarded : CommuteEvent {
        override val priority: EventPriority get() = EventPriority.USER_INTENT
    }

    /** Select a favourite-bound service to poll until its departure platform appears. */
    data class WatchPlatform(
        val serviceId: String,
        val originCrs: String,
        val destinationCrs: String,
        val destinationName: String,
        val scheduledDepartureLabel: String,
        val expectedDepartureLabel: String,
        val platform: String?,
    ) : CommuteEvent {
        override val priority: EventPriority get() = EventPriority.USER_INTENT
    }

    /** Stop polling the selected departure platform. */
    data object ClearPlatformWatch : CommuteEvent {
        override val priority: EventPriority get() = EventPriority.USER_INTENT
    }
}

/** Side effect reduce cannot perform itself; the ViewModel interprets these. */
sealed interface CommuteEffect {
    /** Fetch favourite-bound departures for the locked origin CRS. */
    data class LoadFavouriteBoundDepartures(
        val originCrs: String,
        val forceRefresh: Boolean = false,
    ) : CommuteEffect

    /** Persist the pinned journey; interpreter writes DataStore. */
    data class Pin(val journey: PinnedJourney) : CommuteEffect

    /** Start the on-train foreground notification. */
    data class StartTracking(
        val destinationName: String,
        val minutesRemaining: Long? = null,
    ) : CommuteEffect

    /** Update the on-train foreground notification. */
    data class UpdateTracking(
        val destinationName: String,
        val minutesRemaining: Long?,
    ) : CommuteEffect

    /** Stop the on-train foreground notification. */
    data object StopTracking : CommuteEffect

    /** Write a journey_log row from the last Darwin option. */
    data object WriteJourneyLog : CommuteEffect

    /** Clear PinnedJourney from DataStore. */
    data object ClearPin : CommuteEffect

    /** Arrival-buzz thresholds (3 / 2 / 1 min) that should vibrate now. */
    data class Vibrate(val thresholds: List<Int>) : CommuteEffect

    /** Fetch GetServiceDetails after GPS confirms a new calling-point stop. */
    data object RefreshServiceDetails : CommuteEffect

    /** Post the “near station” notification; tap opens Home for a fresh GPS fix. */
    data class NotifyProximity(val crs: String, val stationName: String) : CommuteEffect
}

/** Next session plus any effects to run after reduce. */
data class CommuteTransition(
    val session: CommuteSession,
    val effects: List<CommuteEffect> = emptyList(),
)

/** True when presence is a user-chosen origin that GPS must not overwrite. */
fun CommuteSession.isManualOriginOverride(): Boolean =
    this is CommuteSession.AtStation && origin.manualOverride

/** Locked origin if at a station or confirming boarding, otherwise null. */
fun CommuteSession.lockedOriginOrNull(): LockedOrigin? = when (this) {
    is CommuteSession.AtStation -> origin
    is CommuteSession.Confirming -> origin
    else -> null
}

fun CommuteSession.onTrainOrNull(): CommuteSession.OnTrain? = this as? CommuteSession.OnTrain

fun CommuteSession.pinnedJourneyOrNull(): PinnedJourney? = onTrainOrNull()?.pin

/** Nested at-station sub-phase: idle browsing vs watching one departure for its platform. */
sealed interface PlatformWatchPhase {
    data object Idle : PlatformWatchPhase

    data class Watching(
        val serviceId: String,
        val originCrs: String,
        val destinationCrs: String,
        val destinationName: String,
        val scheduledDepartureLabel: String,
        val expectedDepartureLabel: String,
        val platform: String?,
    ) : PlatformWatchPhase
}

fun CommuteSession.platformWatchOrNull(): PlatformWatchPhase.Watching? =
    (this as? CommuteSession.AtStation)?.platformWatch as? PlatformWatchPhase.Watching
