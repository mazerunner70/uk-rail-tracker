# API Documentation — uk-rail-tracker

## First-party HTTP API

**None.** No `server/api` process and no OpenAPI surface. Live-sched exposes a CLI and a SQLite file. CLI command count: 3 (`load`, `cfa-update`, `consume`). There is no HTTP server today. The local timetable server in `docs/future-intents/local-timetable.md` is not implemented.

## External REST — RDM OpenLDBWS (Android)

Preserved from the prior store. This run did not open the Android clients.

| Client | Method / path | Auth | Notes |
|--------|---------------|------|-------|
| `OpenLdbWsApi.kt` | `GET …/GetArrDepBoardWithDetails/{crs}` | header `x-apikey` | Query: `numRows`, `filterCrs`, `filterType`, `timeOffset`, `timeWindow` |
| `OpenLdbServiceDetailsApi.kt` | `GET …/GetServiceDetails/{serviceId}` | header `x-apikey` | Service detail product |

Transport: OkHttp 4.12.0 (no Retrofit dependency in Gradle).

## External Kafka — RDM Darwin PubSub

Scripts under `server/poc/kafka/` (`consume`, `timestamp-consumer`, metadata helpers) are prior-scan coverage: SASL_SSL/PLAIN; JSON/XML/Avro topics. Credentials via `.env` (gitignored); see `.env.example`. This run did not open that tree.

Live-sched `live-sched consume` uses the same PubSub family via `confluent-kafka`.

| Setting | Contract |
|---------|----------|
| Required env | `KAFKA_BOOTSTRAP_SERVERS`, `KAFKA_CONSUMER_KEY`, `KAFKA_CONSUMER_SECRET`, `KAFKA_GROUP_ID` |
| Optional env | `KAFKA_SSL_CA_LOCATION`, `KAFKA_TOPIC_JSON`, `KAFKA_TOPIC_XML`, `KAFKA_TOPIC_AVRO` |
| Protocol | `SASL_SSL`, mechanism `PLAIN`, `enable.auto.commit=false`, `auto.offset.reset=latest` |
| `--format` | `json` (default), `xml`, or `avro`. Selects which topic env var is required. `kafka_classify.py` parses JSON only. XML and Avro have no parser in this package. |
| Read position | The consumer lists the topic and assigns every partition. It does not subscribe. A partition with a `kafka_offsets` row seeks to `next_offset`. A partition with no row seeks to the live end. |
| Resume write | After the CSV append and apply-or-ignore, stored offset is `message.offset + 1`. Ctrl+C keeps offsets for messages already handled. |
| `load` interaction | Deleting the SQLite file drops `kafka_offsets`, so the next `consume` starts at the live end again. |

Per message, in order: append the CSV row (`received_at`, `offset`, `partition`, `topic`, `payload`), including undecodable text; decode a raw dict, a JSON object, or an envelope whose `bytes` field is a JSON string; classify keys under wrapper `uR` or `sR`. Known types include `schedule`, `deactivated`, `association`, `scheduleFormations`, `TS`, `formationLoading`, `serviceLoading`, `trainOrder`, `OW`, `trainAlert`, `trackingID`, `alarm`, `stationInformation`. The apply set is only `TS`, `schedule`, and `deactivated`.

Journey resolution: `rid` alias in `service_aliases`, else `(uid, ssd)` through `service_day`, else the first `journeys` row with that `train_uid` from a full `list_journeys()` scan. `ssd` of `YYYY-MM-DD` is normalised to `YYMMDD`. There is no separate TOC check on this path; the journey must already be in the store. The full-table fallback is called out in `code-quality-assessment.md`.

