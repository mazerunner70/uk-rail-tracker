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

    /**
     * Live single-service detail via LDB `GetServiceDetails` (timing + calling points).
     * Used once a service is selected for an on-train journey.
     */
    suspend fun getServiceDetails(
        serviceId: String,
        originCrs: String,
        destinationCrs: String,
        destinationNameFallback: String = "",
    ): JourneyOption
}
