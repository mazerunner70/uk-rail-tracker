## Developer Code Scan Results

Focused Standard-depth scan of the existing live-sched proof of concept. The local timetable service in `docs/future-intents/local-timetable.md` does not exist yet. This scan records the SQLite store and the commands that fill it. Snapshot bound: `server/poc/live-sched`, store generation `sha256:125731cd588e65aa541d751cf6f93b0f83e2fad194ce475567120ae7e4a9a540`, source fingerprint `git:99dd493302d01daca4b62fdaa26dee42ac41aca2`.

### Scan Coverage
- **Analyzed deeply**:
  - server/poc/live-sched
  - server/poc/live-sched/pyproject.toml
  - server/poc/live-sched/README.md
  - server/poc/live-sched/.gitignore
  - server/poc/live-sched/live_sched/__init__.py
  - server/poc/live-sched/live_sched/db.py
  - server/poc/live-sched/live_sched/identity.py
  - server/poc/live-sched/live_sched/cli.py
  - server/poc/live-sched/live_sched/config.py
  - server/poc/live-sched/live_sched/repository.py
  - server/poc/live-sched/live_sched/service_day.py
  - server/poc/live-sched/live_sched/cif_loader.py
  - server/poc/live-sched/live_sched/cfa_apply.py
  - server/poc/live-sched/live_sched/cfa_zip.py
  - server/poc/live-sched/live_sched/cfa_report.py
  - server/poc/live-sched/live_sched/feed_sequence.py
  - server/poc/live-sched/live_sched/apply.py
  - server/poc/live-sched/live_sched/consumer.py
  - server/poc/live-sched/live_sched/kafka_classify.py
  - server/poc/live-sched/live_sched/csv_archive.py
  - server/poc/live-sched/live_sched/change_log.py
  - server/poc/live-sched/live_sched/docs_gen.py
  - server/poc/live-sched/demo/run_demo.py
  - server/poc/live-sched/scripts/compare_mca_to_db.py
  - server/poc/live-sched/tests/conftest.py
  - server/poc/live-sched/tests/test_db.py
  - server/poc/live-sched/tests/test_feed_sequence.py
  - server/poc/live-sched/tests/test_cfa_apply.py
  - server/poc/live-sched/tests/test_apply.py
- **Skimmed only**:
  - server/poc/live-sched/.venv
  - server/poc/live-sched/.pytest_cache
  - server/poc/live-sched/fixtures
  - server/poc/live-sched/data
  - server/poc/live-sched/output
  - server/poc/live-sched/docs
  - server/poc/schedule

`server/poc/schedule` was noted from the sibling `pyproject.toml` path and from the live-sched README. Its sources were not opened. `server/poc/timetable` and `server/poc/kafka` appear only as path strings inside live-sched (`config.py`) and were not opened. Fixture bytes, the gitignored SQLite file, generated markdown under `docs/`, and CSV/change-log output were not opened.

### Packages Found
- `live-sched` / import package `live_sched` — application library plus CLI — Python — London Midland timetable load, CFA daily update, and Darwin Push Port overlay into one SQLite file (`pyproject.toml`, `live_sched/__init__.py`). Version `0.1.0`.
- `tests` — test package — Python — pytest suite; excluded from the installed distribution (`pyproject.toml` `[tool.setuptools.packages.find]`).
- `demo` — script — Python — thin wrapper that calls `live_sched.cli.main` (`demo/run_demo.py`). Excluded from the distribution.
- `scripts/compare_mca_to_db.py` — offline script — Python — recounts an MCA the same way load does and prints journey, calling-point, and service-day totals. Excluded from the distribution. Not a reader API.

No HTTP server, UI, Docker image, or second application package exists inside this tree.

### Build System
- **Type**: setuptools (`setuptools>=68`, `setuptools.build_meta`) with a console script. Requires Python `>=3.12`. The on-disk `.venv` sits on `python3.14` (path `server/poc/live-sched/.venv/lib/python3.14/`); that tree was not inspected beyond the path name.
- **Config Files**: `server/poc/live-sched/pyproject.toml` (project metadata, dependencies, pytest `testpaths` and `pythonpath`). `server/poc/live-sched/.gitignore` ignores `data/*.sqlite`, `output/*.csv`, `output/db-changes.md`, `.venv/`, and `.pytest_cache/`. No `setup.cfg`, `requirements.txt`, Dockerfile, or compose file in this tree.
- **Build Dependencies**: the distribution contains only `live_sched`. Tests, demo, fixtures, docs, data, and output are excluded. Install path documented in the README is a project-local `.venv` and `pip install -e ".[dev]"`. No package in this tree depends on another workspace package.

