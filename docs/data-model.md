# Data model

What UK Rail Tracker knows, where it lives, and what survives a process kill.

This is the **inventory** of types and stores. How commute phases change over time is in [state-model.md](state-model.md). How live Darwin feeds are fetched is in [data-sources.md](data-sources.md). Package layout is in [architecture.md](architecture.md).

Stations are identified by **CRS** (three-letter National Rail codes, always stored uppercase).

```text
Live Darwin (network)     GPS / Play Services
        │                         │
        ▼                         ▼
 Domain models  ◄──── Room / DataStore / prefs
        │
        ▼
 ViewModel UiState (ephemeral)
        │
        ▼
 Compose screens
```

---

## Layers

| Layer | Package | Survives process death? |
|-------|---------|-------------------------|
| Domain | `domain/model/` | Only if a store also holds a copy |
| Room | `uk_rail_tracker.db` | Yes |
| DataStore Preferences | several named files | Yes |
| SharedPreferences | geofence notify cooldown | Yes |
| Play Services geofences | device OS | Until re-arm replaces them |
| ViewModel / `reduce` session | `HomeViewModel`, `CommuteSession` | No (except restored from pin) |

UI never reads Room or Darwin DTOs. Screens consume ViewModel state built from **domain** types.

---

## Domain types (available throughout the app)

These are the Kotlin types repositories, use cases, and ViewModels share. They are **not** a database schema. Persistence is called out per type.

### Stations and location

| Type | Fields (summary) | Persisted? |
|------|------------------|------------|
| `Station` | CRS, name, lat/lng, operator name/code, address lines, postcode, accessibility, map URL | Yes — Room `stations` |
| `StationAccessibility` | Step-free category, ramp, tactile paving, wheelchairs, accessible toilets | Nested in `stations` JSON columns |
| `StationWithDistance` | `Station` + metres | No — computed for Nearby / walk-up |
| `UserLocation` | lat/lng, accuracy, speed m/s, epoch | No — last GPS sample in memory (OS may keep last-known for fused location) |

`Station` is the catalogue for Nearby, Search, station detail, geofence centres, and mapping Darwin CRS → coordinates on a train.

### Boards and journeys

| Type | Role | Persisted? |
|------|------|------------|
| `Departure` | One row on a combined arr/dep board | Inside Room `departure_cache` JSON |
| `DepartureBoard` | Station board + fetch timestamps + `fromCache` | Same cache |
| `TrainStatus` | OnTime / Delayed / Cancelled / Departed / Unknown | Derived, stored with the board JSON |
| `CallingPoint` | Intermediate stop on a service (scheduled / expected / actual labels) | In board JSON; refreshed live via `GetServiceDetails` on-train |
| `JourneyOption` | Planner / pin candidate: origin→dest, times, platform, operator, calling points, delay, cancelled | No as a row. Snapshot fields are copied into `PinnedJourney` and later `JourneyLogEntry` |
| `JourneyPair` | Origin CRS + destination CRS | Yes — favourite journeys + recent journeys DataStores |
| `PinnedJourney` | Active on-train identity (`serviceId`, CRS pair, names, operator, scheduled dep, `pinnedAtEpochMs`) | Yes — DataStore `pinned_journey` |
| `JourneyLogEntry` | Finished-trip snapshot for Delay Repay (M7) | Yes — Room `journey_log` |

`JourneyOption` is the richest in-memory view of a service. The pin stores only enough to re-attach after kill; calling points and ETAs are fetched again.

### User configuration

| Type | Role | Persisted? |
|------|------|------------|
| Favourite station CRS list | Home / geofence “always on” stations | DataStore `favourites` |
| `CommuteWindow` | Days + start/end minutes + station CRS + label | DataStore `commute_windows` |
| Recent search CRS list | Search screen (max 8) | DataStore `search_meta` |

### Disruption

| Type | Role | Persisted? |
|------|------|------------|
| `DisruptionSummary` | Green / amber / red headline derived from a board | No — computed from `DepartureBoard` (`DeriveBoardDisruption`) |

There is no incidents table or disruption DataStore yet.

