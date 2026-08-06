# UK Rail Tracker

A native Android app for live UK rail information — nearby stations, contextual commute dashboards, journey tracking, and Delay Repay compensation assist.

## Status

**M3 in progress:** journey planner (direct services via LDB `filterCrs`), live journey detail with calling points, “My train” pin with 45s polling, favourite/recent routes, and `journey_log` writes for M4.

## Documentation

- **[Product docs](docs/)** — vision, milestone roadmap, architecture, data sources, compensation guide
- **[Android build docs](android/docs/)** — toolchain setup, Gradle scaffold, emulator/phone workflow

## Quick links

| Doc | Description |
|-----|-------------|
| [Milestone roadmap](docs/milestone-roadmap.md) | M0–M6 delivery plan |
| [M0+M1 nearby plan](docs/m0-m1-nearby-plan.md) | Nearby slice + M1 remainder |
| [Product vision](docs/product-vision.md) | Goals, audience, design principles |
| [Architecture](docs/architecture.md) | App layers, Room schema, privacy |
| [Data sources](docs/data-sources.md) | Darwin / TransportAPI setup |
| [Compensation guide](docs/compensation-guide.md) | Delay Repay rules by operator |

## Project structure

```text
uk-rail-tracker/
├── docs/           # product documentation
├── android/        # Kotlin + Compose Android app
├── stations.xml    # National Rail station directory (JSON, 2,612 stations)
└── README.md
```

## Build the Android app

Add your Rail Data Marketplace **consumer key** to `android/local.properties` (gitignored):

```properties
sdk.dir=/path/to/Android/Sdk
DARWIN_LDB_API_KEY=your_consumer_key
```

Live boards call:

`GET https://api1.raildata.org.uk/1010-live-arrival-and-departure-boards-arr-and-dep1_1/LDBWS/api/20220120/GetArrDepBoardWithDetails/{CRS}`

with header `x-apikey: <consumer key>`.

Then:

```bash
cd android
./gradlew :app:assembleDebug
./gradlew :app:installDebug
```

See [android/docs/android-native-app-plan.md](android/docs/android-native-app-plan.md) for full toolchain setup.