### APIs Discovered
- CLI — `live_sched/cli.py` `build_parser` — 3 subcommands, no HTTP routes.
  - `load` — `cmd_load`. If the database path is an existing file, it deletes that file, then `connect`, `init_schema`, `load_lm_schedules`, and `write_schedule_code_docs`. Flags: `--mca`, `--env`, `--db`, `--docs`. Default database `server/poc/live-sched/data/live_sched.sqlite` (`config.py` `DEFAULT_DB_PATH`).
  - `cfa-update` — `cmd_cfa_update`. Opens the existing file, creates missing tables, runs `process_watch_folder`. Does not delete the file. Flags: `--db`, `--env`, `--watch`, `--seed`. Statuses printed by `cfa_report.format_outcome`: `accepted`, `refused`, `failed`, `nothing`.
  - `consume` — `cmd_consume`. Opens the existing file, loads Kafka settings, runs `run_live_consumer`. Does not delete the file. Flags: `--db`, `--env`, `--duration` (default 3600 seconds), `--csv`, `--change-log`, `--format` (`json`, `xml`, `avro`; default `json`).
- SQLite schema — `live_sched/db.py` `SCHEMA_SQL` — 7 tables: `journeys`, `calling_points`, `service_day`, `applied_updates`, `service_aliases`, `feed_meta`, `kafka_offsets`. `connect` sets `row_factory` to `sqlite3.Row` and `PRAGMA foreign_keys = ON`. `init_schema` is `CREATE TABLE IF NOT EXISTS` plus four indexes. There is no migration module.
- Repository — `live_sched/repository.py` `ScheduleRepository` — in-process data access, not a network API. Read methods a local reader can mirror: `get_journey`, `get_service_day`, `get_journey_by_uid_date`, `get_journey_by_cif_identity`, `list_journeys`, `list_calling_points`, `resolve_alias`, `known_lm_uids`. There is no method that lists every service for one calendar date, and no method that finds a journey by an ordered pair of CRS codes.
- CIF load — `cif_loader.load_lm_schedules` — stream-parses an MCA and persists `toc_filter` (default `LM`).
- CFA apply — `cfa_apply.apply_cfa_bytes` and `process_watch_folder` — sequence gate plus `N` / `R` / `D` against the same tables.
- Live overlay — `apply.apply_payload` — applies Push Port children `TS`, `schedule`, and `deactivated`. `consumer.process_message_stream` archives every message first, then applies, then stores the next Kafka offset.
- Feed sequence — `feed_sequence.py` — `feed_meta` key `feed_sequence`. `parse_hd_sequence` reads CIF columns 33–39 (0-based `[32:39]`).

External systems, all reached only by the CLI writers: a local MCA file (`SCHEDULE_DIR` / `SCHEDULE_FILE`, default `server/poc/timetable/RJTTF944MCA.txt`), a CFA watch folder (`CFA_WATCH_DIR`, default `data/cfa-inbox`), and a Darwin Kafka broker via `confluent_kafka.Consumer` (`consumer.iter_kafka_messages`). Credentials are environment variables `KAFKA_BOOTSTRAP_SERVERS`, `KAFKA_CONSUMER_KEY`, `KAFKA_CONSUMER_SECRET`, `KAFKA_GROUP_ID`, plus optional `KAFKA_SSL_CA_LOCATION` and `KAFKA_TOPIC_JSON` / `KAFKA_TOPIC_XML` / `KAFKA_TOPIC_AVRO` (`config.load_kafka_settings`). No credential literals are stored in the modules read here.

### Frameworks & Libraries
- Python — `>=3.12` — runtime (`pyproject.toml`).
- setuptools — `>=68` — build.
- python-dotenv — `>=1.0,<2` — loads the workspace-root `.env` (`config.py`).
- confluent-kafka — `>=2.3,<3` — live consumer only; imported inside `iter_kafka_messages`, not at module import.
- pytest — `>=8.0,<9` — dev extra. The unused `.venv` listing shows `pytest-8.4.2`; that install was not executed for this scan.
- sqlite3, argparse, zipfile, json, csv, logging, dataclasses — standard library. No web framework.

