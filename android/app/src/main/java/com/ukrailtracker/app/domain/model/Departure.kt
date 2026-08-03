package com.ukrailtracker.app.domain.model

enum class TrainStatus {
    OnTime,
    Delayed,
    Cancelled,
    Departed,
    Unknown,
}

data class Departure(
    val destination: String,
    val destinationCrs: String?,
    val scheduledTimeLabel: String,
    val expectedLabel: String,
    val platform: String?,
    val operatorName: String,
    val status: TrainStatus,
    val delayMinutes: Int?,
    val serviceId: String?,
    val isArrival: Boolean = false,
)

data class DepartureBoard(
    val crsCode: String,
    val stationName: String,
    val generatedAtEpochMs: Long,
    val fetchedAtEpochMs: Long,
    val departures: List<Departure>,
    val fromCache: Boolean = false,
)
