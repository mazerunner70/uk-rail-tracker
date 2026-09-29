# Architecture

Technical architecture for the UK Rail Tracker Android app.

## Layer overview

```mermaid
flowchart TB
    subgraph presentation [Presentation Layer]
        Screens[Compose Screens]
        VM[ViewModels]
        Nav[Navigation Compose]
    end
    subgraph domain [Domain Layer]
        UseCases[Use Cases]
        Models[Domain Models]
    end
    subgraph data [Data Layer]
        Repo[Repositories]
        Local[Room DB + DataStore]
        Remote[API Clients]
        Assets[Bundled Assets]
    end
    Screens --> VM
    VM --> UseCases
    UseCases --> Repo
    Repo --> Local
    Repo --> Remote
    Repo --> Assets
```

## Package structure

```text
com.ukrailtracker.app/
├── ui/
│   ├── theme/              # existing neon theme
│   ├── navigation/         # NavHost, bottom bar, routes
│   ├── home/               # M2 contextual home
│   ├── nearby/             # M1 nearby stations
│   ├── station/            # M1 station detail + departures
│   ├── journey/            # M3 journey planner + tracking
│   ├── compensation/       # M7 disruption inbox
│   └── settings/           # commute windows, favourites
├── domain/
│   ├── model/              # Station, Departure, Journey, Disruption
│   ├── repository/         # Repository interfaces (no Android imports)
│   └── usecase/            # GetNearbyStations, GetDepartures, etc.
├── data/
│   ├── local/
│   │   ├── db/             # Room database, DAOs, entities
│   │   └── datastore/      # Preferences, favourites, commute windows
│   ├── remote/
│   │   ├── darwin/         # OpenLDBWS (GetArrDepBoardWithDetails), KB, RTJP, HSP
│   │   └── dto/            # Feed-specific response models (never in ui/)
│   ├── repository/         # *RepositoryImpl — Darwin primary, TransportAPI fallback
│   └── mapper/             # DTO/XML → domain model conversions
├── location/               # LocationProvider wrapper
├── debug/                  # DebugWalkUp facade (wired via Gradle source sets)
└── worker/                 # WorkManager background refresh
```

Walk-up / on-train GPS simulation is **opt-in** via `-PincludeSimulator=true` or `INCLUDE_SIMULATOR=1` (also `--simulator` on `android/scripts/deploy-*.sh`). That selects `src/simulator/` (`WalkUpLocationSimulator` + real `DebugWalkUp`); otherwise `src/noSimulator/` provides a no-op facade and simulator classes are not compiled in.

The simulator wraps **only** `LocationProvider`. Departure boards and `GetServiceDetails` stay on live Darwin so Home Refresh is accurate. Walk-up interpolates 300 m → APS over ~2 minutes; after a debug pin, GPS follows calling points on **real timetable gaps** (plus dwells). Full operator steps, dual clocks, and deferred time-scale work: [simulated-journey.md](simulated-journey.md).

## Key dependencies (M0)

| Library | Purpose |
|---------|---------|
| `androidx.navigation:navigation-compose` | Multi-screen navigation |
| `androidx.lifecycle:lifecycle-viewmodel-compose` | ViewModels |
| `org.jetbrains.kotlinx:kotlinx-coroutines-android` | Async operations |
| `androidx.room:room-runtime` + `room-ktx` | Local SQLite database |
| `androidx.datastore:datastore-preferences` | User preferences, favourites |
| `com.squareup.retrofit2:retrofit` + Gson/Moshi converter | HTTP API client |
| `com.google.android.gms:play-services-location` | GPS location |
| `androidx.work:work-runtime-ktx` | Background refresh |

---

Live inventory of domain types vs Room / DataStore / in-memory session: [data-model.md](data-model.md).

## Room database schema

### `stations` table

Imported from bundled `stations.xml` on first launch.

| Column | Type | Notes |
|--------|------|-------|
| `crs_code` | TEXT PK | e.g. `"PAD"` |
| `name` | TEXT | Display name |
| `latitude` | REAL | For distance calc |
| `longitude` | REAL | For distance calc |
| `operator_name` | TEXT | e.g. `"Great Western Railway"` |
| `operator_code` | TEXT | e.g. `"GW"` |
| `address_json` | TEXT | Serialised address object |
| `accessibility_json` | TEXT | Serialised accessibility data |
| `station_map_url` | TEXT | Nullable |
| `min_connection_time` | INTEGER | Minutes |

Index: `(latitude, longitude)` for bounding-box nearby queries.

### `departure_cache` table

Short-lived cache for offline display.

| Column | Type | Notes |
|--------|------|-------|
| `id` | INTEGER PK AUTO | |
| `crs_code` | TEXT | Station CRS |
| `board_type` | TEXT | `"arrdep"` (primary), or `"departure"` / `"arrival"` if split |
| `data_json` | TEXT | Serialised board (from `GetArrDepBoardWithDetails` parse) |
| `fetched_at` | INTEGER | Epoch millis |
| `filter_crs` | TEXT | Nullable; mirrors OpenLDBWS `filterCrs` when used |

TTL: 5 minutes while online; serve stale up to 30 minutes when offline.