### Commute session (presence)

These types describe **this commute**, not the station catalogue. See [state-model.md](state-model.md) for transitions.

| Type | Role | Persisted? |
|------|------|------------|
| `CommuteSession` | `Idle` / `Near` / `AtStation` / `Confirming` / `OnTrain` | No |
| `LockedOrigin` | Locked CRS, name, lat/lng, lock time, distance, manual override | No |
| `WalkUpPhase` | Detector internal: Away / Dwelling / Arrived | No |
| `BoardingPhase` | Idle / Sustaining / Triggered (nested on `AtStation`) | No |
| `BoardingServiceSnapshot` | Cached favourite-bound services for match-at-board | No |
| `StationStopPhase` | Moving / Dwelling / Confirmed on the route | No |
| `WalkUpHomeUi` | Home projection: permission, near, at, boarding prompt, on-journey | No |
| `CommuteEvent` / `CommuteEffect` | Inputs and side-effect intents for `reduce` | No |

**Durable commute fact:** only `PinnedJourney`. After a kill, Home restores **OnJourney** from the pin; walk-up dwell timers and boarding prompts start over from GPS.

---

## What is persisted

### Room — `uk_rail_tracker.db` (version 3)

Destructive migration on schema change (`fallbackToDestructiveMigration`).

#### `stations`

Imported from bundled `stations.xml` → JSON parser on first launch. Re-imported when `stations_db_version` in DataStore is behind the asset version.

| Column | Meaning |
|--------|---------|
| `crs_code` | Primary key |
| `name` | Display name |
| `latitude` / `longitude` | Nearby bounding box + geofence centres |
| `operator_name` / `operator_code` | TOC |
| `address_json` | Serialised address |
| `accessibility_json` | Serialised accessibility |
| `station_map_url` | Optional |

Index on `(latitude, longitude)`. Offline-always: Nearby and Search work without network.

#### `departure_cache`

Last successful `GetArrDepBoardWithDetails` parse, keyed by CRS + board type (`arrdep`) + optional `filter_crs`.

| Column | Meaning |
|--------|---------|
| `data_json` | Encoded `DepartureBoard` |
| `fetched_at` | Epoch millis |

TTL in code: treat as **fresh for 60 s**; if the live call fails, **serve stale up to 30 min**. Older rows are deleted on write. Journey planner options are **not** a separate table; they are mapped from this board (or a live fetch) in memory.

#### `journey_log`

One row per finished tracked trip (`JourneyLogRepository.logFromOption` on GPS/Darwin arrival or End trip).

| Column | Meaning |
|--------|---------|
| `date` | ISO local date |
| `origin_crs` / `destination_crs` | CRS pair |
| `operator_name` / `operator_code` | TOC (`operator_code` is currently unused on write) |
| `scheduled_departure` / `scheduled_arrival` | Time labels from the option |
| `actual_arrival` | Stores the option’s **expected** arrival label at log time (column name is historical) |
| `delay_minutes` | From Darwin at log time |
| `was_cancelled` | Flag |
| `service_id` | Darwin service id |
| `claim_status` | `eligible` if cancelled or delay ≥ 15 min, else `not_eligible`. `claimed` / `dismissed` are for M7 |

No 56-day purge is implemented yet (noted as future retention in architecture).

### DataStore (on-device Preferences)

Each file is independent.

| Store file | Key | Contents | Lifetime |
|------------|-----|----------|----------|
| `station_meta` | `stations_db_version` | Int; gates catalogue re-import | Until next asset bump |
| `favourites` | `favourite_stations` | Set of CRS | Until user removes |
| `favourite_journeys` | `favourite_journeys` | JSON array `{origin, destination}` | Until user removes |
| `commute_windows` | `commute_windows` | JSON array of windows (id, CRS, days, start/end minutes, label) | Until user deletes |
| `recent_journeys` | `recent_journeys` | JSON pairs, max 8 | Recency list |
| `search_meta` | `recent_station_searches` | JSON CRS array, max 8 | Recency list |
| `pinned_journey` | `pinned_journey` | JSON `PinnedJourney` or absent | Until arrival / End trip / dismiss / debug teardown |
| `app_prefs` | `background_refresh_enabled`, `background_refresh_interval` | Default on, interval 15 min | User / default |

