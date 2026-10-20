## Developer Code Scan Results

Focused brownfield scan of the live schedule proof of concept. Snapshot paths: `server/poc/live-sched`. Store generation `sha256:6fb75870d12002f8221c65744946615f4ccc2f3c449063f98f801649422da99e`. Source fingerprint `git:30d3727199bc6b32fb29187ac5e6e9317229febc`. The workflow language list records TypeScript; this tree is Python 3.12.

The package is a local CLI and library. It does not import `schedule_poc`. It turns a Network Rail CIF timetable into a small SQLite plan, then overlays Darwin Push Port JSON. There is no HTTP server, no AWS SDK, and no object-store client.

### Scan Coverage

- **Analyzed deeply**:
  - `server/poc/live-sched/`
  - `server/poc/live-sched/pyproject.toml`
  - `server/poc/live-sched/README.md`
  - `server/poc/live-sched/.gitignore`
  - `server/poc/live-sched/live_sched/`
  - `server/poc/live-sched/tests/`
  - `server/poc/live-sched/fixtures/lm_tiny.mca`
  - `server/poc/live-sched/fixtures/kafka_payloads.jsonl`
  - `server/poc/live-sched/fixtures/g43975_overlay.mca` (exercised through `tests/test_service_day.py`; the fixture file itself was not opened line by line)
  - `server/poc/live-sched/docs/schedule-code-summary.md`
  - `server/poc/live-sched/docs/schedule-code-algorithms.md` (algorithm text is the constant in `live_sched/docs_gen.py`)
  - `server/poc/live-sched/demo/run_demo.py`
  - `server/poc/live-sched/scripts/compare_mca_to_db.py` (module docstring and the duplicated CIF flush rules; the print/compare tail was not read line by line)
- **Skimmed only**:
  - `server/poc/timetable/DATA-MODEL.md` (opening sections only: plan versus live layers, CIF identity, STP winner, and the G43975 example the loader implements)
  - `server/poc/live-sched/live_sched.egg-info/` (generated setuptools metadata; `requires.txt` and `entry_points.txt` match `pyproject.toml`)
  - `server/poc/kafka/` and `server/poc/schedule/` were not opened. `live_sched/config.py` names them as the credential-name source and as a package this tree must not import. No in-repo Python file outside `server/poc/live-sched` imports `live_sched`.

### Packages Found

- `live-sched` / import name `live_sched` — application library plus CLI — Python 3.12 — London Midland timetable load, CFA daily-update apply, and a bounded Kafka consumer. Version `0.1.0`. Console script `live-sched` → `live_sched.cli:main`.
- `tests` — test package — Python — pytest suite and synthetic CIF/zip builders. Not installed (`pyproject.toml` excludes `tests*`).
- `demo` — script — Python — thin wrapper that calls `live_sched.cli.main`. Excluded from the distribution.
- `scripts` — operator script — Python — recounts an MCA with the loader's persist rules and compares totals to SQLite. Excluded from the distribution.
- `fixtures`, `docs`, `data`, `output` — data and generated output — excluded from the distribution. `data/`, `output/`, and `docs/` also contain `.gitkeep` placeholders. SQLite, CSV, and `output/db-changes.md` are gitignored.

File classification inside `live_sched/`:

| File | Role | Purpose |
|------|------|---------|
| `cli.py` | Controller | Subcommands `load`, `cfa-update`, `consume` |
| `config.py` | Configuration | Workspace-root `.env`, path defaults, Kafka SASL settings |
| `cif_loader.py` | Service | Stream-parse a full MCA; persist LM schedules |
| `cfa_zip.py` | Utility | List zips and extract the single CFA member |
| `cfa_apply.py` | Service | Sequence gate and N/R/D apply |
| `cfa_report.py` | Utility | CLI outcome lines and counters |
| `feed_sequence.py` | Utility | 3-digit feed code parse and store |
| `identity.py` | Model | `service_guid` and HHMM normalisation |
| `service_day.py` | Service | STP winner and calendar expansion |
| `db.py` | Model | SQLite DDL |
| `repository.py` | Repository | Journeys, stops, service days, aliases, offsets, applied updates |
| `consumer.py` | Service | Poll loop, CSV archive, offset resume |
| `kafka_classify.py` | Utility | Push Port JSON decode and type extract |
| `apply.py` | Service | Apply `TS`, `schedule`, `deactivated` |
| `csv_archive.py` | Repository | Append-only Kafka CSV |
| `change_log.py` | Utility | Markdown log of applied DB changes |
| `docs_gen.py` | Utility | Schedule-code summary and algorithm markdown |

