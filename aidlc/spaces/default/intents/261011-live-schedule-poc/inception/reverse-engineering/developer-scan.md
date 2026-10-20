## Developer Code Scan Results

**Scan meta**: Full rescan (whole repo roof `./`), Minimal depth, intent `261011-live-schedule-poc`, brownfield. Prior CodeKB from `261004-schedule-tracking-api` (2026-10-04) is **stale** — it omits `server/poc/schedule/` (now implemented with CIF load + pytest). This scan supersedes that inventory for product runtime.

### Scan Coverage
- **Analyzed deeply**:
  - `./README.md`, `.gitignore`
  - `docs/architecture.md`, `docs/data-sources.md`, `docs/milestone-roadmap.md`, `docs/product-vision.md` (intent-relevant sections)
  - `android/settings.gradle.kts`, `android/build.gradle.kts`, `android/app/build.gradle.kts`, `android/gradle.properties`, `android/gradle/wrapper/gradle-wrapper.properties`
  - `android/app/src/main/AndroidManifest.xml`, `MainActivity.kt`, `AppContainer.kt`, `ui/navigation/AppNavHost.kt`, `data/local/db/AppDatabase.kt`, `data/remote/darwin/OpenLdbWsApi.kt`, `OpenLdbServiceDetailsApi.kt`, `domain/session/SessionEngineTest.kt` (sample)
  - `server/PLAN.md` (identity + planned API), `server/pyrightconfig.json`
  - `server/poc/kafka/` — README, FINDINGS, `config.py`, `consume.py`, `consumer_utils.py`, `offset_store.py`, `timestamp-consumer.py` (header), `requirements.txt`, `.env.example`
  - `server/poc/timetable/FINDINGS.md` (CIF/MCA design; full MCA dump body not line-walked)
  - `server/poc/schedule/` — README, `pyproject.toml`, `requirements.txt`, `.env.example`, `schedule_poc/*` (cif_loader, db, repository, identity, kafka_classify, harvest, replay), `demo/run_demo.py`, `tests/*`, `fixtures/lm_tiny.mca` header
  - Confirmed absent: `server/poc/live-sched/` (intent target path)
  - Prior CodeKB cross-check: `aidlc/spaces/default/codekb/uk-rail-tracker/{technology-stack,code-structure,component-inventory,api-documentation,code-quality-assessment,reverse-engineering-timestamp}.md`
- **Skimmed only**:
  - Remaining `docs/` (data-model, compensation, commute-state*, simulated-journey) and `android/docs/`
  - Bulk of Android `ui/**` / `domain/**` / `data/**` sources beyond samples (~125 main Kotlin files per prior inventory; not every file re-opened)
  - Remaining Android JVM tests beyond `SessionEngineTest` sample
  - `server/*.pdf` specs (titles known; not parsed)
  - Full national CIF dump `server/poc/timetable/RJTTF*.txt` (~699 MB; gitignored pattern)
  - `stations.xml` (referenced by Gradle copy task; not parsed)
  - `aidlc/` workflow records (except this intent state + prior RE handoff)
  - `.cursor/` harness (agents/skills/hooks — not product runtime)
  - `server/poc/kafka/.venv/`, `.offsets/`, `__pycache__/`, schedule `.venv` if present

### Packages Found
- `android` (`:app`) — application — Kotlin — UK Rail Tracker consumer (M6 done: walk-up → on-train); Compose + Room + Darwin LDB
- `server/poc/schedule` (`schedule-poc` 0.1.0) — library + demo + tests — Python ≥3.12 — Offline LM CIF MCA → SQLite + Kafka harvest/replay overlay (closest precursor to intent)
- `server/poc/kafka` — exploratory scripts — Python 3.12 — RDM Darwin PubSub Kafka consume / classify / metadata
- `server/poc/timetable` — design notes + sample CIF — Markdown/CIF — DTD timetable feed design (parser code lives in schedule POC, not here)
- `server/poc/live-sched` — **absent** — intended new POC folder per project description
- `server` (design) — planning — Markdown/PDF — AWS harvester + `serviceGUID` + `/v1` API (`PLAN.md`); no `api/`/`ingest/`/`infra/` trees
- `docs` (+ root README, `android/docs/`) — product documentation — Markdown
- `aidlc` / `.cursor` — AI-DLC method runtime — Markdown/TypeScript — not product runtime

