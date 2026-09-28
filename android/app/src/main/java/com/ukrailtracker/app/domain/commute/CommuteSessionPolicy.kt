package com.ukrailtracker.app.domain.commute

import com.ukrailtracker.app.domain.usecase.CommuteSession
import com.ukrailtracker.app.domain.usecase.isManualOriginOverride
import com.ukrailtracker.app.domain.usecase.lockedOriginOrNull

/**
 * Runtime infrastructure policy for [CommuteSession].
 *
 * **Rule:** transitions belong in `reduce()`; these extensions only answer how the
 * runtime should react to the current session. GPS polling is a fixed interval
 * owned by [com.ukrailtracker.app.domain.location.GpsPoller], not by the session.
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

/** At-station / boarding — Home shows a trip [com.ukrailtracker.app.ui.home.HomeBodyUi]. */
fun CommuteSession.focusesAtStation(): Boolean =
    this is CommuteSession.AtStation || this is CommuteSession.Confirming

/** Trip-focused Home body (at-station, boarding, or on-train). */
fun CommuteSession.focusesTripUi(): Boolean =
    focusesAtStation() || isOnTrain()

/**
 * Whether a raw GPS fix should be reduced. Manual override and Confirming ignore it;
 * presence edges are separate events and hosts decide those themselves.
 */
fun CommuteSession.acceptsWalkUpLocationEvents(): Boolean =
    !isManualOriginOverride() && !isConfirming()

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

/** Fixed GPS poll interval for [com.ukrailtracker.app.domain.location.GpsPoller]. */
const val LOCATION_POLL_MS = 10_000L
