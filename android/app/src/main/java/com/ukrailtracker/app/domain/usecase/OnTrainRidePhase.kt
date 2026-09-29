package com.ukrailtracker.app.domain.usecase

/**
 * First-class ride sub-state while [CommuteSession.OnTrain].
 *
 * Distinct from walk-up [CommuteSession.AtStation] and from raw [GpsMotion].
 * Progress UI is shown only in [tracksJourneyProgress] phases.
 */
sealed interface OnTrainRidePhase {
    /**
     * Pinned service selected; passenger still on the platform.
     * Train may be approaching from an earlier calling point (see timetable).
     */
    data class WaitingAtOrigin(
        val originStationName: String,
        val minutesUntilTrainArrives: Long?,
        val approachingFromStopName: String?,
        val originIsFirstOnRoute: Boolean,
        val boarded: Boolean,
    ) : OnTrainRidePhase

    /** Passenger marked boarded (or auto T−2 min); waiting for GPS to show movement. */
    data object AwaitingDeparture : OnTrainRidePhase

    /** Train is moving; journey fraction advances from GPS vs leg endpoints. */
    data object EnRoute : OnTrainRidePhase

    /** GPS confirmed a route calling point; progress snaps to that stop. */
    data class AtCallingPoint(
        val crs: String,
        val name: String,
        val confirmedAtEpochMs: Long,
        val expectation: StopExpectation,
    ) : OnTrainRidePhase
}

/**
 * Timetable judgment of a confirmed stop — display / mismatch only, not progress math.
 */
sealed interface StopExpectation {
    data object Unknown : StopExpectation
    data object MatchesTimetableNext : StopExpectation
    data object StillAtPriorStop : StopExpectation
    data object BehindTimetable : StopExpectation
    data class Unexpected(val timetableNextCrs: String?) : StopExpectation
}

fun OnTrainRidePhase.atCallingPointOrNull(): OnTrainRidePhase.AtCallingPoint? =
    this as? OnTrainRidePhase.AtCallingPoint

fun OnTrainRidePhase.waitingAtOriginOrNull(): OnTrainRidePhase.WaitingAtOrigin? =
    this as? OnTrainRidePhase.WaitingAtOrigin

/** True when the journey progress bar and spatial fraction should be shown. */
fun OnTrainRidePhase.tracksJourneyProgress(): Boolean = when (this) {
    is OnTrainRidePhase.EnRoute,
    is OnTrainRidePhase.AtCallingPoint,
    -> true
    is OnTrainRidePhase.WaitingAtOrigin,
    OnTrainRidePhase.AwaitingDeparture,
    -> false
}

fun StopExpectation.isUnexpected(): Boolean = this is StopExpectation.Unexpected

fun initialWaitingAtOrigin(originName: String = ""): OnTrainRidePhase.WaitingAtOrigin =
    OnTrainRidePhase.WaitingAtOrigin(
        originStationName = originName,
        minutesUntilTrainArrives = null,
        approachingFromStopName = null,
        originIsFirstOnRoute = false,
        boarded = false,
    )