Internal import edges (no cycles):

- `cli` → `cfa_apply`, `cfa_report`, `cif_loader`, `config`, `consumer`, `db`, `docs_gen`, `repository`
- `cfa_apply` → `cfa_report`, `cfa_zip`, `feed_sequence`, `identity`, `repository`
- `cif_loader` → `identity`, `repository`
- `consumer` → `apply`, `config`, `csv_archive`, `kafka_classify`, `repository`
- `apply` → `change_log`, `kafka_classify`, `repository`
- `repository` → `service_day`
- `docs_gen` → `cif_loader.HANDLED_CODES`
- `config` → `python-dotenv`
- `cfa_zip`, `feed_sequence`, `identity`, `service_day`, `csv_archive`, `change_log`, `cfa_report`, `db`, `kafka_classify` use the standard library only (plus `sqlite3` where they touch the database)

### Build System

- **Type**: setuptools (`setuptools.build_meta`, `setuptools>=68`). Editable install documented as `python3 -m venv .venv` then `.venv/bin/pip install -e ".[dev]"`. No Makefile, Dockerfile, lockfile, or `requirements.txt` inside this tree.
- **Config Files**: `server/poc/live-sched/pyproject.toml` (project metadata, console script, package find, pytest `testpaths` and `pythonpath`). `requires-python = ">=3.12"`.
- **Build Dependencies**: the distribution depends only on `python-dotenv` and `confluent-kafka`. Tests depend on `pytest`. Application code uses the stdlib for SQLite, zip, CSV, JSON, and argparse. Nothing in this tree depends on another workspace package.

### APIs Discovered

- CLI — `live_sched/cli.py` — 3 subcommands. No HTTP, GraphQL, or gRPC surface.
- Kafka consumer — `live_sched/consumer.py` `iter_kafka_messages` — one topic per run, manual partition assign, poll loop. Not a server endpoint.
- SQLite — `live_sched/db.py` — 7 tables. This is the query contract the rest of the package uses.

#### CLI contract

| Command | Behaviour | Success |
|---------|-----------|---------|
| `load` | Resolve MCA from `--mca` or `SCHEDULE_DIR`/`SCHEDULE_FILE`. Delete the existing SQLite file when the path is not `:memory:`. Stream-parse, write LM rows, rewrite `docs/schedule-code-summary.md` and `docs/schedule-code-algorithms.md`. | Exit 0. Missing file: exit 1. |
| `cfa-update` | One-shot scan of `--watch` or `CFA_WATCH_DIR`. Does not delete the database. Prints labeled lines per zip (`zip`, `status`, `sequence`, `expected`, `reason`, and on accept `total`/`new`/`override`/`duplicate`/`deletes`). | Exit 0 when every zip is accepted or the folder is empty (`status: nothing`). Exit 1 when any zip is refused or failed, or the watch folder is missing. |
| `consume` | Load Kafka settings or exit 1. Run for `--duration` seconds (default 3600). Archive every message, apply matching LM updates, append `output/db-changes.md`. | Exit 0 after duration, exhausted source, or Ctrl+C. |

`load` and `cfa-update` are separate paths. A full MCA replace drops `kafka_offsets` and `feed_meta` with the file. A CFA run keeps existing rows and only rebuilds `service_day` for UIDs it touched.

#### How a timetable file is received

Full timetable (`load`):

