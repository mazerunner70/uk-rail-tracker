package com.ukrailtracker.app.domain.model

data class Station(
    val crsCode: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val operatorName: String,
    val operatorCode: String,
    val addressLines: List<String> = emptyList(),
    val postcode: String? = null,
    val accessibility: StationAccessibility = StationAccessibility(),
    val stationMapUrl: String? = null,
)

data class StationAccessibility(
    val stepFreeCategory: String? = null,
    val rampAvailable: Boolean? = null,
    val tactilePaving: String? = null,
    val wheelchairsAvailable: Boolean? = null,
    val accessibleToiletsAvailable: Boolean? = null,
)

data class StationWithDistance(
    val station: Station,
    val distanceMetres: Double,
)

data class UserLocation(
    val latitude: Double,
    val longitude: Double,
    val accuracyMetres: Float? = null,
)
