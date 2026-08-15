package com.ukrailtracker.app.domain.usecase

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.abs

/**
 * Snapshot of a favourite-bound service cached while at the station (M5).
 * Enough to pin without another network round-trip.
 */
data class BoardingServiceSnapshot(
    val serviceId: String,
    val originCrs: String,
    val originName: String,
    val destinationCrs: String,
    val destinationName: String,
    val operatorName: String,
    val scheduledDepartureLabel: String,
    val expectedDepartureLabel: String,
    val platform: String?,
    /** Index in the user's favourite list (0 = highest priority). */
    val favouriteRank: Int,
)

data class BoardingMatchResult(
    val assumed: BoardingServiceSnapshot,
    val alternatives: List<BoardingServiceSnapshot>,
)

object MatchBoardingCandidates {

    /**
     * Flatten M4 favourite-bound destination blocks into boarding snapshots.
     * [favouriteDestinationOrder] defines priority (first = highest).
     */
    fun snapshotsFromDestinations(
        originCrs: String,
        originName: String,
        destinations: List<FavouriteDestinationServices>,
        favouriteDestinationOrder: List<String>,
        operatorNameFallback: String = "",
    ): List<BoardingServiceSnapshot> {
        val rankByCrs = favouriteDestinationOrder
            .mapIndexed { index, crs -> crs.trim().uppercase() to index }
            .toMap()

        return destinations.flatMap { dest ->
            val rank = rankByCrs[dest.destinationCrs.uppercase()]
                ?: Int.MAX_VALUE
            dest.services
                .filter { !it.isCancelled }
                .map { service ->
                    BoardingServiceSnapshot(
                        serviceId = service.serviceId,
                        originCrs = originCrs.uppercase(),
                        originName = originName,
                        destinationCrs = dest.destinationCrs.uppercase(),
                        destinationName = service.destinationName.ifBlank { dest.destinationName },
                        operatorName = operatorNameFallback,
                        scheduledDepartureLabel = service.scheduledDepartureLabel,
                        expectedDepartureLabel = service.expectedDepartureLabel,
                        platform = service.platform,
                        favouriteRank = rank,
                    )
                }
        }
    }

    /**
     * Match cached services to a boarding signal time.
     * Default assumption: lowest [BoardingServiceSnapshot.favouriteRank], then closest departure.
     */
    fun match(
        cached: List<BoardingServiceSnapshot>,
        boardedAtEpochMs: Long,
        pastMinutes: Long = BoardingConfig().matchPastMinutes,
        futureMinutes: Long = BoardingConfig().matchFutureMinutes,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): BoardingMatchResult? {
        val now = LocalDateTime.ofInstant(Instant.ofEpochMilli(boardedAtEpochMs), zoneId)
        val candidates = cached.filter { snapshot ->
            val label = JourneyTimeUtils.effectiveDepartureLabel(
                scheduledDepartureLabel = snapshot.scheduledDepartureLabel,
                expectedDepartureLabel = snapshot.expectedDepartureLabel,
            )
            JourneyTimeUtils.isDepartureAroundNow(
                departureLabel = label,
                now = now,
                pastMinutes = pastMinutes,
                futureMinutes = futureMinutes,
            )
        }
        if (candidates.isEmpty()) return null

        val assumed = candidates.minWith(
            compareBy<BoardingServiceSnapshot> { it.favouriteRank }
                .thenBy { abs(minutesFromNow(it, now) ?: Long.MAX_VALUE) }
                .thenBy { it.scheduledDepartureLabel }
                .thenBy { it.serviceId },
        )
        val alternatives = candidates
            .filter { it.serviceId != assumed.serviceId }
            .sortedWith(
                compareBy<BoardingServiceSnapshot> { it.favouriteRank }
                    .thenBy { abs(minutesFromNow(it, now) ?: Long.MAX_VALUE) }
                    .thenBy { it.scheduledDepartureLabel },
            )
        return BoardingMatchResult(assumed = assumed, alternatives = alternatives)
    }

    private fun minutesFromNow(snapshot: BoardingServiceSnapshot, now: LocalDateTime): Long? {
        val label = JourneyTimeUtils.effectiveDepartureLabel(
            scheduledDepartureLabel = snapshot.scheduledDepartureLabel,
            expectedDepartureLabel = snapshot.expectedDepartureLabel,
        )
        return JourneyTimeUtils.minutesUntilDeparture(label, now)
    }
}
