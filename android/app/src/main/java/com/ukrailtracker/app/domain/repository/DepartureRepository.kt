package com.ukrailtracker.app.domain.repository

import com.ukrailtracker.app.domain.model.DepartureBoard

interface DepartureRepository {
    suspend fun getBoard(crs: String, forceRefresh: Boolean = false): DepartureBoard
}
