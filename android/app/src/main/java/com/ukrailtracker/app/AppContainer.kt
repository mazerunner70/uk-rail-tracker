package com.ukrailtracker.app

import android.app.Application
import android.content.Context
import androidx.room.Room
import com.ukrailtracker.app.data.local.datastore.AppPreferencesStore
import com.ukrailtracker.app.data.local.datastore.CommuteWindowStore
import com.ukrailtracker.app.data.local.datastore.FavouriteJourneysStore
import com.ukrailtracker.app.data.local.datastore.FavouritesStore
import com.ukrailtracker.app.data.local.datastore.PinnedJourneyStore
import com.ukrailtracker.app.data.local.datastore.RecentJourneysStore
import com.ukrailtracker.app.data.local.datastore.RecentSearchStore
import com.ukrailtracker.app.data.local.datastore.StationMetaStore
import com.ukrailtracker.app.data.local.db.AppDatabase
import com.ukrailtracker.app.data.mapper.ArrDepBoardWithDetailsParser
import com.ukrailtracker.app.data.parse.StationsJsonParser
import com.ukrailtracker.app.data.remote.darwin.OpenLdbServiceDetailsApi
import com.ukrailtracker.app.data.remote.darwin.OpenLdbWsApi
import com.ukrailtracker.app.data.repository.DepartureRepositoryImpl
import com.ukrailtracker.app.data.repository.JourneyLogRepository
import com.ukrailtracker.app.data.repository.JourneyRepositoryImpl
import com.ukrailtracker.app.data.repository.StationRepositoryImpl
import com.ukrailtracker.app.data.source.asset.AssetStationsDataSource
import com.ukrailtracker.app.data.store.StationStore
import com.ukrailtracker.app.domain.location.LocationProvider
import com.ukrailtracker.app.domain.commute.CommuteSessionController
import com.ukrailtracker.app.domain.repository.DepartureRepository
import com.ukrailtracker.app.domain.repository.JourneyRepository
import com.ukrailtracker.app.domain.repository.StationRepository
import com.ukrailtracker.app.domain.usecase.GetNearbyStationsUseCase
import com.ukrailtracker.app.debug.DebugWalkUp
import com.ukrailtracker.app.location.FusedLocationProvider
import com.ukrailtracker.app.location.geofence.StationGeofenceController
import com.ukrailtracker.app.worker.GeofenceRearmScheduler
import com.ukrailtracker.app.worker.HomeRefreshScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class UkRailTrackerApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.homeRefreshScheduler.scheduleIfEnabled()
        container.geofenceRearmScheduler.scheduleIfLocationGranted()
        container.armStationGeofencesAsync(forceLocationRefresh = false)
    }
}

class AppContainer(app: Application) {
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val database: AppDatabase = Room.databaseBuilder(
        app,
        AppDatabase::class.java,
        "uk_rail_tracker.db",
    ).fallbackToDestructiveMigration(dropAllTables = true).build()

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

    val locationProvider: LocationProvider =
        DebugWalkUp.wrapLocation(FusedLocationProvider(app))

    /** Latest resolved GPS speed (m/s) from the home location poll; null before first fix. */
    val latestSpeedMps = MutableStateFlow<Double?>(null)

    val getNearbyStations: GetNearbyStationsUseCase = GetNearbyStationsUseCase(
        locationProvider = locationProvider,
        stationRepository = stationRepository,
    )

    private val openLdbWsApi = OpenLdbWsApi(apiKey = BuildConfig.DARWIN_LDB_API_KEY)
    private val openLdbServiceDetailsApi = OpenLdbServiceDetailsApi(
        apiKey = BuildConfig.DARWIN_LDB_SERVICE_DETAILS_API_KEY,
    )

    val departureRepository: DepartureRepository = DepartureRepositoryImpl(
        api = openLdbWsApi,
        cacheDao = database.departureCacheDao(),
        parser = ArrDepBoardWithDetailsParser(),
    )

    val journeyRepository: JourneyRepository = JourneyRepositoryImpl(
        departureRepository = departureRepository,
        serviceDetailsApi = openLdbServiceDetailsApi,
    )

    val journeyLogRepository = JourneyLogRepository(database.journeyLogDao())

    val recentSearchStore = RecentSearchStore(app)
    val favouritesStore = FavouritesStore(app)
    val favouriteJourneysStore = FavouriteJourneysStore(app)
    val recentJourneysStore = RecentJourneysStore(app)
    val pinnedJourneyStore = PinnedJourneyStore(app)
    val commuteWindowStore = CommuteWindowStore(app)
    val appPreferencesStore = AppPreferencesStore(app)
    val homeRefreshScheduler = HomeRefreshScheduler(app)
    val geofenceRearmScheduler = GeofenceRearmScheduler(app)
    val stationGeofenceController = StationGeofenceController(
        appContext = app,
        locationProvider = locationProvider,
        stationRepository = stationRepository,
        favouritesStore = favouritesStore,
        commuteWindowStore = commuteWindowStore,
    )

    val commuteSessionController = CommuteSessionController(
        appContext = app,
        scope = ioScope,
        stationRepository = stationRepository,
        journeyRepository = journeyRepository,
        locationProvider = locationProvider,
        favouritesStore = favouritesStore,
        pinnedJourneyStore = pinnedJourneyStore,
        journeyLogRepository = journeyLogRepository,
        onLatestSpeedMps = { latestSpeedMps.value = it },
    )

    fun armStationGeofencesAsync(forceLocationRefresh: Boolean = true) {
        geofenceRearmScheduler.scheduleNow()
        ioScope.launch {
            stationGeofenceController.armFromCurrentLocation(forceLocationRefresh)
        }
    }
}

fun Context.appContainer(): AppContainer =
    (applicationContext as UkRailTrackerApp).container