- Default file is `server/poc/timetable/RJTTF944MCA.txt` (`SCHEDULE_DIR` + `SCHEDULE_FILE` in the workspace-root `.env`). A relative `SCHEDULE_DIR` resolves from the workspace root (`config.py` `resolve_schedule_path`). `--mca` overrides both variables.
- The file is streamed line by line in binary mode (`cif_loader.py` `load_lm_schedules`). It is not copied into an inbox and not validated as a zip.
- Record codes counted include every two-letter prefix. Codes applied are `HD`, `TI`, `BS`, `BX`, `LO`, `LI`, `LT`, `ZZ` (`HANDLED_CODES`). A generated summary already in `docs/schedule-code-summary.md` shows one full MCA pass: 15,724 LM journeys and 9,064,174 records, of which `LI` is 7,102,336. `AA` (6,344) and `CR` (97,900) were counted and not applied.
- Only TOC `LM` is persisted. The tiny fixture `fixtures/lm_tiny.mca` contains an LM journey `C16922` and a non-LM `Y99999` that the loader drops (`tests/test_loader.py`).
- The BS transaction byte is not read on the full-load path. `cif_loader` stores each LM draft that has a UID, locations, origin and destination CRS, and an origin public departure.

Daily CFA (`cfa-update`):

- Watch folder default `server/poc/live-sched/data/cfa-inbox` (`CFA_WATCH_DIR`). The command scans once. It does not register a filesystem watcher.
- Eligible files are `*.zip` in lexicographic order (`cfa_zip.py` `list_eligible_zips`). Non-zips are ignored. Zips stay in the folder after accept, refuse, and failure (`tests/test_cfa_cli.py`, `tests/test_cfa_apply.py`).
- Exactly one CFA member is required. Accepted names are `*.CFA` / `*.cfa` and `*CFA.txt` (for example `RJTTC991CFA.txt`). Companion members such as `*DAT.txt` and `RJTTF991FLF.txt` (also `MSN`, `REJ`, `SET`, `TSI`, `ZTR` in the zip test) are ignored. Zero or more than one CFA member fails that zip.
- Bytes are decoded as UTF-8 with replacement characters. An HD record is required.

#### How a CFA is validated

Sequence gate in `feed_sequence.py` and `cfa_apply.py` `apply_cfa_bytes`, before `begin_batch`:

- Stored previous code lives in `feed_meta.key = 'feed_sequence'`. Empty store seeds `990` (`FEED_SEQUENCE_SEED` or `--seed`, default `990`).
- Expected code is previous + 1, modulo 1000, zero-padded to three digits (`999` → `000`).
- Actual code comes from the HD Current File Reference, CIF columns 33–39 (0-based slice `[32:39]`). The field must contain a three-digit run. A header shaped like the full MCA `DFTTISD` reference raises. Only then, if a member name was supplied, the code is taken from that member (`RJTTC991CFA.txt` and `RJTTC991.CFA` both yield `991`). The zip basename is not used.
- Mismatch raises `ValueError` (`sequence refuse: expected …, got …`) and does not change schedule rows or the stored sequence (`tests/test_cfa_apply.py` `test_refuse_wrong_sequence_leaves_db`).
- `process_watch_folder` still attempts later zips after a refuse. A later zip that matches the still-current expected code can be accepted in the same run. Exit code is 1 if any zip failed. Status strings are `accepted`, `refused`, `failed`, `nothing`. `refused` is chosen when the error text contains `sequence refuse` or `HD`.

Per-schedule rules after the gate, inside one deferred SQLite batch. Any later error rolls the batch back, including the sequence update that is written in that same batch.

- `BS` transaction at `[2:3]`: `N`, `R`, or `D` (default `N`). Other BS fields match the loader: UID `[3:9]`, date from `[9:15]`, date to `[15:21]`, days `[21:28]`, STP `[79:80]` default `P`.
- `BX` TOC at `[11:13]`. Non-delete drafts whose TOC is not `LM` are skipped.
- `D` has no TOC check. A delete counts only when `(train_uid, date_from, stp)` is already in `journeys`. An unmatched delete increments nothing.
- `N` when that CIF identity already exists increments `duplicate` and does not write.
- `R` upserts the journey and replaces calling points. If the existing identity's `service_guid` differs (origin CRS, dest CRS, start date, or origin public time changed), the old guid is deleted first. Counts as `override`.
- `N` when the identity is absent inserts and counts as `new`.
- A draft that cannot form a guid (missing CRS or origin departure) is logged and not counted.
- `service_day` is rebuilt only for UIDs whose row was inserted, replaced, or deleted.
- The accepted sequence is written in the same transaction.