`TS` writes `calling_points.forecast_arr` / `forecast_dep` from `at`, then `et`, then `wet` (first four digits) for a matching TIPLOC. Booked `public_arr` / `public_dep` stay. `schedule` does not change timetable rows; the child is stored on `applied_updates`. `deactivated` sets `journeys.status` from its previous value (default `published`) to `deactivated`. `overlay_calling_point_times` looks up `service_guid` and `tiploc` with `fetchone`, so a repeated TIPLOC updates one row. The method arguments are named `public_arr` and `public_dep`; the columns written are `forecast_arr` and `forecast_dep`.

## Library / CLI surface — Live-sched POC (non-HTTP)

Deep this run. Package version `0.1.0`. Command count: 3.

| Command | Behaviour | Exit |
|---------|-----------|------|
| `load` | Resolve the MCA from `--mca` or `SCHEDULE_DIR`/`SCHEDULE_FILE`. Default file is `server/poc/timetable/RJTTF944MCA.txt`. A relative `SCHEDULE_DIR` resolves from the workspace root. Delete the existing SQLite file when the path is an existing file. Stream-parse and persist LM rows. Rewrite `docs/schedule-code-summary.md` and `docs/schedule-code-algorithms.md`. | 0 on success. 1 when the file is missing. |
| `cfa-update` | One-shot scan of `--watch` or `CFA_WATCH_DIR` (default `server/poc/live-sched/data/cfa-inbox`). Does not delete the database. Does not register a filesystem watcher. Prints labeled lines per zip (`zip`, `status`, `sequence`, `expected`, `reason`, and on accept `total`/`new`/`override`/`duplicate`/`deletes`). | 0 when every zip is accepted or the folder is empty (`status: nothing`). 1 when any zip is refused or failed, or the watch folder is missing. |
| `consume` | Load Kafka settings or exit 1. Run for `--duration` seconds (default 3600). Archive every message, apply matching LM updates, append `output/db-changes.md`. | 0 after the duration, an exhausted source, or Ctrl+C. |

Flags confirmed this run: `load` takes `--mca`, `--env`, `--db`, `--docs`. `cfa-update` takes `--db`, `--env`, `--watch`, `--seed`. `consume` takes `--db`, `--env`, `--duration`, `--csv`, `--change-log`, `--format` (`json`, `xml`, `avro`; default `json`). Default database path is `server/poc/live-sched/data/live_sched.sqlite`.

`load` and `cfa-update` are separate paths. A full replace drops `kafka_offsets` and `feed_meta` with the file. A CFA run keeps existing rows and rebuilds `service_day` only for UIDs it touched.

### CFA intake and sequence

Eligible files are `*.zip` in lexicographic order. Non-zips are ignored. Zips stay in the folder after accept, refuse, and failure. Exactly one CFA member is required. Accepted names are `*.CFA`, `*.cfa`, and `*CFA.txt` (for example `RJTTC991CFA.txt`). Companion members such as `*DAT.txt` and `RJTTF991FLF.txt` (also `MSN`, `REJ`, `SET`, `TSI`, `ZTR` in the zip test) are ignored. Zero or more than one CFA member fails that zip. Bytes are decoded as UTF-8 with replacement characters. An HD record is required.

Sequence gate in `feed_sequence.py` and `cfa_apply.py` `apply_cfa_bytes`, before `begin_batch`:

- Stored previous code: `feed_meta.key = 'feed_sequence'`. Empty store seeds `990` (`FEED_SEQUENCE_SEED` or `--seed`, default `990`). `get_feed_sequence` commits that seed when the key is absent.
- Expected code: previous + 1, modulo 1000, zero-padded to three digits (`999` → `000`).
- Actual code: HD Current File Reference, CIF columns 33–39 (0-based slice `[32:39]`). The field must contain a three-digit run. A header shaped like the full MCA `DFTTISD` reference raises. Only then, if a member name was supplied, the code is taken from that member (`RJTTC991CFA.txt` and `RJTTC991.CFA` both yield `991`). The zip basename is ignored. This run confirmed that fallback in `tests/test_cfa_apply.py` (`test_member_name_sequence_when_hd_has_no_digits`, zip name `timetable_update_991.zip`).
- Mismatch raises `ValueError` (`sequence refuse: expected …, got …`) and does not change schedule rows or the stored sequence.
- `process_watch_folder` still attempts later zips after a refuse. A later zip that matches the still-current expected code can be accepted in the same run. Status strings are `accepted`, `refused`, `failed`, `nothing`. `refused` is chosen when the error text contains `sequence refuse` or `HD`.

