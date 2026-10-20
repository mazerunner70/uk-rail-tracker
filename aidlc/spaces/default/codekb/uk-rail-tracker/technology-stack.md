# Technology Stack — uk-rail-tracker

Versions from the prior store, with the live-sched rows replaced from the 2026-10-20 focused scan. Cross-ref dependency graph → `dependencies.md`.

## Languages and runtimes

| Tech | Version / constraint | Where |
|------|----------------------|-------|
| Kotlin | 2.2.10 | Android (prior scan) |
| Python | ≥3.12 (3.12 in kafka POC, prior scan) | `server/poc/schedule`, `server/poc/kafka`, `server/poc/live-sched` |
| Markdown / CIF | — | docs, PLAN, timetable samples |

The workflow language list has recorded TypeScript. The tree deep-scanned this run is Python 3.12 (`requires-python = ">=3.12"`).

## Android build and UI

Prior scan. Not re-opened.

| Tech | Version | Purpose |
|------|---------|---------|
| Android Gradle Plugin | 9.3.3 | Build |
| Gradle Wrapper | 9.5.0 | Build |
| KSP | 2.3.6 | Room compiler |
| Jetpack Compose BOM | 2024.10.01 | UI |
| Navigation Compose | 2.8.4 | Navigation |
| Lifecycle / ViewModel Compose | 2.8.7 | Presentation |
| Room | 2.8.4 | Local SQLite |
| DataStore Preferences | 1.1.1 | Favourites / commute prefs |
| WorkManager | 2.9.1 | Background refresh / geofence |
| OkHttp | 4.12.0 | Darwin HTTP |
| Play Services Location | 21.3.0 | GPS / geofences |
| kotlinx-coroutines | 1.9.0 | Async |

## Android test

Prior scan. Not re-opened.

| Tech | Version |
|------|---------|
| JUnit | 4.13.2 |
| Robolectric | 4.14.1 |
| kotlinx-coroutines-test | 1.9.0 |

## Python POC — live-sched (deep, this run)

| Tech | Version | Purpose |
|------|---------|---------|
| setuptools | ≥68 (`setuptools.build_meta`) | Packaging and the `live-sched` console script |
| python-dotenv | ≥1.0,<2 | Workspace-root `.env` (`override=False`) |
| confluent-kafka | ≥2.3,<3 | Darwin PubSub consumer, imported inside `iter_kafka_messages` |
| pytest | ≥8.0,<9 (optional `dev`) | Tests. `testpaths = ["tests"]`, `pythonpath = ["."]` |
| sqlite3, zipfile, csv, json, argparse, dataclasses, logging | stdlib | Persistence, CFA zip extract, Kafka CSV, CLI |

No web framework, ORM, AWS SDK, Glue client, Athena client, or S3 client. No watchdog: `cfa-update` lists the watch folder once per invocation.

## Python POC — schedule / kafka (prior)

Not re-opened this run.

| Tech | Version | Purpose |
|------|---------|---------|
| stdlib sqlite3 | stdlib | Schedule persistence |
| pytest | ≥8,<9 | Schedule tests |
| confluent-kafka | ≥2.6.0 | Kafka client (kafka POC) |
| python-dotenv | ≥1.0.0 | `.env` loading |
| fastavro | ≥1.9.0 | Avro decode attempts in the kafka POC. Not a live-sched dependency. |

## Not present in the live-sched tree

- Retrofit (product docs may still mention it; Android transport on the prior scan is OkHttp)
- First-party server API framework
- JaCoCo, Kover, coverage.py, or pytest-cov
- CI workflow, Dockerfile, Makefile, lockfile, or `requirements.txt` under `server/poc/live-sched`
- AWS SDK, Glue, Athena, or an S3 client
- A filesystem watcher library