CRS on a daily file: comments and `test_new_resolves_crs_from_stored_calling_points` state that a daily CFA has schedule records and no TI extract. Origin and destination CRS are filled from `calling_points` already stored by the MCA load, then from any TI records if the file contains them. LO/LI/LT lines do not carry CRS; they look up TIPLOC `[2:9]`.

#### How Kafka messages are handled

`consume` → `run_live_consumer` → `iter_kafka_messages` + `process_message_stream`.

Connection (`config.py` `KafkaSettings.consumer_config`):

- Required env: `KAFKA_BOOTSTRAP_SERVERS`, `KAFKA_CONSUMER_KEY`, `KAFKA_CONSUMER_SECRET`, `KAFKA_GROUP_ID`. Optional: `KAFKA_SSL_CA_LOCATION`, `KAFKA_TOPIC_JSON`, `KAFKA_TOPIC_XML`, `KAFKA_TOPIC_AVRO`.
- Protocol `SASL_SSL`, mechanism `PLAIN`, `enable.auto.commit=false`, `auto.offset.reset=latest`.
- CLI `--format` is `json` (default), `xml`, or `avro`, and selects which topic env var is required. The decoder in `kafka_classify.py` parses JSON only. XML and Avro have no parser in this package.

Read position:

- The consumer lists the topic and assigns every partition itself. It does not use subscribe.
- A partition with a `kafka_offsets` row seeks to `next_offset`. A partition with no row seeks to `OFFSET_END` (live tail).
- After CSV append and apply (or ignore), the next offset stored is `message.offset + 1`. Ctrl+C still keeps the offset of messages already handled.
- `load` deletes the database file, so the next `consume` starts at the live end again. README states that explicitly.

Per message, in order:

1. Append a CSV row to `output/kafka-messages.csv` with header `received_at, offset, partition, topic, payload`. Undecodable text is still archived.
2. `decode_kafka_value`: raw dict, JSON object, or an envelope whose `bytes` field is a JSON string. JSON parse failure returns None and the message is ignored after the CSV write.
3. Classify keys under wrapper `uR` or `sR`. Known types: `schedule`, `deactivated`, `association`, `scheduleFormations`, `TS`, `formationLoading`, `serviceLoading`, `trainOrder`, `OW`, `trainAlert`, `trackingID`, `alarm`, `stationInformation`.
4. Apply set is only `TS`, `schedule`, `deactivated`. Other known types are logged and counted as ignored.
5. Resolve a child to a journey: `rid` alias in `service_aliases`, else `(uid, ssd)` through `service_day`, else the first `journeys` row with that `train_uid` (full `list_journeys()` scan). `ssd` of `YYYY-MM-DD` is normalised to `YYMMDD`. A resolved `rid` is stored as an alias.
6. There is no separate TOC check on the Kafka path. Matching is "this journey is already in the LM store".
7. `TS`: for each `Location.tpl` / `tiploc`, take forecast time from `arr`/`dep` keys `at`, then `et`, then `wet`, first four digits. Write `calling_points.forecast_arr` / `forecast_dep`. Booked `public_arr` / `public_dep` stay as loaded (`tests/test_apply.py`). A stop not on the journey is reported and skipped.
8. `schedule`: does not change timetable rows. The child is stored on `applied_updates` and described in the change log, including a short location list.
9. `deactivated`: sets `journeys.status` from its previous value (default `published`) to `deactivated`.
10. Each successful apply inserts `applied_updates` (`update_type`, `rid`, full JSON `payload_json`, `applied_at`, `source_offset`) and appends a markdown section to `output/db-changes.md`.

