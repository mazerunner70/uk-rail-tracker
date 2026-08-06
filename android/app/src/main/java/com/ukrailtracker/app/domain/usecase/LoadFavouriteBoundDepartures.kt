package com.ukrailtracker.app.domain.usecase

import com.ukrailtracker.app.domain.model.JourneyOption
import com.ukrailtracker.app.domain.repository.JourneyRepository
import com.ukrailtracker.app.domain.repository.StationRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.time.LocalDateTime

data class FavouriteDestinationServices(
    val destinationCrs: String,
    val destinationName: String,
    val services: List<FavouriteServiceUiModel>,
    val errorMessage: String? = null,
)

data class FavouriteServiceUiModel(
    val serviceId: String,
    val scheduledDepartureLabel: String,
    val expectedDepartureLabel: String,
    val platform: String?,
    val statusLabel: String,
    val delayMinutes: Int?,
    val isCancelled: Boolean,
    val durationMinutes: Int?,
    val destinationName: String,
)

/**
 * Loads next-hour direct services from [originCrs] to each favourite destination CRS.
 */
class LoadFavouriteBoundDepartures(
    private val journeyRepository: JourneyRepository,
    private val stationRepository: StationRepository,
) {
    suspend fun load(
        originCrs: String,
        favouriteDestinationCrs: List<String>,
        forceRefresh: Boolean = false,
        now: LocalDateTime = LocalDateTime.now(),
        maxServicesPerDestination: Int = 3,
    ): List<FavouriteDestinationServices> {
        val origin = originCrs.trim().uppercase()
        val destinations = favouriteDestinationCrs
            .map { it.trim().uppercase() }
            .filter { it.isNotBlank() && it != origin }
            .distinct()

        if (destinations.isEmpty()) return emptyList()

        return coroutineScope {
            destinations.map { dest ->
                async {
                    val name = stationRepository.getByCrs(dest)?.name ?: dest
                    try {
                        val options = journeyRepository.getOptions(
                            originCrs = origin,
                            destinationCrs = dest,
                            forceRefresh = forceRefresh,
                        )
                        val nextHour = JourneyTimeUtils.filterNextHour(options, now = now)
                            .take(maxServicesPerDestination)
                        FavouriteDestinationServices(
                            destinationCrs = dest,
                            destinationName = name,
                            services = nextHour.map { it.toUiModel(fallbackDestName = name) },
                        )
                    } catch (t: Throwable) {
                        FavouriteDestinationServices(
                            destinationCrs = dest,
                            destinationName = name,
                            services = emptyList(),
                            errorMessage = t.message ?: "Could not load services",
                        )
                    }
                }
            }.awaitAll()
        }
    }

    private fun JourneyOption.toUiModel(fallbackDestName: String): FavouriteServiceUiModel {
        val statusLabel = when {
            isCancelled -> "Cancelled"
            delayMinutes != null && delayMinutes > 0 -> "+$delayMinutes"
            expectedDepartureLabel.equals("On time", ignoreCase = true) -> "On time"
            else -> expectedDepartureLabel.ifBlank { "On time" }
        }
        return FavouriteServiceUiModel(
            serviceId = serviceId,
            scheduledDepartureLabel = scheduledDepartureLabel,
            expectedDepartureLabel = expectedDepartureLabel,
            platform = platform,
            statusLabel = statusLabel,
            delayMinutes = delayMinutes,
            isCancelled = isCancelled,
            durationMinutes = JourneyTimeUtils.durationMinutes(this),
            destinationName = destinationName.ifBlank { fallbackDestName },
        )
    }
}