### `journey_log` table

Written by M3/M6, read by M7.

| Column | Type | Notes |
|--------|------|-------|
| `id` | INTEGER PK AUTO | |
| `date` | TEXT | ISO date |
| `origin_crs` | TEXT | |
| `destination_crs` | TEXT | |
| `operator_code` | TEXT | For Delay Repay rules |
| `scheduled_departure` | TEXT | ISO datetime |
| `scheduled_arrival` | TEXT | ISO datetime |
| `actual_arrival` | TEXT | ISO datetime, nullable |
| `delay_minutes` | INTEGER | Calculated |
| `was_cancelled` | BOOLEAN | |
| `service_id` | TEXT | API service identifier |
| `claim_status` | TEXT | `"eligible"`, `"claimed"`, `"dismissed"`, `"not_eligible"` |

---

## DataStore preferences

| Key | Type | Purpose |
|-----|------|---------|
| `favourite_stations` | StringSet | CRS codes |
| `favourite_journeys` | String | JSON array of `{origin, destination}` pairs |
| `commute_windows` | String | JSON array of `{days, startTime, endTime, stationCrs}` |
| `stations_db_version` | Int | Trigger re-import when asset changes |
| `notification_enabled` | Boolean | M8 |
| `background_refresh_interval` | Int | Minutes (default 15) |

---

## Repository pattern

Repository **interfaces** live in `domain/repository/`; **implementations** in `data/repository/`. ViewModels and use cases depend on interfaces only — never on Darwin DTOs or XML parsers.

