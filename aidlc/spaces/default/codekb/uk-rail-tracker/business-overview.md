# Business Overview — uk-rail-tracker

## Purpose

UK Rail Tracker helps UK rail travellers plan and follow journeys: station boards, commute home, and on-train session state on Android, backed by National Rail Darwin (LDB HTTP) and local Python proofs of concept that load a London Midland timetable and overlay Darwin Push Port messages. A planned server-side harvest API remains design-only (`server/PLAN.md`).

## Domain

- **Stations / CRS** — board queries and route filters
- **Services / journeys** — Darwin `serviceID` (ephemeral) vs durable `service_guid` (`SVC-{originCrs}{destCrs}{runDateYYMMDD}{originStdHHMM}`)
- **Calling points / schedule codes** — CIF record types (`TI`/`BS`/`BX`/`LO`/`LI`/`LT`/`ZZ`) and Push Port overlays
- **Commute / session** — walk-up → on-train lifecycle (Android M6)
- **Timetable feed sequence** — 3-digit code, modulo 1000, stored under `feed_meta.key = 'feed_sequence'`. `live-sched cfa-update` reads it from the CFA HD Current File Reference (columns 33–39). The zip basename is not a source. See `architecture.md` for the gate and `api-documentation.md` for the command contract.

## Key functionality (as implemented)

| Area | Status |
|------|--------|
| Android consumer (boards, commute, session, Room, geofence) | Implemented (M6) |
| OpenLDBWS arr/dep + service details | Implemented (OkHttp) |
| Offline LM CIF → SQLite + Kafka harvest/replay | Implemented in `server/poc/schedule/` |
| Kafka consume / classify transport patterns | Exploratory in `server/poc/kafka/` |
| **Live-sched POC** (`server/poc/live-sched/`) | **Present** — MCA full-refresh, CFA daily zip apply, and a duration-bounded Kafka JSON overlay (default 3600 seconds) |
| Planned `/v1` harvest API + AWS topology | Design only (`server/PLAN.md`). No S3, Glue, or Athena client in the live-sched tree |

## Live-sched (focused deep scan, 2026-10-20)

`live-sched` is an installed Python package (`live-sched` 0.1.0, console script `live-sched` → `live_sched.cli:main`). It does not import `schedule_poc`. Three commands:

1. **Load** — stream-parses a full MCA, persists TOC `LM` only, rebuilds `service_day`, and rewrites the schedule-code markdown. When the database path is not the in-memory database, the command deletes the SQLite file first, which drops offsets, feed sequence, aliases, forecasts, and applied updates.
2. **cfa-update** — one-shot scan of a watch folder (default `server/poc/live-sched/data/cfa-inbox`). Eligible `*.zip` files stay in the folder. Exactly one CFA member is required. The sequence must be the stored code plus one (seed `990` when the store is empty). Accepted zips apply LM `N` / `R` / `D` in one SQLite batch and rebuild `service_day` only for UIDs that changed.
3. **Consume** — polls one Kafka topic for `--duration` seconds (default 3600). Every message is appended to `output/kafka-messages.csv` before classification. Only `TS`, `schedule`, and `deactivated` that resolve to an existing journey change live state. `TS` writes forecast times; booked public times stay as loaded.

A checked-in full-MCA summary (counts and why the raw CIF is the wide scan) is recorded in `architecture.md`. Lookup keys are recorded in `api-documentation.md`.

## Intent relevance

| Intent | Relevance |
|--------|-----------|
| `261011-live-schedule-poc` (prior) | Delivered the live-sched MCA + Kafka package shape under `server/poc/live-sched/` |
| `261017-cfa-daily-updates` (prior) | CFA zip intake, sequence gate, and N/R/D apply are now present in that package |
| `athena-timetable-lake` (current) | The package already separates raw intake (MCA path, zip left in the watch folder, Kafka CSV) from the tables it queries. No object-store or query-engine code exists in this tree to copy |

## Cross-references

- Architecture, decisions, and transaction flows → `architecture.md`
- Components → `component-inventory.md`
- CLI and SQLite lookup contract → `api-documentation.md`
