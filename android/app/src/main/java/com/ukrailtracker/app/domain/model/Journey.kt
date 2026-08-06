package com.ukrailtracker.app.domain.model

data class JourneyPair(
    val originCrs: String,
    val destinationCrs: String,
)

data class CallingPoint(
    val locationName: String,
    val crs: String?,
    val scheduledTimeLabel: String?,
    val expectedLabel: String?,
    val isCancelled: Boolean = false,
)

data class JourneyOption(
    val serviceId: String,
    val originCrs: String,
    val destinationCrs: String,
    val destinationName: String,
    val scheduledDepartureLabel: String,
    val expectedDepartureLabel: String,
    val scheduledArrivalLabel: String?,
    val expectedArrivalLabel: String?,
    val platform: String?,
    val operatorName: String,
    val status: TrainStatus,
    val delayMinutes: Int?,
    val callingPoints: List<CallingPoint>,
    val isCancelled: Boolean,
)

data class PinnedJourney(
    val serviceId: String,
    val originCrs: String,
    val destinationCrs: String,
    val originName: String,
    val destinationName: String,
    val operatorName: String,
    val scheduledDepartureLabel: String,
    val pinnedAtEpochMs: Long,
)

data class JourneyLogEntry(
    val id: Long = 0,
    val dateIso: String,
    val originCrs: String,
    val destinationCrs: String,
    val operatorName: String,
    val operatorCode: String?,
    val scheduledDepartureLabel: String,
    val scheduledArrivalLabel: String?,
    val expectedArrivalLabel: String?,
    val delayMinutes: Int?,
    val wasCancelled: Boolean,
    val serviceId: String,
    val claimStatus: String = "not_eligible",
)
