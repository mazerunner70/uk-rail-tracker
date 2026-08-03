package com.ukrailtracker.app

import android.app.Application
import android.content.Context
import androidx.room.Room
import com.ukrailtracker.app.data.local.datastore.StationMetaStore
import com.ukrailtracker.app.data.local.db.AppDatabase
import com.ukrailtracker.app.data.parse.StationsJsonParser
import com.ukrailtracker.app.data.repository.StationRepositoryImpl
import com.ukrailtracker.app.data.source.asset.AssetStationsDataSource
import com.ukrailtracker.app.data.store.StationStore
import com.ukrailtracker.app.domain.location.LocationProvider
import com.ukrailtracker.app.domain.repository.StationRepository
import com.ukrailtracker.app.domain.usecase.GetNearbyStationsUseCase
import com.ukrailtracker.app.location.FusedLocationProvider

class UkRailTrackerApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

class AppContainer(app: Application) {
    private val database: AppDatabase = Room.databaseBuilder(
        app,
        AppDatabase::class.java,
        "uk_rail_tracker.db",
    ).build()

    private val stationStore = StationStore(database.stationDao())
    private val metaStore = StationMetaStore(app)
    private val stationsSource = AssetStationsDataSource(app)
    private val parser = StationsJsonParser()

    val stationRepository: StationRepository = StationRepositoryImpl(
        stationsSource = stationsSource,
        parser = parser,
        store = stationStore,
        metaStore = metaStore,
    )

    val locationProvider: LocationProvider = FusedLocationProvider(app)

    val getNearbyStations: GetNearbyStationsUseCase = GetNearbyStationsUseCase(
        locationProvider = locationProvider,
        stationRepository = stationRepository,
    )
}

fun Context.appContainer(): AppContainer =
    (applicationContext as UkRailTrackerApp).container