Per-schedule rules after the gate, inside one deferred batch. A later error rolls the batch back, including the sequence update written in that batch.

- `BS` transaction at `[2:3]`: `N`, `R`, or `D` (default `N`). Other BS fields match the loader: UID `[3:9]`, date from `[9:15]`, date to `[15:21]`, days `[21:28]`, STP `[79:80]` default `P`.
- `BX` TOC at `[11:13]`. Non-delete drafts whose TOC is not `LM` are skipped.
- `D` has no TOC check. A delete counts only when `(train_uid, date_from, stp)` is already in `journeys`.
- `N` when that identity exists increments `duplicate` and does not write. `N` when it is absent inserts and counts as `new`.
- `R` upserts the journey and replaces calling points. If the existing identity's `service_guid` differs, the old guid is deleted first. Counts as `override`. `upsert_journey` overwrites `status`. `_build_journey` leaves `status` at the default `published`. `replace_calling_points` deletes every stop for the guid and inserts the new points with null forecasts. An `R` therefore clears a `deactivated` status and any TS overlay on that journey.
- A draft that cannot form a guid is logged and not counted.
- Origin and destination CRS on a daily file are filled from `calling_points` already stored by the MCA load, then from any TI records if the file contains them. LO/LI/LT lines look up TIPLOC `[2:9]`.

Full-load `HANDLED_CODES` are `HD`, `TI`, `BS`, `BX`, `LO`, `LI`, `LT`, `ZZ`. The full-load path does not read the BS transaction byte and does not persist `feed_sequence`.

### SQLite query contract

`live_sched/db.py` defines 7 tables. `init_schema` is `CREATE TABLE IF NOT EXISTS` plus four indexes: `idx_journeys_uid_date` on `(train_uid, run_date)`, `idx_service_day_guid`, `idx_aliases_guid`, and `idx_calling_guid`. There is no index on `service_day.calendar_date` or `calling_points.crs`. There is no migration history. Foreign keys are on. `calling_points`, `service_day`, and `service_aliases` cascade on journey delete. `applied_updates.service_guid` references `journeys` without `ON DELETE CASCADE`. `delete_journey` deletes the journey row only, so a journey that already has `applied_updates` rows cannot be deleted while foreign keys are on. Dates are `YYMMDD` text. Times are `HHMM` text. `connect` sets `row_factory` to `sqlite3.Row` and `PRAGMA foreign_keys = ON`. `connect` creates a missing database file.

| Lookup | Table | Key |
|--------|-------|-----|
| Journey by stable id | `journeys` | `service_guid` primary key. Form `SVC-{originCrs}{destCrs}{runDateYYMMDD}{originStdHHMM}` from `identity.py`. Example `SVC-CREBHM2605170925` in `tests/test_db.py`. `run_date` is CIF `date_runs_from`, not each operating day. |
| CIF identity | `journeys` | Unique `(train_uid, run_date, stp_indicator)`. Index `(train_uid, run_date)`. |
| What runs on a calendar day | `service_day` | Primary key `(train_uid, calendar_date)` → `service_guid`, or `cancelled=1`. |
| Stops in order | `calling_points` | `(service_guid, seq)`. Booked public times plus nullable forecast columns. `crs` is NULL when the TIPLOC map has no code. `seq` is 0-based LO/LI/LT order. `activity` is `origin`, `call`, `pass`, or `destination`. |
| Darwin running id | `service_aliases` | Primary key `(alias_type, alias_value)`; type `rid`. |
| LM UID set | `journeys` | `SELECT DISTINCT train_uid` where `toc = 'LM'`. |
| Last feed code | `feed_meta` | `key = 'feed_sequence'`. |
| Kafka resume | `kafka_offsets` | Primary key `(topic, partition)` → `next_offset`. |
| Applied live payload | `applied_updates` | Append-only; `payload_json` is the raw child object. |

