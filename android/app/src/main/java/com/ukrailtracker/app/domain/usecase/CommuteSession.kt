package com.ukrailtracker.app.domain.usecase

import com.ukrailtracker.app.domain.model.JourneyOption
import com.ukrailtracker.app.domain.model.PinnedJourney
import com.ukrailtracker.app.domain.model.StationWithDistance

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

/** Walk-up presence for one commute: away, dwelling near a favourite, or locked at a station. */
sealed interface CommuteSession {
    /** Not inside a walk-up lock; home shows not-at-station. */
    data object Idle : CommuteSession

    /** Inside the geofence and dwelling toward a lock; home shows Near. */
    data class Near(val dwelling: WalkUpPhase.Dwelling) : CommuteSession

    /** Origin is locked (GPS or override); [boarding] tracks leave-station motion. */
    data class AtStation(
        val origin: LockedOrigin,
        val boarding: BoardingPhase = BoardingPhase.Idle,
    ) : CommuteSession

    /** Leave-station motion matched a favourite-bound service; waiting for confirm / picker / timeout. */
    data class Confirming(
        val origin: LockedOrigin,
        val assumed: BoardingServiceSnapshot,
        val alternatives: List<BoardingServiceSnapshot>,
        val triggeredAtEpochMs: Long,
        val showPicker: Boolean = false,
    ) : CommuteSession

    /** Pinned ride; GPS goes to [DetectStationStop], Darwin to progress / finish. */
    data class OnTrain(
        val pin: PinnedJourney,
        val stop: StationStopPhase = StationStopPhase.Moving,
        val confirmedStopCrs: String? = null,
        val buzzed: Set<Int> = emptySet(),
    ) : CommuteSession
}

/** Input that may change [CommuteSession]; GPS, override, and permission all go through reduce. */
sealed interface CommuteEvent {
    /** One GPS fix plus nearest station, resolved speed, and whether a cold-start lock is allowed. */
    data class Location(
        val sample: WalkUpSample,
        val nearest: StationWithDistance?,
        val speedMps: Double?,
        val alreadyPresentOk: Boolean = false,
    ) : CommuteEvent

    /** User (or debug) pinned an origin; GPS must not overwrite until cleared. */
    data class SetOriginOverride(val origin: LockedOrigin) : CommuteEvent

    /** Drop a manual origin lock and return to Idle so GPS can re-lock. */
    data object ClearOverride : CommuteEvent

    /** OS geofence ENTER. Wake hint only — never locks presence. */
    data class OsGeofenceEnter(val crs: String, val stationName: String) : CommuteEvent

    /** Notification tap: elevate GPS poll and allow a cold-start lock; GPS still owns presence. */
    data class OpenedForFrequentChecks(
        val untilEpochMs: Long,
        val hintCrs: String? = null,
    ) : CommuteEvent

    /** Location permission gone; presence must reset to Idle. */
    data object PermissionLost : CommuteEvent

    /** Confirm the assumed boarding service and pin it. */
    data class Confirm(val nowEpochMs: Long) : CommuteEvent

    /** Pin a specific co-departing service from the boarding picker. */
    data class SelectService(val serviceId: String, val nowEpochMs: Long) : CommuteEvent

    /** Show co-departing alternatives; auto-accept still targets [CommuteSession.Confirming.assumed]. */
    data object ShowPicker : CommuteEvent

    /** Dismiss the boarding prompt and return to Idle. */
    data object Dismiss : CommuteEvent

    /** Wall-clock tick; auto-accepts when [nowEpochMs] is past triggeredAt + auto-accept window. */
    data class Tick(val nowEpochMs: Long) : CommuteEvent

    /** Re-attach after DataStore pin restore or an external pin write. */
    data class RestorePin(val journey: PinnedJourney) : CommuteEvent

    /** Pin vanished from DataStore without a local End trip (no journey_log). */
    data object PinCleared : CommuteEvent

    /** User tapped End trip; log and clear the pin. */
    data object EndTrip : CommuteEvent

    /** Drop the ride without writing journey_log (debug teardown). */
    data object DiscardTrip : CommuteEvent

    /** Fresh Darwin service details for the pinned ride. */
    data class ServiceDetails(val option: JourneyOption) : CommuteEvent
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

    /** Raise in-app GPS poll until [untilEpochMs] and allow a first-fix lock. */
    data class ElevateFrequentChecks(val untilEpochMs: Long) : CommuteEffect

    /** Post the “near station” notification; tap opens Home for frequent checks. */
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
