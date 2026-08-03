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
        ORDER BY name ASC
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

@Database(entities = [StationEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun stationDao(): StationDao
}