Read methods on `ScheduleRepository`: `get_journey`, `get_service_day`, `get_journey_by_uid_date`, `get_journey_by_cif_identity`, `list_journeys`, `list_calling_points`, `resolve_alias`, `known_lm_uids`. `list_journeys` orders by `service_guid` and has no date predicate.

`service_day` expands each schedule window (`date_runs_from`..`date_runs_to` where the Monday–Sunday `days_run` bit is set; weekday index 0 is Monday) and picks one winner per `(train_uid, calendar_date)`: `C` cancels the day and stores `service_guid` NULL with `cancelled` 1; else `O`, else `P`, else `N`. Ties on the same STP letter pick the lowest `service_guid`. An unknown STP letter falls through to the lowest guid among remaining candidates. The G43975 case is the prior store's reading of `tests/test_service_day.py`: on `261213` the overlay wins; on the next Sunday `261220` the permanent schedule wins. Both schedule rows stay in `journeys`. This run did not open that test body.

Library entry points used by the CLI and tests: `load_lm_schedules`, `apply_cfa_bytes`, `process_watch_folder`, `ScheduleRepository`, `apply_payload`, `process_message_stream`, `run_live_consumer`, `resolve_schedule_path`, `load_kafka_settings`.

### Gaps a local reader would meet

These are the contract holes between this CLI and the three views in `docs/future-intents/local-timetable.md`. They are not HTTP routes.

- No method lists every service for one calendar date. "Today" is `service_day.calendar_date`, not `journeys.run_date`.
- No method finds a journey by an ordered pair of CRS codes. `origin_crs` and `dest_crs` are the whole-journey ends.
- No index supports "all rows for this calendar date" or "stops with this CRS".
- `connect` creates a missing file. `init_schema` is additive only. `get_feed_sequence` commits seed `990` when `feed_meta` is empty. A reader that calls those helpers writes.
- Two different flags mean "not running as planned": `service_day.cancelled` (STP cancellation, often with a null guid) and `journeys.status = 'deactivated'` (live overlay).
- CFA `R` resets `status` to `published` and drops forecast times. `load` deletes the file.
- A later CFA `D` or guid-changing `R` can fail while foreign keys are on if `applied_updates` still points at the journey.
- The UID-wide `list_journeys()` fallback can attach a live message to another schedule with the same UID.
- `feed_meta.feed_sequence` is the last accepted code. That code can come from the inner member name when the HD field has no digits.

## Library surface — Schedule POC (non-HTTP)

Preserved from the prior store. This run did not open the package. Live-sched must not import it.

| Entry | Role |
|-------|------|
| `load_lm_schedules` | Stream-parse LM CIF MCA → SQLite |
| `harvest_lm_events` / `replay_records` | Kafka overlay apply |
| `ScheduleRepository` + sqlite schema | `journeys`, `calling_points`, `applied_updates`, `service_aliases` |
| `identity` | `serviceGUID` form `SVC-{origin}{dest}{YYMMDD}{HHMM}` |

## Planned REST (not implemented)

From `server/PLAN.md` §8: `/v1/stations`, `/boards/{crs}`, `/services/{serviceGUID}`, `/services/{serviceGUID}/live`, `/routes/...`, `/services/resolve`, `/health`.

The local timetable page does not add these routes. It describes a later local reader of the SQLite file.

## Cross-references

- Components → `component-inventory.md`
- Transaction flows and the decisions behind this contract → `architecture.md`
- Scale of a full MCA pass → `architecture.md` (Data Flow)
