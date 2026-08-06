package com.ukrailtracker.app.domain.repository

import com.ukrailtracker.app.domain.model.DepartureBoard

interface DepartureRepository {
    suspend fun getBoard(
        crs: String,
        forceRefresh: Boolean = false,
        filterCrs: String? = null,
        filterType: String = "to",
        numRows: Int = 10,
    ): DepartureBoard
}