Fixture shape in `fixtures/kafka_payloads.jsonl`: Darwin-style objects with `ts`, `version`, and `uR.TS` / `uR.schedule` / `uR.deactivated`, identity `uid` + `ssd` + `rid`, locations with `tpl` and forecast `et`/`at`.

#### SQLite query shapes the code actually uses

These are the lookups to preserve when choosing a cheap analytical layout. Dates are `YYMMDD` text. Times are `HHMM` text.

| Lookup | Table | Key |
|--------|-------|-----|
| Journey by stable id | `journeys` | `service_guid` PK. Form `SVC-{originCrs}{destCrs}{runDateYYMMDD}{originStdHHMM}` from `identity.py`. `run_date` is CIF `date_runs_from`, not each operating day. |
| CIF identity | `journeys` | Unique `(train_uid, run_date, stp_indicator)`. Index `(train_uid, run_date)`. |
| What runs on a calendar day | `service_day` | PK `(train_uid, calendar_date)` → `service_guid` or `cancelled=1`. |
| Stops in order | `calling_points` | `(service_guid, seq)`. Booked public times plus nullable forecast columns. |
| Darwin running id | `service_aliases` | PK `(alias_type, alias_value)`; type `rid`. |
| LM UID set | `journeys` | `SELECT DISTINCT train_uid WHERE toc = 'LM'`. |
| Last feed code | `feed_meta` | `key = 'feed_sequence'`. |
| Kafka resume | `kafka_offsets` | PK `(topic, partition)` → `next_offset`. |
| Applied live payload | `applied_updates` | Append-only; `payload_json` is the raw child object. |

`service_day` is built by expanding each schedule window (`date_runs_from`..`date_runs_to` where the Monday–Sunday `days_run` bit is set) and picking one winner per `(train_uid, calendar_date)` (`service_day.py`, RSPS5046 §5.5.2 as coded):

- `C` cancels the day (`service_guid` null, `cancelled` true).
- Else `O`, else `P`, else `N`.
- Ties on the same STP letter pick the lowest `service_guid`.
- An unknown STP letter falls through to the lowest guid among remaining candidates.

`tests/test_service_day.py` locks the G43975 case: permanent `SVC-EUSBHM2612131222` (12:22, STP `P`, window through `270509`, Sundays) and overlay `SVC-EUSBHM2612131223` (12:23, STP `O`, one day). On `261213` the overlay wins. On the next Sunday `261220` the permanent schedule wins. Both schedule rows stay in `journeys`.

Schema notes: `init_schema` is `CREATE TABLE IF NOT EXISTS` plus four indexes. There is no migration history. Foreign keys are on. `calling_points`, `service_day`, and `service_aliases` cascade on journey delete. `applied_updates.service_guid` references `journeys` without `ON DELETE CASCADE`.

#### Transformation already performed (facts for a later lake design)

The package already separates raw intake from the tables it queries:

- Raw full file: the MCA path on disk. Parsed once. The SQLite file is replaced.
- Raw daily file: the zip left in the watch folder. Parsed once. Applied as N/R/D against the existing plan.
- Raw live feed: every Kafka value appended to CSV, including messages that do not apply.
- Queryable plan: one row per CIF schedule (`journeys`) plus ordered booked stops (`calling_points`). The calendar question is a second table (`service_day`), so a "what runs on this date" read does not scan every window and re-evaluate `days_run` and STP.
- Queryable live state: forecast columns on the stop, `journeys.status`, `rid` alias, and `applied_updates`. `schedule` messages are stored as JSON and do not rewrite booked times. `TS` writes forecast columns only.

Scale signal from the checked-in summary of a real MCA load: about 15.7k LM journeys against 7.1M intermediate-location records in the raw file, and about 439k basic schedules in the full file before the LM filter. Association (`AA`) and `CR` records are visible in the raw file and absent from the tables.

The UID-only fallback in `apply.py` `resolve_service_guid` scans every journey when `(uid, ssd)` misses `service_day`. That path is present; it is not indexed.

### Frameworks & Libraries

