package com.ukrailtracker.app.domain.commute

import com.ukrailtracker.app.domain.usecase.CommuteSession
import com.ukrailtracker.app.domain.usecase.isManualOriginOverride
import com.ukrailtracker.app.domain.usecase.lockedOriginOrNull

/**
 * Runtime infrastructure policy for [CommuteSession].
 *
 * **Rule:** transitions belong in `reduce()`; these extensions only answer how the
 * runtime should sample GPS and schedule jobs for the current session.
 * Do not encode the same acceptance rule in both places without a comment linking them.
 *
 * **Adding a state that changes GPS behaviour:** add/adjust one extension here + a
 * table row in [CommuteSessionPolicyTest] — do not sprinkle `is CommuteSession.X`
 * checks through [CommuteRuntime].
 */

enum class CommutePhase {
    Idle,
    Near,
    AtStation,
    Confirming,
    OnTrain,
}

fun CommuteSession.phase(): CommutePhase = when (this) {
    CommuteSession.Idle -> CommutePhase.Idle
    is CommuteSession.Near -> CommutePhase.Near
    is CommuteSession.AtStation -> CommutePhase.AtStation
    is CommuteSession.Confirming -> CommutePhase.Confirming
    is CommuteSession.OnTrain -> CommutePhase.OnTrain
}

fun CommuteSession.isOnTrain(): Boolean = this is CommuteSession.OnTrain

fun CommuteSession.isConfirming(): Boolean = this is CommuteSession.Confirming

/** At-station / boarding UI focus (mirrors [com.ukrailtracker.app.ui.home.focusesAtStation]). */
fun CommuteSession.focusesAtStation(): Boolean =
    this is CommuteSession.AtStation || this is CommuteSession.Confirming

/** Trip-focused Home body (at-station, boarding, or on-train). */
fun CommuteSession.focusesTripUi(): Boolean =
    focusesAtStation() || isOnTrain()

/**
 * Whether the location watch may feed [com.ukrailtracker.app.domain.usecase.CommuteEvent.Location]
 * into reduce. Manual override and Confirming ignore GPS (reduce also ignores override;
 * Confirming is runtime-only — see reduce location branch).
 */
fun CommuteSession.acceptsWalkUpLocationEvents(): Boolean =
    !isManualOriginOverride() && !isConfirming()

/** Nearby station lookup is unused on-train (route stops come from service details). */
fun CommuteSession.shouldResolveNearbyStations(): Boolean = !isOnTrain()

/** Single CRS accessor for locked origins (AtStation / Confirming). */
fun CommuteSession.lockedOriginCrs(): String? = lockedOriginOrNull()?.crs

/**
 * Origin CRS for favourite-bound departure reload. Confirming keeps the boarding
 * prompt and does not refresh destination lists.
 */
fun CommuteSession.destinationLoadOriginCrs(): String? = when (this) {
    is CommuteSession.AtStation -> origin.crs
    is CommuteSession.Confirming -> null
    else -> lockedOriginCrs()
}

/** Show "location unavailable" only when not already locked at a station or on a train. */
fun CommuteSession.showsLocationUnavailableOverlay(): Boolean =
    !isOnTrain() && this !is CommuteSession.AtStation

/** Clear sensing overlay when restarting the location watch from cold Idle. */
fun CommuteSession.clearsPresenceOverlayOnWatchStart(): Boolean =
    this is CommuteSession.Idle &&
        !isManualOriginOverride() &&
        !isConfirming() &&
        !isOnTrain()

/** Cold-start "already at station" lock is only valid from Idle. */
fun CommuteSession.allowsColdStartAlreadyPresentLock(): Boolean =
    this is CommuteSession.Idle

/**
 * Request a fresh GPS fix while presence is actively watched (or elevated poll is on).
 * Confirming is included so the loop keeps ticking; [acceptsWalkUpLocationEvents] no-ops the sample.
 */
fun CommuteSession.forceRefreshLocationSample(
    nowEpochMs: Long,
    elevatedUntilEpochMs: Long,
): Boolean =
    isOnTrain() ||
        this is CommuteSession.AtStation ||
        isConfirming() ||
        nowEpochMs < elevatedUntilEpochMs

fun CommuteSession.locationPollIntervalMs(
    nowEpochMs: Long,
    elevatedUntilEpochMs: Long,
): Long {
    if (isOnTrain()) return LOCATION_POLL_ON_TRAIN_MS
    if (nowEpochMs < elevatedUntilEpochMs) return LOCATION_POLL_WATCHING_MS
    val watching = this is CommuteSession.AtStation || isConfirming()
    return if (watching) LOCATION_POLL_WATCHING_MS else LOCATION_POLL_MS
}

const val LOCATION_POLL_MS = 10_000L
const val LOCATION_POLL_WATCHING_MS = 4_000L
const val LOCATION_POLL_ON_TRAIN_MS = 5_000L
