# Business Overview — uk-rail-tracker

## Purpose

UK Rail Tracker helps UK rail travellers plan and follow journeys: station boards, commute home, and on-train session state on Android, backed by National Rail Darwin (LDB HTTP) and local Python proofs of concept that load a London Midland timetable and overlay Darwin Push Port messages. A planned server-side harvest API remains design-only (`server/PLAN.md`).

A later local timetable viewer, described in `docs/future-intents/local-timetable.md`, will read the SQLite file that `server/poc/live-sched/` already fills. That server and its UI are not in the tree. The viewer, and any successor of live-sched, must not import `schedule_poc`.

## Domain

- **Stations / CRS** — board queries and route filters
- **Services / journeys** — Darwin `serviceID` (ephemeral) vs durable `service_guid` (`SVC-{originCrs}{destCrs}{runDateYYMMDD}{originStdHHMM}`)
- **Calling points / schedule codes** — CIF record types (`TI`/`BS`/`BX`/`LO`/`LI`/`LT`/`ZZ`) and Push Port overlays
- **Commute / session** — walk-up → on-train lifecycle (Android M6)
- **Timetable feed sequence** — 3-digit code, modulo 1000, stored under `feed_meta.key = 'feed_sequence'`. `live-sched cfa-update` reads it from the CFA HD Current File Reference (columns 33–39). The zip basename is ignored. See `architecture.md` for the gate and `api-documentation.md` for the command contract.
- **Deviation** — a calling point's forecast time differs from its booked public time, or the journey status is `deactivated`. Booked times stay the published timetable.
- **Station pair** — two calling points in stop order. The journey's overall origin and destination are the ends of the whole journey.

## Key functionality (as implemented)

| Area | Status |
|------|--------|
| Android consumer (boards, commute, session, Room, geofence) | Implemented (M6) |
| OpenLDBWS arr/dep + service details | Implemented (OkHttp) |
| Offline LM CIF → SQLite + Kafka harvest/replay | Implemented in `server/poc/schedule/` |
| Kafka consume / classify transport patterns | Exploratory in `server/poc/kafka/` |
| **Live-sched POC** (`server/poc/live-sched/`) | **Present** — MCA full-refresh, CFA daily zip apply, and a duration-bounded Kafka JSON overlay (default 3600 seconds) |
| Local timetable server and UI | Absent. `docs/future-intents/local-timetable.md` describes a later reader of the live-sched SQLite file |
| Planned `/v1` harvest API + AWS topology | Design only (`server/PLAN.md`). No S3, Glue, or Athena client in the live-sched tree |

## Live-sched (focused deep scan, 2026-10-25)

`live-sched` is an installed Python package (`live-sched` 0.1.0, console script `live-sched` → `live_sched.cli:main`). It does not import `schedule_poc`. There is no HTTP server. Three commands:

1. **Load** — stream-parses a full MCA, persists TOC `LM` only, rebuilds `service_day`, and rewrites the schedule-code markdown. When the database path is an existing file, the command deletes that file first, which drops offsets, feed sequence, aliases, forecasts, and applied updates.
2. **cfa-update** — one-shot scan of a watch folder (default `server/poc/live-sched/data/cfa-inbox`). Eligible `*.zip` files stay in the folder. Exactly one CFA member is required. The sequence must be the stored code plus one (seed `990` when the store is empty). Accepted zips apply LM `N` / `R` / `D` in one SQLite batch and rebuild `service_day` only for UIDs that changed.
3. **Consume** — polls one Kafka topic for `--duration` seconds (default 3600). Every message is appended to `output/kafka-messages.csv` before classification. Only `TS`, `schedule`, and `deactivated` that resolve to an existing journey change live state. `TS` writes forecast times; booked public times stay as loaded.

A checked-in full-MCA summary (counts and why the raw CIF is the wide scan) was recorded on 2026-10-20 in `architecture.md`. This run did not open the generated docs. Lookup keys and the queries a local reader still lacks are in `api-documentation.md`.

## What the local timetable page will read

The page in `docs/future-intents/local-timetable.md` reads the SQLite file the proof of concept already uses. The server and UI do not exist. This package does not serve the three views.

- The file holds London Midland schedules (`toc = LM`). "All trains" on that page means those journeys.
- Which journeys run on a calendar day is `service_day`. `journeys.run_date` is the CIF window start, not that calendar day.
- The network view lists LM journeys that run today and are not cancelled.
- A train deviates when a forecast time differs from the booked public time, or `journeys.status` is `deactivated`.
- A station pair is two `calling_points` CRS codes in `seq` order. Origin and destination on the journey row are the whole-journey ends.
- Where the train is means the latest calling point at or before now, and the next calling point. The database has no GPS track.
- Issues on a pair are those deviations, plus a today-row in `service_day` marked cancelled.
- **Load** deletes an existing database file. **cfa-update** and **consume** keep the file. A reader has to tolerate the file disappearing on a full reload.
- The package has no method that lists services for one calendar date, and no method that finds a journey by an ordered pair of CRS codes. Detail is in `api-documentation.md`.

## Intent relevance

| Intent | Relevance |
|--------|-----------|
| `261011-live-schedule-poc` (prior) | Delivered the live-sched MCA + Kafka package shape under `server/poc/live-sched/` |
| `261017-cfa-daily-updates` (prior) | CFA zip intake, sequence gate, and N/R/D apply are now present in that package |
| `athena-timetable-lake` (prior) | Built the previous store. The package separates raw intake from the tables it writes. No object-store or query-engine code exists in this tree to copy |
| `local-timetable-viewer` (current) | The server and UI are not built. They will read the live-sched SQLite file described above. They must not import `schedule_poc` |

## Cross-references

- Architecture, decisions, and transaction flows → `architecture.md`
- Components → `component-inventory.md`
- CLI and SQLite lookup contract → `api-documentation.md`