### Build System
- **Type**: Multi-root — Gradle Kotlin DSL (Android); pip/venv + `pyproject.toml` (schedule POC); pip/venv (kafka POC); no first-party server API build
- **Config Files**:
  - Android: `android/settings.gradle.kts`, `build.gradle.kts`, `app/build.gradle.kts`, `gradle.properties`, `gradle/wrapper/gradle-wrapper.properties` (Gradle **9.5.0**, AGP **9.3.3**)
  - Schedule: `server/poc/schedule/pyproject.toml`, `requirements.txt` (pytest only)
  - Kafka: `server/poc/kafka/requirements.txt`; typecheck `server/pyrightconfig.json` (**scoped to `poc/kafka` only** — schedule not included)
  - Root `.gitignore` (CIF dumps `RJTTF*.txt`, AI-DLC machine-local paths)
- **Build Dependencies**:
  - Android `:app` copies `../stations.xml` → `assets/stations.json` on `preBuild`
  - Schedule package is importable via `pythonpath=.` / demo path injection; no wheel publish
  - Kafka scripts import sibling modules (`config`, `consumer_utils`, `offset_store`) — no packaging
  - Planned `server/{api,ingest,infra}` (PLAN Appendix B) — **not present**

### APIs Discovered
- **External REST (Android → RDM OpenLDBWS)** — `OpenLdbWsApi.kt` — `GET .../GetArrDepBoardWithDetails/{crs}` (+ `numRows`, `filterCrs`, `filterType`, `timeOffset`, `timeWindow`); header `x-apikey`
- **External REST (Android → RDM OpenLDBWS)** — `OpenLdbServiceDetailsApi.kt` — `GET .../GetServiceDetails/{serviceId}`; header `x-apikey`
- **External Kafka (POC → RDM Darwin PubSub)** — `server/poc/kafka/{consume,timestamp-consumer,metadata}.py` — SASL_SSL/PLAIN; JSON/XML/Avro topics; **no HTTP server**
- **Library surface (schedule POC, non-HTTP)** — `schedule_poc`: `load_lm_schedules`, `harvest_lm_events`, `replay_records`, `ScheduleRepository`, SQLite schema (`journeys`, `calling_points`, `applied_updates`, `service_aliases`)
- **Planned REST (not implemented)** — `server/PLAN.md` §8 — `/v1/stations`, `/boards/{crs}`, `/services/{serviceGUID}`, `/services/{serviceGUID}/live`, `/routes/...`, `/services/resolve`, `/health`
- **First-party HTTP API process**: none in repo

### Frameworks & Libraries
- **Kotlin** — 2.2.10 — Android app
- **Android Gradle Plugin** — 9.3.3 — build
- **KSP** — 2.3.6 — Room compiler
- **Jetpack Compose BOM** — 2024.10.01 — UI
- **Navigation Compose** — 2.8.4 — navigation
- **Lifecycle / ViewModel Compose** — 2.8.7 — presentation
- **Room** — 2.8.4 — local SQLite (app)
- **DataStore Preferences** — 1.1.1 — favourites / commute prefs
- **WorkManager** — 2.9.1 — background refresh / geofence rearm
- **OkHttp** — 4.12.0 — Darwin HTTP (no Retrofit in Gradle despite docs)
- **Play Services Location** — 21.3.0 — GPS / geofences
- **kotlinx-coroutines** — 1.9.0 — async
- **JUnit / Robolectric / coroutines-test** — 4.13.2 / 4.14.1 / 1.9.0 — Android JVM tests
- **stdlib sqlite3** — (stdlib) — schedule POC persistence
- **pytest** — ≥8,<9 — schedule POC tests
- **confluent-kafka** — ≥2.6.0 — Kafka POC client
- **python-dotenv** — ≥1.0.0 — `.env` loading (kafka + optional schedule harvest)
- **fastavro** — ≥1.9.0 — Avro decode attempts (kafka)