### Test Coverage
- **Test Directories**: `server/poc/live-sched/tests`. Shared fixtures in `tests/conftest.py`: in-memory SQLite `db_conn` / `repo`, `fixtures/lm_tiny.mca`, `fixtures/kafka_payloads.jsonl`. A third fixture file `fixtures/g43975_overlay.mca` is named by `tests/test_service_day.py` (test name only; that module body was not read). `tests/cfa_fixtures.py` builds synthetic HD/CFA zips (imported by the CFA tests that were read).
- **Test Frameworks**: pytest, configured only by `[tool.pytest.ini_options]` (`testpaths = ["tests"]`, `pythonpath = ["."]`). README command is `.venv/bin/pytest tests/ -q` from the package root. 45 `def test_` functions across 11 modules. This scan did not run them.
- **Coverage Config**: absent. No `pytest-cov`, `.coveragerc`, coverage section, or coverage tool setting in `pyproject.toml` or elsewhere in this tree.

Modules whose tests were opened: schema creation (`tests/test_db.py`), HD sequence and the member-name fallback (`tests/test_feed_sequence.py`), CFA accept/refuse/N/R/D and the member-name accept path (`tests/test_cfa_apply.py`), and TS versus booked times plus `deactivated` (`tests/test_apply.py`). Remaining test modules were inventoried by name only.

### Code Quality Indicators
- **Linting**: no Ruff, Black, Flake8, or mypy config in this tree. `pyproject.toml` has no lint or format section. Three `# noqa` sites: `SLF001` on `repo._conn` in `cfa_apply.py` (lines 237, 391, 423); `E402` in `demo/run_demo.py` and `scripts/compare_mca_to_db.py` after a `sys.path` insert.
- **CI/CD**: no pipeline file, Dockerfile, or compose file inside `server/poc/live-sched`.
- **Documentation**: `README.md` covers setup, the three commands, default outputs, and the `schedule_poc` boundary. `docs_gen.py` writes `docs/schedule-code-summary.md` and `docs/schedule-code-algorithms.md` on each load (generated copies under `docs/` were not opened). Public functions have docstrings. Style observed in the modules read: `from __future__ import annotations`, PEP 604 unions, frozen dataclasses for records, `logging.getLogger(__name__)`, snake_case, domain exceptions (`ValueError`, `RuntimeError`) mapped to CLI exit codes in `cli.py`.

### Technical Debt Signals
- CIF location parsing is copied. `cif_loader.py` and `cfa_apply.py` each define `_slice`, `_public_time`, `_parse_ti_crs`, `_Location`, and a schedule draft. `scripts/compare_mca_to_db.py` starts a third copy (`_slice`, `_Location`, `_parse_ti`).
- `ScheduleRepository._conn` is private, and CFA apply reaches it directly to read and write `feed_meta` (`cfa_apply.py` lines 237, 391, 423). `get_feed_sequence` also commits a seed row when the key is absent (`feed_sequence.py` lines 42–49, default seed `990`).
- `applied_updates.service_guid` references `journeys` with no `ON DELETE` action (`db.py` lines 46–54). `calling_points`, `service_day`, and `service_aliases` use `ON DELETE CASCADE`. `delete_journey` (`repository.py` lines 217–223) deletes the journey row only. With foreign keys on, a journey that already has `applied_updates` rows cannot be deleted. CFA `D`, and `R` when the new `service_guid` differs, call `delete_journey` inside the zip batch (`cfa_apply.py` lines 173 and 199–200). No test read here deletes a journey after `record_applied_update`.
- `resolve_service_guid` (`apply.py` lines 137–142), when a message has a UID and no usable schedule date, scans `list_journeys()` and returns the first journey with that UID. `list_journeys` orders by `service_guid` and has no date predicate (`repository.py` lines 225–234).
- `overlay_calling_point_times` (`repository.py` lines 339–368) takes arguments named `public_arr` and `public_dep` and writes `forecast_arr` and `forecast_dep`. The lookup is `WHERE service_guid = ? AND tiploc = ?` with `fetchone`, so a repeated TIPLOC updates one row.
- `upsert_journey` on conflict overwrites `status` (`repository.py` lines 107–117). CFA `_build_journey` leaves `status` at the dataclass default `published` (`cfa_apply.py` lines 84–95, `repository.py` line 26). `replace_calling_points` deletes every stop for the guid and inserts the new points with null forecasts (`repository.py` lines 139–167). An `R` therefore clears a `deactivated` status and any TS overlay on that journey.
- `cli.py` accepts `--format xml` and `--format avro`, and `KafkaSettings` has topic fields for both. `decode_kafka_value` (`kafka_classify.py`) is JSON only. No test in the inventory exercises xml or avro.
- Indexes are `idx_journeys_uid_date` on `(train_uid, run_date)`, `idx_service_day_guid`, `idx_aliases_guid`, and `idx_calling_guid` (`db.py` lines 76–79). `run_date` stores CIF `date_runs_from`, not the calendar day. There is no index on `service_day.calendar_date` or `calling_points.crs`.
- No `TODO`, `FIXME`, or `HACK` comments in the Python sources searched. Hard-coded domain constants, not secrets: `LM_TOC = "LM"`, feed seed `990`, duration `3600`, default MCA name `RJTTF944MCA.txt`.