- Python — `>=3.12` — runtime.
- setuptools — `>=68` — build.
- python-dotenv — `>=1.0,<2` — `.env` load (`override=False`).
- confluent-kafka — `>=2.3,<3` — consumer client. Imported inside `iter_kafka_messages`, so unit tests that inject messages do not import it.
- pytest — `>=8.0,<9` — dev extra only.
- sqlite3, zipfile, csv, argparse, json, dataclasses, logging — stdlib.

No web framework, ORM, AWS SDK, Glue, or Athena client.

### Test Coverage

- **Test Directories**: `server/poc/live-sched/tests/` (44 test functions). Shared fixtures in `conftest.py`: in-memory SQLite `repo`, `fixtures/lm_tiny.mca`, `fixtures/kafka_payloads.jsonl`. Synthetic HD/CFA/zip builders in `tests/cfa_fixtures.py`. Tests do not open the multi-GB production MCA.
- **Test Frameworks**: pytest 8. `testpaths = ["tests"]`, `pythonpath = ["."]`. Run command in the README: `.venv/bin/pytest tests/ -q` from the package root.
- **Coverage Config**: absent. No `pytest-cov`, no coverage threshold, no `[tool.coverage]` section.

| Module | What the tests lock |
|--------|---------------------|
| `test_loader.py` | LM filter, guid `SVC-CREBHM2605170925`, progress log, docs markdown |
| `test_service_day.py` | O beats P, C cancels, G43975 overlay versus later Sunday |
| `test_cfa_zip.py` | Lexicographic zips, `.CFA` member, `RJTTC991CFA.txt` among companions, missing member |
| `test_cfa_apply.py` | Seed 990 then accept 991; refuse leaves rows; N duplicate; R override and new origin time; CRS from stored stops; unmatched D; matched D; wrap 999→000; member-name sequence; empty folder; refuse continues and leaves zips |
| `test_cfa_cli.py` | CLI nothing / accepted / refused, zip left on disk, sequence unchanged on refuse |
| `test_feed_sequence.py` | Seed once, wrap, HD slice, refuse header without digits, member-name fallback |
| `test_apply.py` | TS forecast columns, booked times unchanged, schedule and deactivated logged, non-LM ignored, non-apply types ignored, CSV archive and duration stop |
| `test_kafka_offsets.py` | Resume map, next offset after handle, Ctrl+C partial counts, overwrite |
| `test_config.py` | Default MCA path, relative `SCHEDULE_DIR` from workspace root, `--mca` override |
| `test_db.py` | Seven tables exist |
| `test_repository.py` | Upsert, replace stops, `known_lm_uids` |

No test opens a live broker. `iter_kafka_messages` is untested beyond the pure offset helper. No test covers XML/Avro, multiple CFA members, or `applied_updates` delete behaviour when a journey is removed.

### Code Quality Indicators

- **Linting**: no Ruff, Flake8, Black, or mypy config in this tree. Three `# noqa: SLF001` on `repo._conn` in `cfa_apply.py`. Two `# noqa: E402` for sys.path inserts in `demo/run_demo.py` and `scripts/compare_mca_to_db.py`.
- **CI/CD**: no workflow, pipeline, or Dockerfile under `server/poc/live-sched`.
- **Documentation**: `README.md` covers setup, env vars, the three commands, outputs, and the resume rule. Public functions have docstrings. `docs_gen.py` embeds the per-code algorithm text and rewrites it on every `load`. The checked-in `docs/schedule-code-summary.md` is output from a full MCA (15,724 journeys), not from the tiny fixture. Naming is consistent (`train_uid`, `service_guid`, `stp_indicator`, YYMMDD, HHMM).

Quick assessment: naming good; error handling good on the sequence gate, zip membership, and batch rollback; tests good for the PoC paths and silent on coverage; function size fair (`cfa_apply.py` 474 lines, `repository.py` 442, `cif_loader.py` 352, `apply.py` 327); duplication poor across the three CIF parsers.

### Technical Debt Signals

