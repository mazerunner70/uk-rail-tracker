# Developer Code Scan — live-sched (focused)

Intent: `cfa-daily-updates`  
Scan mode: FOCUSED (pipeline link 1)  
Snapshot paths: `server/poc/live-sched`  
Depth: Minimal (poc)  
Date: 2026-10-17

## Developer Code Scan Results

### Scan Coverage
- **Analyzed deeply**:
  - `server/poc/live-sched/pyproject.toml`
  - `server/poc/live-sched/README.md`
  - `server/poc/live-sched/live_sched/` (all modules: `__init__.py`, `cli.py`, `cif_loader.py`, `db.py`, `repository.py`, `service_day.py`, `config.py`, `apply.py`, `consumer.py`, `kafka_classify.py`, `identity.py`, `docs_gen.py`, `change_log.py`, `csv_archive.py`)
  - `server/poc/live-sched/tests/` (all test modules + `conftest.py`)
  - `server/poc/live-sched/fixtures/` (`lm_tiny.mca`, `g43975_overlay.mca`, `kafka_payloads.jsonl` — structure/purpose)
  - `server/poc/live-sched/docs/schedule-code-algorithms.md` / `schedule-code-summary.md` (generated artefacts)
  - `server/poc/live-sched/scripts/compare_mca_to_db.py`
  - `server/poc/live-sched/demo/run_demo.py`
- **Skimmed only**:
  - `server/poc/timetable/DATA-MODEL.md` / `FINDINGS.md` — domain reference for CFA/`feed_receipt`/zip layout (outside snapshot; context for gaps)
  - `aidlc/spaces/default/codekb/uk-rail-tracker/` — STALE prior CodeKB from `261011-live-schedule-poc` (titles only; not re-merged here)
  - `server/poc/live-sched/.venv/`, `live_sched.egg-info/`, `.pytest_cache/` — build artefacts, not source

### Packages Found
- `live-sched` / `live_sched` — application library + CLI — Python ≥3.12 — MCA LM timetable load into SQLite + one-hour Kafka overlay apply
- `tests` — pytest suite — Python — unit/integration tests with tiny fixtures
- `demo` — thin wrapper — Python — re-exports CLI `main`
- `scripts/compare_mca_to_db.py` — diagnostic script — Python — MCA vs DB count comparison (duplicates loader logic, not imported by package)

### Build System
- **Type**: setuptools via `pyproject.toml` (editable install `pip install -e ".[dev]"`)
- **Config Files**: `server/poc/live-sched/pyproject.toml` only (no Makefile, Dockerfile, tox, ruff/mypy config in-tree)
- **Build Dependencies**:
  - runtime: `python-dotenv>=1.0,<2`, `confluent-kafka>=2.3,<3`
  - optional `dev`: `pytest>=8.0,<9`
  - build-backend: `setuptools>=68`
- **Entry point**: console script `live-sched` → `live_sched.cli:main`
- **Package discovery**: `live_sched*` included; `tests*`, `demo*`, `data*`, `output*`, `fixtures*`, `docs*` excluded from the wheel
- **Venv convention** (README): project-local `.venv` under `server/poc/live-sched` — do not install into system Python

### APIs Discovered
- **CLI** — `live_sched/cli.py` — 2 subcommands:
  - `live-sched load [--mca] [--env] [--db] [--docs]` — stream-parse MCA, persist LM journeys, write schedule-code docs; **deletes existing DB file** before reload when path ≠ `:memory:`
  - `live-sched consume [--db] [--env] [--duration] [--csv] [--change-log] [--format]` — Kafka consumer (default 3600s) → CSV archive + apply + markdown change log
- **Library (internal, no HTTP)**:
  - `load_lm_schedules(mca_path, repo)` — CIF MCA stream parse
  - `ScheduleRepository` — SQLite CRUD / service_day rematerialise / Kafka apply audit
  - `apply_payload` / `process_message_stream` / `run_live_consumer` — Push Port overlay
  - `resolve_schedule_path` / `load_kafka_settings` — workspace-root `.env`
- **HTTP/REST/gRPC**: none
- **Folder watch / zip / CFA CLI**: **absent**

### Frameworks & Libraries
- Python 3.12+ stdlib (`argparse`, `sqlite3`, `logging`, `csv`, `json`, `pathlib`, `dataclasses`)
- `python-dotenv` — load workspace-root `.env`
- `confluent-kafka` — RDM Darwin Pub/Sub consumer (SASL_SSL)
- `pytest` — tests (`testpaths = ["tests"]`)
- SQLite — local persistence (`data/live_sched.sqlite` default)