## Handoff Summary
- **Intent-relevant finding**: A new local reader of `server/poc/live-sched/data/live_sched.sqlite` has seven tables and no query service in front of them. `journeys.service_guid` is the primary key, shaped `SVC-{originCrs}{destCrs}{runDateYYMMDD}{originStdHHMM}` (`identity.py` lines 24–39; example `SVC-CREBHM2605170925` in `tests/test_db.py`). `run_date` is the CIF window start `date_runs_from` (`YYMMDD` text), with `date_runs_to` and Monday-first `days_run` (`service_day.py` `iter_matching_dates`, weekday index 0 = Monday). One journey row covers every matching calendar day. Which schedule actually runs on a day is `service_day` (`train_uid`, `calendar_date`) → (`service_guid`, `cancelled`). STP `C` stores `service_guid` NULL and `cancelled` 1; otherwise overlay `O`, then permanent `P`, then new `N` (`service_day.py` `pick_winner`, lines 65–98). Stops are `calling_points` ordered by `seq` (0-based LO/LI/LT order), with nullable `crs`, booked `public_arr` / `public_dep` (`HHMM` text or NULL), nullable `forecast_arr` / `forecast_dep`, and `activity` `origin`, `call`, `pass`, or `destination` (`db.py` lines 24–36, `cif_loader.py` `_flush_lm`). Load (`cli.py` `cmd_load` lines 38–44, `cif_loader.py` `LM_TOC`) keeps BX TOC `LM` only, and deletes the SQLite file first, which clears journeys, stops, `service_day`, aliases, forecasts, `applied_updates`, `kafka_offsets`, and `feed_meta`. CFA update (`cfa_apply.py`) does not delete the file. It accepts a zip when the sequence is the stored `feed_meta.feed_sequence` plus one (modulo 1000). The sequence is the HD Current File Reference, columns 33–39 (`feed_sequence.py` `parse_hd_sequence`). When that field has no three digits, `resolve_feed_sequence` falls back to the inner CFA member name (`RJTTC991CFA.txt` → `991`); the zip basename is not read (`tests/test_cfa_apply.py` `test_member_name_sequence_when_hd_has_no_digits`, zip name `timetable_update_991.zip`). Transaction `N` inserts, `R` replaces the CIF identity `(train_uid, date_runs_from, stp_indicator)`, `D` deletes that identity when it is already stored. Consume (`apply.py`) archives the message, then: `TS` writes forecast columns and leaves booked public times (`tests/test_apply.py` lines 46–52); `deactivated` sets `journeys.status` from `published` to `deactivated`; `schedule` appends `applied_updates` and does not rewrite booked times (`apply.py` line 228). The package has no HTTP API and no station-pair or “services on this calendar date” query. No Python file under this tree imports `schedule_poc` (search of `*.py`; the boundary is stated in `README.md` line 9 and `pyproject.toml` description). The new reader must not import `schedule_poc`.
- **Risks / follow-up**: Preserve these for the architect. The reader is a reader of an existing file: `connect` creates a missing database file, `init_schema` is additive only, and `get_feed_sequence` commits seed `990` when `feed_meta` is empty, so a read path that calls those helpers writes. “Today” is `service_day.calendar_date`, not `journeys.run_date`. A station pair is two `calling_points.crs` values in `seq` order; `origin_crs` and `dest_crs` are the whole-journey ends, and `crs` is NULL when the TIPLOC map has no code. Two different flags mean “not running as planned”: `service_day.cancelled` (STP cancellation, often with a null guid) and `journeys.status = 'deactivated'` (live overlay). Booked times stay in `public_arr` / `public_dep`; live times stay in `forecast_*`. `load` wipes the file, so a reader must tolerate the database disappearing. CFA `R` resets `status` to `published` and drops forecast times. A later CFA `D` or guid-changing `R` can fail while foreign keys are on if `applied_updates` still points at the journey. The UID-wide `list_journeys()` fallback can attach a live message to another schedule with the same UID. `feed_meta.feed_sequence` is the last accepted code, and that code can come from the inner member name when the HD field has no digits. There is no index for “all rows for this calendar date” or “stops with this CRS”. XML and Avro topic choices are untested JSON-decode paths. The new server must not import `schedule_poc`, must not open the multi-GB production MCA, and must not open a live Kafka broker or print `KAFKA_CONSUMER_KEY` / `KAFKA_CONSUMER_SECRET`.
