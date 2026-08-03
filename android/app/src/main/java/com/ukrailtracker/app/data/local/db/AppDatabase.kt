package com.ukrailtracker.app.data.local.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase

@Entity(
    tableName = "stations",
    indices = [Index(value = ["latitude", "longitude"])],
)
data class StationEntity(
    @PrimaryKey @ColumnInfo(name = "crs_code") val crsCode: String,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "latitude") val latitude: Double,
    @ColumnInfo(name = "longitude") val longitude: Double,
    @ColumnInfo(name = "operator_name") val operatorName: String,
    @ColumnInfo(name = "operator_code") val operatorCode: String,
    @ColumnInfo(name = "address_json") val addressJson: String?,
    @ColumnInfo(name = "accessibility_json") val accessibilityJson: String? = null,
    @ColumnInfo(name = "station_map_url") val stationMapUrl: String? = null,
)

@Entity(
    tableName = "departure_cache",
    indices = [Index(value = ["crs_code", "board_type"])],
)
data class DepartureCacheEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "crs_code") val crsCode: String,
    @ColumnInfo(name = "board_type") val boardType: String,
    @ColumnInfo(name = "data_json") val dataJson: String,
    @ColumnInfo(name = "fetched_at") val fetchedAt: Long,
    @ColumnInfo(name = "filter_crs") val filterCrs: String? = null,
)

@Dao
interface StationDao {
    @Query("SELECT COUNT(*) FROM stations")
    suspend fun count(): Int

    @Query("SELECT * FROM stations WHERE crs_code = :crs LIMIT 1")
    suspend fun getByCrs(crs: String): StationEntity?

    @Query(
        """
        SELECT * FROM stations
        WHERE name LIKE '%' || :query || '%' OR crs_code LIKE '%' || :query || '%'
        ORDER BY
          CASE WHEN crs_code = upper(:query) THEN 0
               WHEN crs_code LIKE upper(:query) || '%' THEN 1
               WHEN name LIKE :query || '%' THEN 2
               ELSE 3 END,
          name ASC
        LIMIT :limit
        """,
    )
    suspend fun search(query: String, limit: Int = 50): List<StationEntity>

    @Query(
        """
        SELECT * FROM stations
        WHERE latitude BETWEEN :minLat AND :maxLat
          AND longitude BETWEEN :minLng AND :maxLng
        """,
    )
    suspend fun inBoundingBox(
        minLat: Double,
        maxLat: Double,
        minLng: Double,
        maxLng: Double,
    ): List<StationEntity>

    @Query("SELECT * FROM stations")
    suspend fun getAll(): List<StationEntity>

    @Query("DELETE FROM stations")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(stations: List<StationEntity>)
}

@Dao
interface DepartureCacheDao {
    @Query(
        """
        SELECT * FROM departure_cache
        WHERE crs_code = :crs AND board_type = :boardType
        ORDER BY fetched_at DESC
        LIMIT 1
        """,
    )
    suspend fun getLatest(crs: String, boardType: String): DepartureCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: DepartureCacheEntity)

    @Query("DELETE FROM departure_cache WHERE crs_code = :crs AND board_type = :boardType")
    suspend fun deleteFor(crs: String, boardType: String)

    @Query("DELETE FROM departure_cache WHERE fetched_at < :beforeEpochMs")
    suspend fun deleteOlderThan(beforeEpochMs: Long)
}

@Database(
    entities = [StationEntity::class, DepartureCacheEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun stationDao(): StationDao
    abstract fun departureCacheDao(): DepartureCacheDao
}
