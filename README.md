# UK Rail Tracker

A native Android app for live UK rail information — nearby stations, contextual commute dashboards, journey tracking, and Delay Repay compensation assist.

## Status

**M6 done:** walk-up → boarding confirm → on-train progress (countdown, calling-point diagram, station-stop ETA refresh, route-mismatch warning, 3/2/1 min arrival buzz, foreground tracking notification). Next: M7 compensation inbox from `journey_log`.

## Documentation

- **[Product docs](docs/)** — vision, milestone roadmap, architecture, data sources, compensation guide
- **[Android build docs](android/docs/)** — toolchain setup, Gradle scaffold, emulator/phone workflow

## Quick links

| Doc | Description |
|-----|-------------|
| [Milestone roadmap](docs/milestone-roadmap.md) | M0–M9 delivery plan |
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
DARWIN_LDB_API_KEY=your_arrdep_consumer_key
DARWIN_LDB_SERVICE_DETAILS_API_KEY=your_service_details_consumer_key
```

Live boards call:

`GET https://api1.raildata.org.uk/1010-live-arrival-and-departure-boards-arr-and-dep1_1/LDBWS/api/20220120/GetArrDepBoardWithDetails/{CRS}`

On-train / station-stop confirmations use the selected Darwin `serviceID`:

`GET https://api1.raildata.org.uk/1010-service-details1_2/LDBWS/api/20220120/GetServiceDetails/{serviceID}`

Each request sends `x-apikey` for the matching RDM product key.

Then:

```bash
cd android
./gradlew :app:assembleDebug
./gradlew :app:installDebug

# Opt-in APS walk-up GPS simulator (debug tooling):
./gradlew :app:installDebug -PincludeSimulator=true
# or: ./scripts/deploy-emulator.sh --simulator
```

See [android/docs/android-native-app-plan.md](android/docs/android-native-app-plan.md) for full toolchain setup.
