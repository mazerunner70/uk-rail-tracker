# Component Inventory — uk-rail-tracker

Product runtime and design components. AIDLC/Cursor harness and `.venv` trees excluded.

The local timetable server and UI in `docs/future-intents/local-timetable.md` are not components. They do not exist. The inventory below is the system that already fills the SQLite file they would read.

## Android Application

- **Path**: `android/` (`:app`)
- **Type**: Mobile application
- **Language**: Kotlin
- **Responsibility**: UK Rail Tracker consumer — boards, commute, journey session (M6), local persistence, location/geofence
- **Depends on**: Darwin LDB Clients; Room/DataStore; Play Services Location; stations asset from `stations.xml`
- **Health**: healthy
- **Notes**: Bulk UI/domain skimmed on a prior scan; instrumented tests disabled. Not opened this run.

## Darwin LDB Clients

- **Path**: `android/app/src/main/java/com/ukrailtracker/app/data/remote/darwin/`
- **Type**: Anti-corruption / remote adapter
- **Language**: Kotlin (OkHttp)
- **Responsibility**: OpenLDBWS arr/dep and service-details; map JSON to data layer
- **Depends on**: RDM OpenLDBWS; API keys via `local.properties`
- **Health**: healthy

## Live-sched POC

- **Path**: `server/poc/live-sched/` (`live-sched` / `live_sched`)
- **Type**: Library + CLI + tests + demo
- **Language**: Python ≥3.12
- **Responsibility**: LM CIF MCA full-refresh into SQLite; CFA zip intake with a 3-digit sequence gate and N/R/D apply; STP `service_day` rebuild; duration-bounded Kafka Push Port overlay with CSV archive and markdown change log; generated schedule-code docs
- **Depends on**: python-dotenv; confluent-kafka; stdlib sqlite3, zipfile, csv, json; pytest (dev); RDM Kafka environment settings; MCA file via `SCHEDULE_DIR`/`SCHEDULE_FILE`; CFA zips via `CFA_WATCH_DIR`
- **Health**: healthy (MCA, CFA, and JSON Kafka paths); at-risk (three CIF parsers, UID-wide journey scan, no CI or coverage gate)
- **Notes**: Deep-scanned this run (2026-10-25). No HTTP server and no UI in this tree. `load` deletes an existing SQLite file and does not persist feed sequence. `cfa-update` keeps the file and reads HD Current File Reference columns 33–39. Constraint: this package, and any successor, must not import `schedule_poc`. This run did not open `server/poc/schedule`. XML and Avro topic flags have no parser. `server/poc/timetable/` and `server/poc/kafka/` were not opened; live-sched only names them as path or credential-name strings.

## Schedule POC

- **Path**: `server/poc/schedule/` (`schedule-poc` 0.1.0)
- **Type**: Library + demo + tests
- **Language**: Python ≥3.12
- **Responsibility**: Offline LM CIF MCA → SQLite; `serviceGUID` identity; Kafka classify/harvest/replay overlay
- **Depends on**: stdlib sqlite3; pytest; optional dotenv/Kafka patterns from Kafka PubSub POC
- **Health**: healthy
- **Notes**: Precursor / sibling stack to Live-sched POC; harvest defaults 20 events / 2h. Not opened this run. Live-sched must not import it.

## Kafka PubSub POC

- **Path**: `server/poc/kafka/`
- **Type**: Exploratory scripts
- **Language**: Python 3.12
- **Responsibility**: Consume Darwin PubSub; decode/classify Push Port updates; offset store
- **Depends on**: confluent-kafka, python-dotenv, fastavro; RDM Kafka environment settings
- **Health**: at-risk
- **Notes**: No automated tests; not a deployable service. Not opened this run. Live-sched names it as the credential-name source and does not import it.

## Timetable CIF Notes

- **Path**: `server/poc/timetable/`
- **Type**: Design notes + sample data
- **Language**: Markdown / CIF
- **Responsibility**: CIF full-refresh design notes; hold MCA sample; domain notes for update-zip layout
- **Depends on**: National Rail CIF dumps (`RJTTF*.txt`, gitignored)
- **Health**: degraded
- **Notes**: Parser implementation lives in Schedule / Live-sched POCs, not here. A prior scan skimmed the opening of `DATA-MODEL.md` (plan versus live layers, CIF identity, STP winner, and the G43975 example the loader implements). This run (2026-10-25) did not open `server/poc/timetable/`.

## Server Harvest Plan

- **Path**: `server/PLAN.md` (+ PDF specs under `server/`)
- **Type**: Architecture / API design
- **Language**: Markdown
- **Responsibility**: `serviceGUID`, alias model, AWS harvest topology, planned `/v1/*`
- **Depends on**: Learnings from Kafka / timetable / schedule / live-sched POCs
- **Health**: design-only
- **Notes**: `server/{api,ingest,infra}` absent. Not opened this run.

## Product Documentation

- **Path**: `docs/` (+ root `README.md`, `android/docs/`)
- **Type**: Product documentation
- **Language**: Markdown
- **Responsibility**: Vision, milestones, architecture narrative, data sources, Android docs
- **Depends on**: None
- **Health**: healthy
- **Notes**: Minor drift (Retrofit vs OkHttp). Not opened this run. `docs/future-intents/local-timetable.md` describes the later local reader; that file is product intent text, not a runtime component.

## Component dependency sketch

```text
Product Documentation ──describes──► Android Application / Server Harvest Plan
Android Application ──uses──► Darwin LDB Clients ──HTTPS──► OpenLDBWS
Live-sched POC ──Kafka JSON──► Darwin PubSub
Live-sched POC ──CIF MCA file──► SQLite (own data/)
Live-sched POC ──CFA zip in watch folder──► SQLite incremental N/R/D
(not in tree) Local timetable reader ──would read──► live_sched.sqlite
Schedule POC ──may use transport patterns──► Kafka PubSub POC ──Kafka──► Darwin PubSub
Schedule POC / Live-sched ──CIF concepts──► (design in) Timetable CIF Notes
Server Harvest Plan ──planned API──► Android Application (future)
```

The local timetable reader line is the service described in `docs/future-intents/local-timetable.md`. It is not code in this repository, and it must not import `schedule_poc`.
