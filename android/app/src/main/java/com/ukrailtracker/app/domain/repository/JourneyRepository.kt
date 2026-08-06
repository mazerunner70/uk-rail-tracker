package com.ukrailtracker.app.domain.repository

import com.ukrailtracker.app.domain.model.JourneyOption

interface JourneyRepository {
    /**
     * Direct services from [originCrs] calling at / terminating at [destinationCrs]
     * via LDB `filterCrs` (M3 v1 — not a full multi-leg RTJP planner).
     */
    suspend fun getOptions(
        originCrs: String,
        destinationCrs: String,
        forceRefresh: Boolean = false,
    ): List<JourneyOption>

    suspend fun getOption(
        originCrs: String,
        destinationCrs: String,
        serviceId: String,
        forceRefresh: Boolean = false,
    ): JourneyOption?
}