Darwin is the primary live feed ([data-sources.md §1](data-sources.md#1-overview--sources-features-and-layering)). Train movement uses OpenLDBWS **`GetArrDepBoardWithDetails`**. TransportAPI can implement the same repository interfaces as a dev/fallback. UI rework touches `ui/` and ViewModel `UiState` mapping only.

```kotlin
// domain/repository/ — illustrative
interface StationRepository {
    suspend fun getAllStations(): List<Station>
    suspend fun getStationByCrs(crs: String): Station?
    suspend fun getNearbyStations(lat: Double, lng: Double, limit: Int): List<StationWithDistance>
    suspend fun searchStations(query: String): List<Station>
}

interface DepartureRepository {
    suspend fun getDepartures(crs: String, forceRefresh: Boolean = false): DepartureBoard
    fun observeDepartures(crs: String): Flow<DepartureBoard>
}
```

### Caching strategy

| Data | Cache location | TTL | Offline fallback |
|------|---------------|-----|------------------|
| Station metadata | Room | Permanent (re-import on asset update) | Always available |
| Departure boards | Room `departure_cache` | 60 s (foreground), 15 min (background) | Serve stale up to 30 min |
| Disruptions | In-memory + DataStore | 5 min | Show last-known with staleness badge |
| Journey options | None (always fresh) | — | Show error |
| Journey log | Room | Permanent | Always available |

---

## Location handling

```mermaid
flowchart TD
    Start[Screen requests location] --> Perm{Permission granted?}
    Perm -->|Yes| GPS[Get last known + request update]
    Perm -->|No| Manual[Show search-only UI with prompt]
    GPS --> Sort[Sort stations by haversine distance]
    Sort --> Display[Show top N nearby]
    Manual --> Search[User searches manually]
```

- Use Fused Location Provider (Play Services) for battery efficiency
- Request `ACCESS_COARSE_LOCATION` minimum; `ACCESS_FINE_LOCATION` for better accuracy
- Optional `ACCESS_BACKGROUND_LOCATION` for idle OS geofence ENTER while the app is closed
- Never send location to a server — all processing on-device
- Show permission rationale dialog before system prompt

### Station geofence wake (hybrid)

While idle, WorkManager re-arms a moving window of OS geofences every 15 minutes (nearest 5 + favourites/commute, 300 m radius). ENTER posts a notification (and vibrates); tapping opens Home and elevates the in-app GPS poll so `DetectWalkUpArrival` can confirm presence. Soft geofence / walk / dwell / boarding logic remains in-app — OS ENTER is a hint only.

### Distance calculation

Haversine formula on station lat/lng vs user lat/lng. No need for a spatial DB extension at 2,612 stations — linear scan with bounding-box pre-filter is fast enough.

### On-train session (M6)

While a journey is pinned after boarding confirm (or manual pin):

- Home enters `WalkUpHomeUi.OnJourney` (progress diagram + countdown); walk-up detection is suppressed
- `DetectStationStop` confirms calling-point stations via low speed + proximity; dwell triggers per-minute board refresh
- `OnTrainTrackingService` (foreground, `location` type) keeps an ongoing notification with ETA
- Arrival at the favourite CRS (or End trip) clears the pin, stops the service, and writes `journey_log`

### Commute session design (recommendation)

The M4–M6 pipeline is several **orthogonal** machines (walk-up, boarding, pin, station-stop), not one enum. Keep it that way.

**Implemented as:** events in, priority arbitration, pure state reduction, effects as data.

```text
Event  ──►  SessionEngine (Priority Arbitration)  ──►  SessionReducer.reduce(session, event, context)
                                                                      │
                                                                      ▼
                                                       Transition(newSession, effects)
                                                                      │
                                                                      ▼
                                                       CommuteRuntime runs effects
                                                                      │
                                                                      ▼
                                                       CommuteUiMapper → WalkUpHomeUi
```

`SessionReducer` and modular handlers (`IdleStateHandler`, `AtStationStateHandler`, etc.) are pure functions with no Android types. All contextual inputs pass in via `SessionReduceContext`.

Classify every action before writing it:

| Kind | Example | Where it lives |
|------|---------|----------------|
| Pure transition | Away → Dwelling after a GPS sample | State handlers / `SessionReducer` |
| Effect | Start FGS, pin DataStore, vibrate, `GetServiceDetails` | `CommuteRuntime.interpretEffect` |
| UI projection | Status bar copy, progress diagram, `refreshing` | `CommuteUiMapper` (`session` → `WalkUpHomeUi`) |

If `DetectBoarding` starts a service, the domain is no longer testable. If Darwin latency is a sibling of `Away`, presence and networking are confused — `refreshing` / `error` are load state on the on-train *view*, not commute phases.

**One writer.** GPS, Darwin, notification taps, and buttons all dispatch events to `SessionEngine`. Receivers and Workers **enqueue events** (`OsGeofenceEnter`, `OpenedForFrequentChecks`); they do not set presence directly.

**Time is an input.** Dwells, 8 s boarding, 60 s auto-accept, and 3/2/1 min buzz are evaluated against timestamps in `SessionReduceContext` and `sample.epochMs` or driven by state-scoped `Tick` timeouts.

**Persist the trip, not the sensors.** Durable: `PinnedJourney`, `journey_log`. Ephemeral: walk-up / dwelling — GPS will say them again after process death.

**Priority tiers eliminate races.** User commands (`Dismiss`, `Confirm`, `EndTrip`) have `USER_INTENT` priority and flush with 0ms latency, invalidating ambient sensor noise.

Engine details: [commute-state-machine.md](commute-state-machine.md). Phases and thresholds: [state-model.md](state-model.md). ADR: [android/docs/session-state-engine-architecture.md](../android/docs/session-state-engine-architecture.md).

---

## Background refresh (M2+)

```mermaid
sequenceDiagram
    participant WM as WorkManager
    participant Repo as DepartureRepository
    participant API as OpenLDBWS
    participant DB as Room Cache
    participant Notif as NotificationManager

    WM->>Repo: Refresh favourite stations
    Repo->>API: GetArrDepBoardWithDetails
    API-->>Repo: StationBoardWithDetails
    Repo->>DB: Update cache
    Repo->>Repo: Check for delays on pinned journeys
  alt Significant delay detected
        Repo->>Notif: Show notification
    end
```

- `PeriodicWorkRequest` every 15 minutes
- Constraints: `NetworkType.CONNECTED`
- Respect battery saver: skip if `PowerManager.isPowerSaveMode`
- User can disable in Settings

---

## Navigation structure

```mermaid
flowchart TB
    subgraph bottomNav [Bottom Navigation]
        Home[Home]
        Nearby[Nearby]
        Journeys[Journeys]
        Settings[Settings]
    end
    Nearby --> StationDetail[Station Detail]
    Home --> StationDetail
    Journeys --> JourneyPlanner[Journey Planner]
    JourneyPlanner --> JourneyLive[Journey Live View]
    Journeys --> CompInbox[Compensation Inbox]
    Settings --> CommuteSetup[Commute Windows]
    Settings --> Favourites[Favourites]
```

---

## Error handling

| Scenario | UI response |
|----------|-------------|
| No network | Show cached data with "Last updated X min ago" banner |
| API rate limit (429) | Show cached data; retry with exponential backoff |
| API error (5xx) | Error card with retry button |
| Empty departure board | "No departures scheduled" empty state |
| Location denied | Search-only mode with permission prompt in Settings |
| Station not found | "Station not found" with search suggestion |

---

## Privacy

| Data | Stored where | Sent to server? | Retention |
|------|-------------|-----------------|-----------|
| GPS location | In-memory only | No | Discarded after nearby sort |
| Favourites / commute windows | DataStore (on-device) | No | Until user deletes |
| Journey log | Room (on-device) | No | 56 days (configurable) |
| Departure cache | Room (on-device) | No | 30 min TTL |
| API keys | BuildConfig | Yes (in API request headers) | N/A |

No user accounts in v1. No analytics SDK in v1 (add later with consent).

---

## Testing strategy

| Layer | Approach | Milestone |
|-------|----------|-----------|
| Repositories | Unit tests with fake DAO + mock API | M0 |
| Use cases | Unit tests with fake repositories | M1 |
| ViewModels | Unit tests with fake use cases + coroutine test rule | M1 |
| UI | Compose UI tests for critical flows | M8 |
| Integration | MockWebServer for API client tests | M0 |

Critical test flows:

1. Nearby sort returns closest station given fixed GPS coordinates
2. Departure board maps API response to domain model correctly
3. Journey log records delay and flags as claimable per operator rules
4. Offline mode serves cached departures with staleness indicator
