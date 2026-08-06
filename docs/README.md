# UK Rail Tracker — Documentation

Product and engineering documentation for the UK Rail Tracker Android app.

## Product docs

| Document | Description |
|----------|-------------|
| [Product vision](product-vision.md) | Goals, audience, design principles, and future backlog |
| [Milestone roadmap](milestone-roadmap.md) | Phased delivery plan (M0–M9) with acceptance criteria |
| [M0 + M1 Nearby plan](m0-m1-nearby-plan.md) | Stations + GPS closest-station slice; emulator & phone deploy |
| [Data sources](data-sources.md) | Live rail APIs, credentials, and endpoint mapping |
| [Architecture](architecture.md) | App layers, local storage, offline strategy, privacy |
| [Compensation guide](compensation-guide.md) | UK Delay Repay rules by operator (M7 support) |

## Android build docs

Toolchain setup, Gradle scaffold, and emulator/phone workflows live under [android/docs/](../android/docs/):

- [Android native app plan](../android/docs/android-native-app-plan.md) — runbook
- [Android native app implementation](../android/docs/android-native-app-impl.md) — Hello World spec

## Project layout

```text
uk-rail-tracker/
├── docs/                  # product docs (this directory)
├── android/               # Kotlin + Compose Android app
├── stations.xml           # static National Rail station directory (JSON)
└── README.md
```

## Current status

- **Android app**: v0.6.0 — Nearby, Home (M4 walk-up + favourite destinations), Journeys (M3 v1), Settings
- **Station data**: 2,612 stations bundled at repo root (`stations.xml`)
- **Live rail APIs**: OpenLDBWS `GetArrDepBoardWithDetails` only
- **Next milestone**: [M5 — Boarding detection + service confirmation](milestone-roadmap.md#m5--boarding-detection--service-confirmation)
