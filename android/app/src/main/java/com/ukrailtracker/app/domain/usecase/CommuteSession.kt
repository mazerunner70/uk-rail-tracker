package com.ukrailtracker.app.domain.usecase

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

    /** Origin is locked (GPS or override); [boarding] is Idle until the boarding slice owns it. */
    data class AtStation(
        val origin: LockedOrigin,
        val boarding: BoardingPhase = BoardingPhase.Idle,
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

    /** Notification tap: focus this station and load boards without waiting for walk-up dwell. */
    data class OpenedForFrequentChecks(val origin: LockedOrigin) : CommuteEvent

    /** Location permission gone; presence must reset to Idle. */
    data object PermissionLost : CommuteEvent
}

/** Side effect reduce cannot perform itself; the ViewModel interprets these. */
sealed interface CommuteEffect {
    /** Fetch favourite-bound departures for the locked origin CRS. */
    data class LoadFavouriteBoundDepartures(
        val originCrs: String,
        val forceRefresh: Boolean = false,
    ) : CommuteEffect
}

/** Next session plus any effects to run after reduce. */
data class CommuteTransition(
    val session: CommuteSession,
    val effects: List<CommuteEffect> = emptyList(),
)

/** True when presence is a user-chosen origin that GPS must not overwrite. */
fun CommuteSession.isManualOriginOverride(): Boolean =
    this is CommuteSession.AtStation && origin.manualOverride

/** Locked origin if at a station, otherwise null. */
fun CommuteSession.lockedOriginOrNull(): LockedOrigin? =
    (this as? CommuteSession.AtStation)?.origin
