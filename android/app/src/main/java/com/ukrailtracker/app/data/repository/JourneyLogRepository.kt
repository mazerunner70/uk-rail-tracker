package com.ukrailtracker.app.data.repository

import com.ukrailtracker.app.data.local.db.JourneyLogDao
import com.ukrailtracker.app.data.local.db.JourneyLogEntity
import com.ukrailtracker.app.domain.model.JourneyLogEntry
import com.ukrailtracker.app.domain.model.JourneyOption
import java.time.LocalDate

/**
 * Writes completed / unpinned journey snapshots for M4 compensation.
 */
class JourneyLogRepository(
    private val dao: JourneyLogDao,
) {
    suspend fun logFromOption(option: JourneyOption) {
        val delay = option.delayMinutes ?: 0
        val claimStatus = when {
            option.isCancelled -> "eligible"
            delay >= 15 -> "eligible"
            else -> "not_eligible"
        }
        dao.insert(
            JourneyLogEntity(
                dateIso = LocalDate.now().toString(),
                originCrs = option.originCrs,
                destinationCrs = option.destinationCrs,
                operatorName = option.operatorName,
                operatorCode = null,
                scheduledDepartureLabel = option.scheduledDepartureLabel,
                scheduledArrivalLabel = option.scheduledArrivalLabel,
                expectedArrivalLabel = option.expectedArrivalLabel,
                delayMinutes = option.delayMinutes,
                wasCancelled = option.isCancelled,
                serviceId = option.serviceId,
                claimStatus = claimStatus,
            ),
        )
    }

    suspend fun recent(limit: Int = 50): List<JourneyLogEntry> =
        dao.recent(limit).map { e ->
            JourneyLogEntry(
                id = e.id,
                dateIso = e.dateIso,
                originCrs = e.originCrs,
                destinationCrs = e.destinationCrs,
                operatorName = e.operatorName,
                operatorCode = e.operatorCode,
                scheduledDepartureLabel = e.scheduledDepartureLabel,
                scheduledArrivalLabel = e.scheduledArrivalLabel,
                expectedArrivalLabel = e.expectedArrivalLabel,
                delayMinutes = e.delayMinutes,
                wasCancelled = e.wasCancelled,
                serviceId = e.serviceId,
                claimStatus = e.claimStatus,
            )
        }
}
