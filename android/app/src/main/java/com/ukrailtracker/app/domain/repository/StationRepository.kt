package com.ukrailtracker.app.domain.repository

import com.ukrailtracker.app.domain.model.Station
import com.ukrailtracker.app.domain.model.StationWithDistance

interface StationRepository {
    suspend fun ensureImported()
    suspend fun count(): Int
    suspend fun getByCrs(crs: String): Station?
    suspend fun search(query: String): List<Station>
    suspend fun getNearby(lat: Double, lng: Double, limit: Int = 10): List<StationWithDistance>
}
