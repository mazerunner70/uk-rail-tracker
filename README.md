# UK Rail Tracker

A native Android app for live UK rail information — nearby stations, contextual commute dashboards, journey tracking, and Delay Repay compensation assist.

## Status

Early development. The Android app is a Hello World scaffold (v0.1.0). Station reference data (2,612 stations) is collected; live rail API integration is next.

## Documentation

- **[Product docs](docs/)** — vision, milestone roadmap, architecture, data sources, compensation guide
- **[Android build docs](android/docs/)** — toolchain setup, Gradle scaffold, emulator/phone workflow

## Quick links

| Doc | Description |
|-----|-------------|
| [Milestone roadmap](docs/milestone-roadmap.md) | M0–M6 delivery plan |
| [Product vision](docs/product-vision.md) | Goals, audience, design principles |
| [Architecture](docs/architecture.md) | App layers, Room schema, privacy |
| [Data sources](docs/data-sources.md) | TransportAPI / Darwin setup |
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

```bash
cd android
./gradlew :app:assembleDebug
./gradlew :app:installDebug
```

See [android/docs/android-native-app-plan.md](android/docs/android-native-app-plan.md) for full toolchain setup.
