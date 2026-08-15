package com.ukrailtracker.app.data.repository

import com.ukrailtracker.app.data.local.db.JourneyLogDao
import com.ukrailtracker.app.data.local.db.JourneyLogEntity
import com.ukrailtracker.app.domain.model.JourneyLogEntry
import com.ukrailtracker.app.domain.model.JourneyOption
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/**
 * Writes completed / unpinned journey snapshots for M7 compensation.
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
        dao.recent(limit).map { it.toDomain() }

    fun observeRecent(limit: Int = 100): Flow<List<JourneyLogEntry>> =
        dao.observeRecent(limit).map { list -> list.map { it.toDomain() } }

    private fun JourneyLogEntity.toDomain(): JourneyLogEntry =
        JourneyLogEntry(
            id = id,
            dateIso = dateIso,
            originCrs = originCrs,
            destinationCrs = destinationCrs,
            operatorName = operatorName,
            operatorCode = operatorCode,
            scheduledDepartureLabel = scheduledDepartureLabel,
            scheduledArrivalLabel = scheduledArrivalLabel,
            expectedArrivalLabel = expectedArrivalLabel,
            delayMinutes = delayMinutes,
            wasCancelled = wasCancelled,
            serviceId = serviceId,
            claimStatus = claimStatus,
        )
}
