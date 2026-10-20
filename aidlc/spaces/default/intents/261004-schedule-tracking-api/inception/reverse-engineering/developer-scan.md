## Developer Code Scan Results

### Scan Coverage
- **Analyzed deeply**:
  - `server/poc/kafka/` (README, FINDINGS, Python consumers, config, offset store, requirements, `.env.example`)
  - `server/poc/timetable/FINDINGS.md` + CIF sample header (`RJTTF944MCA.txt` HD/TI lines; full body not line-walked)
  - `server/PLAN.md` (identity, feeds, planned API, phasing)
  - `server/pyrightconfig.json`
  - `android/` Gradle root + `app/build.gradle.kts`, `AndroidManifest.xml`, remote Darwin clients, domain models (`Departure`/`Journey`), Room `AppDatabase.kt`, package inventory (~125 main Kotlin sources, 29 unit tests)
  - `docs/` product/architecture/data-model/data-sources/milestone-roadmap (intent-relevant sections)
  - Root `README.md`, `.gitignore`
- **Skimmed only**:
  - `./` monorepo roof (structure confirmed; not every file opened)
  - `aidlc/` workflow records / method scaffolding
  - `.cursor/` harness agents/skills/knowledge
  - `android/docs/`, remaining product docs (`compensation-guide`, commute-state*, simulated-journey)
  - `server/*.pdf` specs (titles known; not parsed)
  - `server/poc/kafka/.venv/`, `.offsets/`, `__pycache__/`
  - Full ~699 MB / 8.5M-line CIF dump body
  - `stations.xml` (referenced; not parsed)

### Packages Found
- `server/poc/kafka` — exploratory scripts — Python 3.12 — RDM Darwin PubSub Kafka consume / classify / metadata
- `server/poc/timetable` — design notes + sample CIF — Markdown/CIF — DTD timetable feed parse design (no parser package yet)
- `server` (design) — planning docs — Markdown/PDF — AWS harvester + `serviceGUID` plan (`PLAN.md`); no `api/`/`ingest/` code yet
- `android` (`:app`) — Android application — Kotlin — UK Rail Tracker consumer (M6 done); Compose UI + domain/session + Darwin LDB clients
- `docs` — product documentation — Markdown — vision, architecture, data sources, milestones
- `aidlc` / `.cursor` — AI-DLC method runtime — Markdown/TypeScript — workflow scaffolding (not product runtime)

### Build System
- **Type**: Multi-root — Gradle Kotlin DSL (Android); pip + venv (Kafka POC); no server API build yet
- **Config Files**:
  - `android/settings.gradle.kts`, `android/build.gradle.kts`, `android/app/build.gradle.kts`, `android/gradle/wrapper/gradle-wrapper.properties` (Gradle 9.5.0)
  - `android/gradle.properties`
  - `server/poc/kafka/requirements.txt`, `server/pyrightconfig.json` (scoped to `poc/kafka`)
  - Root `.gitignore` (ignores `server/poc/timetable/RJTTF*.txt`, kafka `.env`/`.venv`/`.offsets`)
- **Build Dependencies**:
  - Android `:app` self-contained (copies `../stations.xml` → assets `stations.json` on `preBuild`)
  - Kafka POC scripts import sibling modules (`config`, `consumer_utils`, `offset_store`) — no packaging/publish step
  - Planned `server/{api,ingest,infra}` from Appendix B of `PLAN.md` — **not present**

### APIs Discovered
- **External REST (Android → RDM OpenLDBWS)** — `android/.../data/remote/darwin/OpenLdbWsApi.kt` — `GET .../GetArrDepBoardWithDetails/{crs}` (+ query: `numRows`, `filterCrs`, `filterType`, `timeOffset`, `timeWindow`); header `x-apikey`
- **External REST (Android → RDM OpenLDBWS)** — `OpenLdbServiceDetailsApi.kt` — `GET .../GetServiceDetails/{serviceId}`; header `x-apikey`
- **External Kafka (POC → RDM Darwin PubSub)** — `server/poc/kafka/consume.py` / `timestamp-consumer.py` / `metadata.py` — consume JSON/XML/Avro topics (SASL_SSL/PLAIN); **no HTTP server**
- **Planned REST (not implemented)** — `server/PLAN.md` §8 — `GET /v1/stations`, `/boards/{crs}`, `/services/{serviceGUID}`, `/services/{serviceGUID}/live`, `/routes/{origin}/{dest}/{date}`, `/services/resolve`, `/health`