### Test Coverage
- **Test Directories**:
  - `android/app/src/test/java/...` — JVM unit tests (session, commute, parsers, use cases; ~28 files per prior inventory)
  - `android/app/src/testSimulator/java/...` — simulator-gated GPS tests
  - `server/poc/schedule/tests/` — pytest: `test_loader`, `test_db`, `test_identity`, `test_harvest_replay` (+ `conftest.py`)
  - `server/poc/kafka/` — **no automated tests**
- **Test Frameworks**: JUnit 4, kotlinx-coroutines-test, Robolectric, org.json (Android); pytest (schedule)
- **Coverage Config**: absent (no JaCoCo/Kover/coverage.py config found)

### Code Quality Indicators
- **Linting**: Kotlin official style (`kotlin.code.style=official`); Pyright for `poc/kafka` only. No Detekt/ktlint/ruff project configs.
- **CI/CD**: no `.github/workflows` (or other CI configs) found
- **Documentation**: Strong product docs (`docs/`, READMEs); Kafka/timetable/schedule FINDINGS/READMEs; KDoc on Darwin clients. Drift: architecture still mentions Retrofit; prior CodeKB quality notes outdated regarding schedule parser/tests; aidlc-state still lists Languages=TypeScript for product workspace.

### Technical Debt Signals
- **Intent target missing**: `server/poc/live-sched/` does not exist — greenfield relative to named POC folder (project-description.json)
- **Gap vs intent outputs**: schedule POC has offline demo + 20-events/2h harvest stop; intent asks for **1-hour** Kafka consume, **CSV of all messages**, **MD algorithm doc** for CIF codes (e.g. LI), and **MD change log** per applied DB update — not present as those artifacts/scripts
- **POC duplication risk**: Reusable logic already in `server/poc/schedule/` + transport in `server/poc/kafka/`; new `live-sched` must decide copy vs import vs evolve to avoid three parallel CIF/Kafka stacks
- **Harvest duration mismatch**: `schedule_poc/harvest.py` defaults `DEFAULT_MAX_DURATION_SECONDS = 2*60*60` and `DEFAULT_MAX_LM_EVENTS = 20`; intent wants fixed **1 hour** window with message archival
- **Pyright scope**: `server/pyrightconfig.json` excludes schedule POC — typecheck gap for new Python under `poc/`
- **No CI / no coverage tooling** — delivery confidence local-only
- **Android instrumented tests disabled** (`enableAndroidTest = false`) — not blocking this server POC intent
- **Ephemeral Darwin `serviceID`** still central to Android journey identity — server `serviceGUID` work remains the bridge (`PLAN.md` / schedule identity already implements `SVC-{origin}{dest}{YYMMDD}{HHMM}`)
- **AWS-first PLAN vs local POC intents** — design doc still AWS-oriented; local SQLite POCs are the actual runtime path today
- **Secrets hygiene**: kafka/schedule `.env` patterns gitignored; must not commit credentials

## Handoff Summary
- **Intent-relevant finding**: Closest working precursor is `server/poc/schedule/` — `load_lm_schedules` in `schedule_poc/cif_loader.py` stream-parses CIF MCA records (`TI`/`BS`/`BX`/`LO`/`LI`/`LT`/`ZZ`), filters TOC `LM`, persists SQLite journeys with `serviceGUID` (`identity.py` / `db.py`), and can overlay Push Port updates via `kafka_classify` + `harvest` + `replay`. The named target `server/poc/live-sched/` is **absent**. Kafka transport credentials/patterns live in `server/poc/kafka/` (`config.py` + `.env.example`). Intent-specific deliverables (1h consume → CSV, schedule-code algorithm MD, per-message DB-change MD log) are not implemented yet.
- **Risks / follow-up**: (1) Architect/requirements must decide whether `live-sched` reuses `schedule_poc` or starts a sibling package. (2) Align stop rule (1 hour vs 20-events/2h) and add CSV + markdown logging surfaces. (3) Treat prior CodeKB as stale — republish after this RE. (4) Full MCA dumps remain huge/gitignored; POC should keep fixture-sized MCA for tests. (5) Shell/directory enumeration was blocked mid-scan; Android file counts lean on prior inventory + sampled reads — expand if Construction needs a precise Kotlin file list.