### File Classification (deep)

| File | Category | Role |
|------|----------|------|
| `cli.py` | Controller/Handler | CLI routing for load/consume |
| `cif_loader.py` | Service/UseCase | Stream CIF MCA; LM filter; flush journeys |
| `db.py` | Configuration / Migration | Schema DDL + connect |
| `repository.py` | Repository/DAO | Journey/calling_point/service_day/alias/applied_updates |
| `service_day.py` | Service/UseCase | STP winner resolution (C→O→P→N) |
| `identity.py` | Utility | `SVC-{origin}{dest}{YYMMDD}{HHMM}` GUID |
| `config.py` | Configuration | Paths, Kafka settings, schedule path |
| `apply.py` | Service/UseCase | TS / schedule / deactivated apply |
| `consumer.py` | Service/UseCase | Duration-bounded Kafka/message loop |
| `kafka_classify.py` | Utility | Decode/classify Push Port JSON |
| `docs_gen.py` | Utility | Write schedule-code markdown |
| `change_log.py` / `csv_archive.py` | Utility | Side-effect outputs for consume |
| `tests/*` | Test | Happy-path + STP overlay + config path |
| `scripts/compare_mca_to_db.py` | Utility (orphan-ish) | Parallel MCA stats vs DB |

### CIF / MCA loading (intent-critical)

**What exists today**
- Stream parse of flat MCA/CIF text (`load_lm_schedules` in `cif_loader.py`).
- Record codes handled for state: `TI`, `BS`, `BX`, `LO`, `LI`, `LT`, `ZZ`. `HD` is listed in `HANDLED_CODES` and counted, but **not parsed** (no sequence, file reference, last-file reference, or F/U indicator extracted).
- TOC filter default `LM`; non-LM schedules skipped after BX.
- BS fields used: `train_uid` `[3:9]`, dates, `days_run`, STP `[79:80]`. Character at `[2:3]` (CIF transaction type `N`/`R`/`D`) is **ignored** — assumed insert/upsert path only.
- Persistence: `UNIQUE (train_uid, run_date, stp_indicator)` on `journeys`; upsert on `service_guid`; then `rematerialize_service_days`.
- Docs: per-code counts + algorithm markdown (MCA load story only).

**What is missing for CFA daily updates**
- No `.CFA` path; no BS transaction switch (`N` insert / `R` revise / `D` delete — delete is BS alone per RSPS5046).
- No TIPLOC amend/delete (`TA`/`TD`) handling.
- No zip open (`zipfile`) or update-zip layout (`RJTTCnnn.CFA` inside update zip).
- No filesystem folder watch (no polling loop, watchdog, or inotify).
- No DB table for previous load / update sequence (intent seed **990**, next accepted **991**, then `+1 mod 1000`). Schema has `applied_updates` for **Kafka** overlays only — not timetable feed receipts.
- No load report counters: total / new / override / duplicate.
- No refuse-on-wrong-sequence behaviour.
- `cmd_load` **unlinks the entire SQLite file** before MCA reload — incompatible with incremental CFA apply that must preserve prior plan + seed sequence.

Evidence — HD counted not applied (`cif_loader.py` ~20–21, stream loop counts `rec` then only branches TI/BS/BX/LO/LI/LT/ZZ); CLI wipe (`cli.py` ~34–36); schema without feed receipt (`db.py` SCHEMA_SQL).

### DB persistence of load/update codes

| Table | Purpose today | CFA sequence relevance |
|-------|---------------|------------------------|
| `journeys` | CIF plan rows (LM) | Target of incremental N/R/D |
| `calling_points` | Stops + forecast overlays | Must update with revise/delete |
| `service_day` | STP-resolved day map | Rematerialise after CFA apply |
| `applied_updates` | Kafka apply audit | **Not** timetable sequence |
| `service_aliases` | rid → service_guid | Untouched by CFA intent |

**Gap**: no `feed_receipt` / `last_load_code` / similar. Domain doc outside snapshot (`server/poc/timetable/DATA-MODEL.md` §4) describes the needed model; live-sched does not implement it. Intent requires seeding previous code **990** in DB so first zip **991** is accepted.

### Zip / folder handling

