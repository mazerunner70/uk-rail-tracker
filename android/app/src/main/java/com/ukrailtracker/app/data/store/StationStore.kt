package com.ukrailtracker.app.data.store

import com.ukrailtracker.app.data.local.db.StationDao
import com.ukrailtracker.app.data.local.db.StationEntity
import com.ukrailtracker.app.data.parse.StationWriteBatch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class StationStore(
    private val dao: StationDao,
) {
    suspend fun count(): Int = dao.count()

    suspend fun getByCrs(crs: String): StationEntity? = dao.getByCrs(crs)

    suspend fun search(query: String, limit: Int = 50): List<StationEntity> =
        dao.search(query, limit)

    suspend fun inBoundingBox(
        minLat: Double,
        maxLat: Double,
        minLng: Double,
        maxLng: Double,
    ): List<StationEntity> = dao.inBoundingBox(minLat, maxLat, minLng, maxLng)

    suspend fun getAll(): List<StationEntity> = dao.getAll()

    suspend fun replaceAll(batch: StationWriteBatch) = withContext(Dispatchers.IO) {
        dao.deleteAll()
        // Insert in chunks to avoid binder / SQLite limits
        batch.stations.chunked(500).forEach { chunk ->
            dao.insertAll(chunk)
        }
    }
}
