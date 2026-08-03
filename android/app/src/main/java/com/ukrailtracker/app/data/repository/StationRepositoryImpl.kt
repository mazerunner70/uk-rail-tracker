package com.ukrailtracker.app.data.repository

import android.util.Log
import com.ukrailtracker.app.data.local.datastore.StationMetaStore
import com.ukrailtracker.app.data.mapper.toDomain
import com.ukrailtracker.app.data.parse.STATIONS_ASSET_VERSION
import com.ukrailtracker.app.data.parse.StationsJsonParser
import com.ukrailtracker.app.data.source.DataSource
import com.ukrailtracker.app.data.store.StationStore
import com.ukrailtracker.app.domain.geo.GeoUtils
import com.ukrailtracker.app.domain.model.Station
import com.ukrailtracker.app.domain.model.StationWithDistance
import com.ukrailtracker.app.domain.repository.StationRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class StationRepositoryImpl(
    private val stationsSource: DataSource,
    private val parser: StationsJsonParser,
    private val store: StationStore,
    private val metaStore: StationMetaStore,
) : StationRepository {

    private val importMutex = Mutex()

    override suspend fun ensureImported() {
        importMutex.withLock {
            val importedVersion = metaStore.getImportedVersion()
            val count = store.count()
            if (importedVersion == STATIONS_ASSET_VERSION && count > 0) return@withLock

            Log.i(TAG, "Importing bundled stations (version=$STATIONS_ASSET_VERSION)…")
            val payload = stationsSource.fetch()
            val batch = parser.parse(payload)
            store.replaceAll(batch)
            metaStore.setImportedVersion(batch.sourceVersion)
            Log.i(TAG, "Imported ${batch.stations.size} stations")
        }
    }

    override suspend fun count(): Int = store.count()

    override suspend fun getByCrs(crs: String): Station? =
        store.getByCrs(crs.uppercase())?.toDomain()

    override suspend fun search(query: String): List<Station> {
        if (query.isBlank()) return emptyList()
        return store.search(query.trim()).map { it.toDomain() }
    }

    override suspend fun getNearby(lat: Double, lng: Double, limit: Int): List<StationWithDistance> {
        // ~50 km box first; expand if sparse (e.g. rural / mock edge cases)
        var delta = GeoUtils.boundingBoxDegrees(50_000.0)
        var candidates = store.inBoundingBox(
            minLat = lat - delta,
            maxLat = lat + delta,
            minLng = lng - delta,
            maxLng = lng + delta,
        )
        if (candidates.size < limit) {
            delta = GeoUtils.boundingBoxDegrees(200_000.0)
            candidates = store.inBoundingBox(
                minLat = lat - delta,
                maxLat = lat + delta,
                minLng = lng - delta,
                maxLng = lng + delta,
            )
        }
        if (candidates.isEmpty()) {
            candidates = store.getAll()
        }
        return candidates
            .map { entity ->
                StationWithDistance(
                    station = entity.toDomain(),
                    distanceMetres = GeoUtils.haversineMetres(
                        lat, lng, entity.latitude, entity.longitude,
                    ),
                )
            }
            .sortedBy { it.distanceMetres }
            .take(limit)
    }

    companion object {
        private const val TAG = "UkRailTracker"
    }
}
