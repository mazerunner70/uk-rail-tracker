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