- `config.resolve_schedule_path` reads a **single file** via `SCHEDULE_DIR` + `SCHEDULE_FILE` (or `--mca`). Directory is a static path, not watched.
- No `zipfile` import anywhere under `live_sched/`.
- Consume “watches” Kafka only, not a drop folder.

### Test Coverage
- **Test Directories**: `server/poc/live-sched/tests/`
- **Test Frameworks**: pytest 8.x
- **Coverage Config**: absent (no pytest-cov / coverage.ini)
- **Tests present**:
  - `test_loader.py` — LM filter, progress logs, docs gen
  - `test_db.py` — schema table presence
  - `test_repository.py` — upsert + calling points
  - `test_service_day.py` — STP priority + G43975 overlay fixture
  - `test_apply.py` — Kafka TS/schedule/deactivated + duration stop
  - `test_config.py` — SCHEDULE_DIR/FILE resolution + `--mca` override
- **Gaps vs intent**: no CFA fixtures, no sequence refuse tests, no zip extract tests, no folder-watch tests, no count-report assertions

### Code Quality Indicators
- **Linting**: no in-package ruff/flake8/mypy config; occasional `# noqa: E402` in scripts/demo
- **CI/CD**: none under `server/poc/live-sched/`; not scanned for repo-root workflows (outside focus)
- **Documentation**: strong README + generated CIF algorithm docs; package does not import sibling `schedule_poc`
- **Naming / structure**: clear module split; functions generally single-purpose; CIF loader ~352 lines (acceptable for stream state machine)
- **Error handling**: MCA missing → CLI exit 1; Kafka env missing → RuntimeError; IntegrityError on journey insert logs conflict then re-raises; Kafka poll errors logged and continued

### Technical Debt Signals
1. **Full-load destructive DB replace** (`cli.cmd_load` unlinks DB) — blocks incremental CFA without redesign of load lifecycle.
2. **HD metadata ignored** — sequence / F|U / file refs never stored; blocks sequence gate.
3. **BS transaction type ignored** — CFA `R`/`D` cannot be expressed with current flush path.
4. **Duplicated MCA parse** in `scripts/compare_mca_to_db.py` vs `cif_loader` — drift risk.
5. **`resolve_service_guid` UID-only fallback** walks all journeys (`apply.py`) — fine for POC size, not ideal long-term.
6. **No CFA/zip/watch deps or tests** — greenfield relative to this intent inside an otherwise working MCA+Kafka POC.
7. **STALE CodeKB** from prior intent — architect must merge this focused scan; do not treat prior store as verified for CFA.

### Dependency adjacency (deep modules)

```
cli → cif_loader, config, consumer, db, docs_gen, repository
cif_loader → identity, repository
repository → service_day
consumer → apply, config, csv_archive, kafka_classify, repository
apply → change_log, kafka_classify, repository
docs_gen → cif_loader (HANDLED_CODES)
config → dotenv
consumer (live) → confluent_kafka
```

No circular imports observed among `live_sched` modules.

## Handoff Summary
- **Intent-relevant finding**: `live-sched` today is an **MCA full-refresh + Kafka overlay** POC. CIF loading (`cif_loader.load_lm_schedules`) persists LM journeys into SQLite and rematerialises `service_day`, but **does not** watch a folder, open daily update zips, parse CFA `N`/`R`/`D`, persist a 3-digit load/update code (seed 990), refuse wrong sequences, or emit total/new/override/duplicate counts. `HD` is counted only; `applied_updates` is Kafka-only; `cmd_load` deletes the DB file on each MCA load (`cli.py` 34–36, `db.py` schema, `cif_loader.py` HD/BS handling).
- **Risks / follow-up**:
  1. Architect should treat CFA path as **net-new** atop existing repository/service_day/CIF stream patterns — not a thin CLI flag.
  2. Sequence store likely needs a new table (align with timetable `feed_receipt` concept) seeded to **990**; first accepted zip **991**.
  3. Incremental apply must **not** unlink the DB; MCA wipe behaviour and CFA apply must be separated.
  4. Domain rules for zip/CFA layout live in `server/poc/timetable/DATA-MODEL.md` (shallow) — confirm RSPS5046 field offsets for HD sequence and BS txn when designing.
  5. Prior CodeKB is STALE; this handoff is the deep source for `server/poc/live-sched` for merge.
  6. Next pipeline step: architect synthesises/merges the 9 CodeKB artefacts from this scan + shallow domain docs as needed.