### Frameworks & Libraries
- **Kotlin** — 2.2.10 (Android Gradle plugins) — app language
- **Android Gradle Plugin** — 9.3.3 — build
- **KSP** — 2.3.6 — Room compiler
- **Jetpack Compose BOM** — 2024.10.01 — UI
- **androidx.compose.material3** — via BOM — Material 3
- **Navigation Compose** — 2.8.4 — navigation
- **Lifecycle / ViewModel Compose** — 2.8.7 — presentation
- **Room** — 2.8.4 — local SQLite
- **DataStore Preferences** — 1.1.1 — favourites, pins, commute windows
- **WorkManager** — 2.9.1 — background refresh / geofence rearm
- **OkHttp** — 4.12.0 — HTTP (Darwin clients; no Retrofit in `app/build.gradle.kts`)
- **Play Services Location** — 21.3.0 — GPS / geofences
- **kotlinx-coroutines** — 1.9.0 — async
- **JUnit** — 4.13.2 — unit tests
- **Robolectric** — 4.14.1 — JVM Android unit tests
- **confluent-kafka** — ≥2.6.0 (venv has 2.15.0) — Kafka POC client
- **python-dotenv** — ≥1.0.0 — Kafka `.env` loading
- **fastavro** — ≥1.9.0 — Avro topic decode attempts

### Test Coverage
- **Test Directories**:
  - `android/app/src/test/java/...` — ~28 JVM unit tests (session engine, commute, parsers, use cases, UI mappers)
  - `android/app/src/testSimulator/java/...` — simulator-gated GPS sim test
  - `server/poc/` — **no automated tests**
- **Test Frameworks**: JUnit 4, kotlinx-coroutines-test, Robolectric, org.json (JVM)
- **Coverage Config**: absent (no JaCoCo/Kover/coverage Gradle plugin found)

### Code Quality Indicators
- **Linting**: Android default / Kotlin official style (`kotlin.code.style=official`); Pyright config for Kafka POC (`server/pyrightconfig.json`). No Detekt/ktlint/ruff/CI lint config found.
- **CI/CD**: no `.github/workflows` (or other CI) present
- **Documentation**: Strong — root README, `docs/*`, `android/docs/*`, `server/PLAN.md`, POC FINDINGS/READMEs. Inline KDoc on Darwin clients; Kafka scripts well-commented.

### Technical Debt Signals
- **No schedule-tracking service/API code** — intent target is greenfield relative to runtime; only Kafka explore scripts + timetable notes + AWS-oriented plan
- **Ephemeral Darwin `serviceID` in Android domain** — `JourneyOption.serviceId`, `PinnedJourney.serviceId`, `JourneyLogEntry.serviceId` (`Journey.kt`); matches problem statement in `PLAN.md` §1 / intent Q1
- **Timetable POC incomplete** — FINDINGS describe stream-parse MCA / LM filter / `serviceGUID` emit, but **no parser or TOC filter implementation**; sample MCA is national (~699 MB)
- **PLAN vs intent cost posture** — `PLAN.md` designs AWS (Fargate/Aurora/Redis/CloudFront); intent success criterion is an **inexpensive non-AWS** prototype before AWS translation
- **RDM consumer-group constraint** — Kafka POC uses manual `assign` + local `.offsets/` because group auth may fail; production offset durability unresolved (`FINDINGS.md` §6/§10)
- **No LM (London Midland / atoc) filter in code** — first-cut TOC is intent-confirmed but not encoded in POC scripts
- **Architecture doc drift** — `docs/architecture.md` lists Retrofit; app uses OkHttp wrappers only
- **Secrets hygiene** — `server/poc/kafka/.env` present locally (gitignored); `.env.example` is the safe template
- **No instrumented Android tests** — `enableAndroidTest = false` in `app/build.gradle.kts`
- **aidlc-state workspace detection** lists Languages: TypeScript — inaccurate for product code (Kotlin + Python)

## Handoff Summary
- **Intent-relevant finding**: Brownfield monorepo already validates the two ingest legs the prototype needs — (1) RDM Darwin PubSub Kafka consume/classify with Push Port `uR`/`sR` + `TS`/`schedule` typing in `server/poc/kafka/` (`timestamp-consumer.py` `UPDATE_TYPES`, `FINDINGS.md`), and (2) CIF full-refresh sample + parse design in `server/poc/timetable/FINDINGS.md` — while the Android consumer still keys journeys on ephemeral LDB `serviceId` (`android/.../domain/model/Journey.kt` lines 18–33, 35–44, 46–60). Stable `serviceGUID` and the planned `/v1/*` API exist only as design in `server/PLAN.md` §3/§8; Appendix B `server/api` / `ingest` trees are absent.
- **Risks / follow-up**:
  - Scope contradiction for architect/requirements: prove **local/cheap** schedule store+API (intent) vs documented **AWS-first** harvester (`PLAN.md`) — preserve algorithm learnings, defer cloud until success.
  - First-cut **LM TOC filter** and CIF stream parser are design-only; national MCA cannot be an in-memory load path.
  - Kafka offset strategy and single-consumer RDM group limits must remain in CodeKB risks.
  - Android integration contract should target `serviceGUID` + alias resolve (`PLAN.md` §8 `/services/resolve`) while keeping Darwin `serviceID` during migration.
  - Do not treat AIDLC/Cursor trees or Kafka `.venv` as product components in the nine CodeKB artifacts.