- CIF fixed-width parsing is copied three times: `cif_loader.py`, `cfa_apply.py`, and `scripts/compare_mca_to_db.py` (`_slice`, `_public_time` / `_public_dep`, TI CRS columns, LO/LI/LT). Full load and daily apply can drift. The full-load parser ignores the BS transaction byte; the CFA parser implements N/R/D.
- `cfa_apply.py` reaches through `ScheduleRepository._conn` for `feed_meta` (three `noqa: SLF001` sites). Sequence updates are split between `feed_sequence.set_feed_sequence` (own commit) and a raw `INSERT` inside the apply batch.
- `apply.py` `resolve_service_guid` falls back to `list_journeys()` and returns the first UID match. That is a full-table scan and can bind a `rid` to the wrong schedule when several STP rows share a UID.
- `upsert_journey` on `IntegrityError` calls `rollback()` and forces `_auto_commit = True`, which ends a surrounding CFA batch early if a unique-key clash escapes the N/R/D logic.
- `applied_updates` does not cascade when a journey is deleted, so a later delete can fail the foreign key or leave the apply history behind depending on SQLite FK timing. Not covered by tests.
- `--format xml` and `--format avro` select topics and then pass bytes through a JSON decoder.
- `load` unlinks the SQLite file. Offsets, feed sequence, aliases, forecasts, and applied updates go with it. `cfa-update` assumes the MCA load has already filled TIPLOC→CRS.
- CFA intake is a one-shot directory listing. Nothing moves or marks a processed zip, so a second run of an accepted sequence refuses it.
- A refuse does not stop the rest of the lexicographic list.
- TOC filter `LM` and seed `990` are constants (`LM_TOC`, `DEFAULT_FEED_SEQUENCE_SEED`).
- Column indexes are magic numbers with no shared layout table.
- No schema version. `CREATE TABLE IF NOT EXISTS` will not alter an older file; `load` avoids that by deleting the file, `cfa-update` does not.
- `docs/schedule-code-summary.md` in the tree is a large-load artifact (9,064,174 records). It is not produced by the unit tests.
- No TODO/FIXME/HACK comments. No linter, coverage gate, or CI for this package.

## Handoff Summary

- **Intent-relevant finding**: The proof of concept already splits raw intake from the tables it queries. A full MCA is streamed from a configured path and replaces SQLite (`cli.py` `cmd_load`, `cif_loader.py` `load_lm_schedules`). A CFA arrives as a zip in a watch folder, is accepted only when its three-digit sequence is the stored code plus one (HD columns 33–39, else the CFA member name, never the zip name), and applies LM `N`/`R`/`D` in one transaction (`cfa_zip.py`, `feed_sequence.py`, `cfa_apply.py` `apply_cfa_bytes`). Every Kafka message is appended to CSV first; only `TS`, `schedule`, and `deactivated` that resolve to an existing journey mutate live columns, and `TS` writes forecast times while booked public times stay (`consumer.py`, `csv_archive.py`, `apply.py`). The cheap reads are `journeys` by CIF identity `(train_uid, date_runs_from, stp)`, `calling_points` by `(service_guid, seq)`, and `service_day` by `(train_uid, calendar_date)` after STP resolution (`C` cancels, else `O`, else `P`, else `N`). A checked-in full-file summary shows why the raw CIF is the expensive scan: 7,102,336 `LI` records and 15,724 persisted LM journeys. Dates and times are `YYMMDD` and `HHMM` text. There is no S3, Glue, or Athena code in this tree.
- **Risks / follow-up**: Keep full-load and CFA parsing from diverging (three copies today, and the full load ignores the BS transaction byte). A daily CFA depends on CRS already stored by the MCA. `load` wipes offsets and feed sequence. Zips are left in place and a re-run refuses an already applied sequence. Kafka XML/Avro are topic switches without parsers. The UID-wide journey scan in `resolve_service_guid` is unsafe to copy as an access pattern. `server/poc/timetable/DATA-MODEL.md` describes the same plan/live split and was only skimmed; `server/poc/kafka/` and `server/poc/schedule/` were not opened. The recorded workflow language is TypeScript; this package is Python.
