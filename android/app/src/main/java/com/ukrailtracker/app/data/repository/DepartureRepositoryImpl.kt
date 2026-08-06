package com.ukrailtracker.app.data.repository

import android.util.Log
import com.ukrailtracker.app.data.local.db.DepartureCacheDao
import com.ukrailtracker.app.data.local.db.DepartureCacheEntity
import com.ukrailtracker.app.data.mapper.ArrDepBoardWithDetailsParser
import com.ukrailtracker.app.data.mapper.DepartureBoardJson
import com.ukrailtracker.app.data.remote.darwin.OpenLdbWsApi
import com.ukrailtracker.app.domain.model.DepartureBoard
import com.ukrailtracker.app.domain.repository.DepartureRepository

class DepartureRepositoryImpl(
    private val api: OpenLdbWsApi,
    private val cacheDao: DepartureCacheDao,
    private val parser: ArrDepBoardWithDetailsParser = ArrDepBoardWithDetailsParser(),
    private val clock: () -> Long = { System.currentTimeMillis() },
) : DepartureRepository {

    override suspend fun getBoard(
        crs: String,
        forceRefresh: Boolean,
        filterCrs: String?,
        filterType: String,
        numRows: Int,
    ): DepartureBoard {
        val normalised = crs.uppercase()
        val filter = filterCrs?.trim()?.uppercase()?.takeIf { it.isNotBlank() }
        val now = clock()
        val cached = cacheDao.getLatest(normalised, BOARD_TYPE, filter)?.let { entity ->
            runCatching { DepartureBoardJson.decode(entity.dataJson, fromCache = true) }.getOrNull()
                ?.takeIf { it.crsCode.isNotBlank() || entity.crsCode == normalised }
                ?.copy(
                    crsCode = normalised,
                    fetchedAtEpochMs = entity.fetchedAt,
                    fromCache = true,
                )
        }

        if (!forceRefresh && cached != null) {
            val age = now - cached.fetchedAtEpochMs
            if (age <= FRESH_TTL_MS) return cached.copy(fromCache = false)
        }

        return try {
            val payload = api.getArrDepBoardWithDetails(
                crs = normalised,
                numRows = numRows.coerceIn(1, 10),
                filterCrs = filter,
                filterType = filterType,
            )
            val board = parser.parse(payload, fetchedAtEpochMs = now)
            val toStore = board.copy(crsCode = normalised.ifBlank { board.crsCode })
            cacheDao.deleteFor(normalised, BOARD_TYPE, filter)
            cacheDao.insert(
                DepartureCacheEntity(
                    crsCode = normalised,
                    boardType = BOARD_TYPE,
                    dataJson = DepartureBoardJson.encode(toStore),
                    fetchedAt = now,
                    filterCrs = filter,
                ),
            )
            cacheDao.deleteOlderThan(now - STALE_MAX_MS)
            toStore
        } catch (t: Throwable) {
            Log.w(TAG, "Live board fetch failed for $normalised filter=$filter: ${t.message}")
            if (cached != null && now - cached.fetchedAtEpochMs <= STALE_MAX_MS) {
                return cached.copy(fromCache = true)
            }
            throw t
        }
    }

    companion object {
        private const val TAG = "UkRailTracker"
        const val BOARD_TYPE = "arrdep"
        const val FRESH_TTL_MS = 60_000L
        const val STALE_MAX_MS = 30 * 60_000L
    }
}