`notification_enabled` from the architecture sketch is **not** implemented.

### SharedPreferences

`GeofenceProximityNotifier` stores per-CRS last notify time so ENTER does not spam for **30 minutes**.

### Outside the app process

| Data | Where | Notes |
|------|--------|--------|
| Armed station fences | Google Play Services | Nearest 5 + favourites + commute-window CRS, max 20, 300 m. Re-registered every 15 min and on boot |
| On-train foreground notification | `OnTrainTrackingService` | Lives with the FGS; pin in DataStore is what Home uses to re-attach UI |

---

## What is not persisted (in-memory / session)

Lost when the process dies unless noted.

| Data | Holder | After kill |
|------|--------|------------|
| Walk-up / `CommuteSession` presence | `reduce` + `HomeViewModel` | Recompute from GPS (`alreadyPresentOk` can lock immediately if already in the footprint) |
| Boarding phase, auto-accept timer, service snapshots | `reduce` + `HomeViewModel` (`Confirming` / cached snapshots) | Prompt does not resume; user is Away until GPS re-locks |
| Platform watch (selected service, vibration once) | `HomeViewModel` | Lost |
| Station-stop phase, confirmed stop CRS, arrival buzz flags | `reduce` (`OnTrain`) | On-train **session** resumes from pin; stop confirm and 3/2/1 buzz start fresh |
| Darwin `GetServiceDetails` calling points / ETAs | Memory after fetch | Re-fetched when on-train starts |
| `JourneyProgress` diagram / countdown | Computed | Recomputed from details + GPS |
| Current GPS sample | Location provider / VM | New fused fix |
| `DisruptionSummary` | Derived from board | Recalculated |
| Frequent-checks 5 min TTL | `ElevateFrequentChecks` → `HomeViewModel` | Lost (OS fences still armed) |
| Home / Nearby / Journeys `UiState` | ViewModels | Recreated on next open |

GPS coordinates are **not** written to Room or DataStore.

---

## What each screen needs

| Surface | Reads |
|---------|--------|
| Nearby | Room `stations` + live GPS |
| Search / station detail | Room `stations`; recents DataStore; boards from cache/network |
| Home (usual) | Favourites, commute windows, recent/favourite journey pairs, boards |
| Home at-station | Locked origin (memory) + favourite-bound boards (cache/network) |
| Home boarding | Snapshots in memory → pin write |
| Home on-train | Pin (DataStore) + service details (network) + GPS + Room CRS→lat/lng |
| Journeys / planner | Boards + `JourneyPair` recents/favourites |
| Settings | Favourites, commute windows, background refresh prefs |
| Compensation (M7) | `journey_log` |

---

## Restoration rules

| Situation | What comes back |
|-----------|-----------------|
| Cold start, no pin | Catalogue + user prefs. Presence = Idle until GPS. |
| Cold start, pin present | On-train UI + FGS path; walk-up ignored while pinned. |
| Offline | Stations and last boards (≤ 30 min). Planner/on-train details need network. |
| Location revoked | Geofence work cancelled; Home `NeedsPermission`. User prefs unchanged. |

---

## Related code

| Concern | Location |
|---------|----------|
| Room entities / DAOs | `data/local/db/AppDatabase.kt` |
| DataStores | `data/local/datastore/` |
| Domain models | `domain/model/` |
| Journey log writes | `data/repository/JourneyLogRepository.kt` |
| Board cache TTL | `DepartureRepositoryImpl` (`FRESH_TTL_MS`, `STALE_MAX_MS`) |
| Pin encode/decode | `PinnedJourneyStore` |
| Presence session | `CommuteSession.kt`, `domain/session/SessionReducer.kt`, `SessionEngine.kt` |

Related: [state-model.md](state-model.md) (commute FSM + persistence table), [architecture.md](architecture.md) (layers, privacy), [data-sources.md](data-sources.md) (Darwin vs bundled stations).
