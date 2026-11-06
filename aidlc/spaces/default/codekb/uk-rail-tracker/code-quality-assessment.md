# Code Quality Assessment — uk-rail-tracker

## Test coverage

| Suite | Location | Framework | Notes |
|-------|----------|-----------|-------|
| Android JVM | `android/app/src/test/` | JUnit 4, coroutines-test, Robolectric | Session/commute/parsers/use cases; ~28 files on the prior inventory. Not opened this run. |
| Android simulator | `android/app/src/testSimulator/` | same family | GPS-gated. Prior scan. |
| Android instrumented | — | disabled (`enableAndroidTest = false`) | Not blocking this POC scan |
| Live-sched POC | `server/poc/live-sched/tests/` | pytest 8 (`>=8.0,<9`) | 45 `def test_` functions across 11 modules. This run did not execute them. |
| Schedule POC | `server/poc/schedule/tests/` | pytest | loader, db, identity, harvest_replay. Not opened this run. |
| Kafka POC | — | none | Manual only. Prior scan. |

**Coverage tooling**: absent (no JaCoCo, Kover, coverage.py, or pytest-cov). No `[tool.coverage]` section and no coverage threshold in `server/poc/live-sched/pyproject.toml`.

### Live-sched tests (this run)

Shared fixtures in `conftest.py`: in-memory SQLite `db_conn` / `repo`, `fixtures/lm_tiny.mca`, `fixtures/kafka_payloads.jsonl`. A third fixture file `fixtures/g43975_overlay.mca` is named by `tests/test_service_day.py`. This run did not open that module body or the fixture bytes. Synthetic HD, CFA, and zip builders live in `tests/cfa_fixtures.py` (imported by the CFA tests that were read). Tests do not open the multi-GB production MCA. No test opens a live broker. The real update-zip demonstration is separate from these synthetic fixtures.

This run opened `test_db.py`, `test_feed_sequence.py`, `test_cfa_apply.py`, and `test_apply.py`. The other seven modules were inventoried by name. The 2026-10-20 store recorded 44 test functions and the lock table below. This run counted 45 `def test_` functions and did not reconcile which function was added.

| Module | What the tests lock |
|--------|---------------------|
| `test_loader.py` | Prior scan, not re-opened: LM filter, guid `SVC-CREBHM2605170925`, progress log, docs markdown |
| `test_service_day.py` | Prior scan, not re-opened: O beats P, C cancels, G43975 overlay versus the later Sunday |
| `test_cfa_zip.py` | Prior scan, not re-opened: lexicographic zips, `.CFA` member, `RJTTC991CFA.txt` among companions, missing member |
| `test_cfa_apply.py` | Re-read this run: accept/refuse, N/R/D, and the member-name accept path (`test_member_name_sequence_when_hd_has_no_digits`). Prior scan also locked seed 990 then accept 991, N duplicate, R override, CRS from stored stops, unmatched and matched D, wrap 999 to 000, empty folder, and refuse-continues |
| `test_cfa_cli.py` | Prior scan, not re-opened: CLI nothing / accepted / refused, zip left on disk, sequence unchanged on refuse |
| `test_feed_sequence.py` | Re-read this run: HD sequence and the member-name fallback. Prior scan also locked seed once, wrap, and refuse a header without digits |
| `test_apply.py` | Re-read this run: TS forecast columns versus booked times, and `deactivated`. Prior scan also locked schedule logged, non-LM ignored, non-apply types ignored, CSV archive, and duration stop |
| `test_kafka_offsets.py` | Prior scan, not re-opened: resume map, next offset after handle, Ctrl+C partial counts, overwrite |
| `test_config.py` | Prior scan, not re-opened: default MCA path, relative `SCHEDULE_DIR` from the workspace root, `--mca` override |
| `test_db.py` | Re-read this run: schema creation. Seven tables. Guid example `SVC-CREBHM2605170925` |
| `test_repository.py` | Prior scan, not re-opened: upsert, replace stops, `known_lm_uids` |

No test this run opened covers XML or Avro, a zip with more than one CFA member, or `applied_updates` when a journey is deleted. The prior scan reported the same three gaps. README command: `.venv/bin/pytest tests/ -q` from the package root. An unused `.venv` listing shows `pytest-8.4.2`; it was not executed.

## Linting and typecheck

- Kotlin official style (`kotlin.code.style=official`); no Detekt or ktlint project configs found on the prior scan
- Pyright: `server/pyrightconfig.json` scoped to `poc/kafka` — schedule and live-sched excluded (prior scan)
- Live-sched: no Ruff, Flake8, Black, or mypy config in this tree. `pyproject.toml` has no lint or format section. Three `# noqa: SLF001` sites on `repo._conn` in `cfa_apply.py` (lines 237, 391, 423). Two `# noqa: E402` sites after a `sys.path` insert in `demo/run_demo.py` and `scripts/compare_mca_to_db.py`

## CI/CD

No workflow, pipeline, or Dockerfile under `server/poc/live-sched`. The prior full scan found no repo CI. Delivery confidence for this package is local pytest only. This run did not execute the suite.

## Documentation quality

Product docs (`docs/`, READMEs) on the prior scan include vision, milestones, and Darwin client KDoc, with a Retrofit-versus-OkHttp drift note. Not re-opened this run, except `docs/future-intents/local-timetable.md` as intent context.

Live-sched `README.md` covers setup, the three commands, default outputs, and the `schedule_poc` boundary. Public functions have docstrings. `docs_gen.py` writes `docs/schedule-code-summary.md` and `docs/schedule-code-algorithms.md` on each load. Generated copies under `docs/` were not opened this run. The checked-in full-MCA counts remain the 2026-10-20 note in `architecture.md`. Naming is consistent (`train_uid`, `service_guid`, `stp_indicator`, `YYMMDD`, `HHMM`). No `TODO`, `FIXME`, or `HACK` comments were found in the Python sources searched. Style observed in the modules read: `from __future__ import annotations`, PEP 604 unions, frozen dataclasses, `logging.getLogger(__name__)`, snake_case, domain exceptions mapped to CLI exit codes.

## Technical debt

### Live-sched (updated 2026-10-25)

The choice to keep full-load and CFA parsing independent is recorded in `architecture.md`. Reader-facing contract gaps are specified once in `api-documentation.md`. What remains:

1. CIF fixed-width parsing is copied. `cif_loader.py` and `cfa_apply.py` each define `_slice`, `_public_time`, `_parse_ti_crs`, `_Location`, and a schedule draft. `scripts/compare_mca_to_db.py` starts a third copy (`_slice`, `_Location`, `_parse_ti`). The full-load parser ignores the BS transaction byte; the CFA parser implements N/R/D. Column indexes are literals, with no shared layout table. Line counts from 2026-10-20 (352, 474) were not re-measured.
2. `cfa_apply.py` uses `ScheduleRepository._conn` for `feed_meta` (three `noqa: SLF001` sites, lines 237, 391, 423). Confirmed this run.
3. `apply.py` `resolve_service_guid`, when a message has a UID and no usable schedule date, scans `list_journeys()` and returns the first UID match. `list_journeys` orders by `service_guid` and has no date predicate. That can attach a `rid` to the wrong schedule when several STP rows share a UID. Confirmed this run.
4. `upsert_journey` on `IntegrityError` calls `rollback()` and forces `_auto_commit = True` (recorded 2026-10-20; not re-opened this run). Separately, this run confirmed that `upsert_journey` on conflict overwrites `status`, and that CFA `R` therefore clears `deactivated` and, through `replace_calling_points`, clears forecasts.
5. `applied_updates` does not cascade. `delete_journey` deletes the journey row only. With foreign keys on, a journey that already has `applied_updates` rows cannot be deleted. CFA `D`, and `R` when the new `service_guid` differs, call `delete_journey`. No test read this run deletes a journey after `record_applied_update`. This replaces the 2026-10-20 wording that left the outcome to foreign-key timing.
6. `--format xml` and `--format avro` select topics and then pass bytes through a JSON decoder. No test in the inventory exercises them. Confirmed this run.
7. `load` unlinks an existing SQLite file, so offsets, feed sequence, aliases, forecasts, and applied updates go with it. `cfa-update` assumes the MCA load has already filled TIPLOC to CRS. There is no schema version; `CREATE TABLE IF NOT EXISTS` will not alter an older file.
8. CFA intake is a one-shot directory listing. Nothing moves or marks a processed zip, so a second run of an accepted sequence refuses it. A refuse does not stop the rest of the lexicographic list.
9. TOC filter `LM` and seed `990` are constants (`LM_TOC`, `DEFAULT_FEED_SEQUENCE_SEED`). `get_feed_sequence` commits that seed when the key is absent. `connect` creates a missing database file. A reader that calls those helpers writes.
10. The 2026-10-20 note that `repository.py` is 442 lines was not re-measured. The checked-in schedule-code summary is a large-load artifact and is not produced by the unit tests. This run did not open that summary.
11. `overlay_calling_point_times` updates one row (`fetchone`) when a TIPLOC repeats. Its arguments are named `public_arr` and `public_dep`; it writes `forecast_arr` and `forecast_dep`.
12. There is no index on `service_day.calendar_date` or `calling_points.crs`, and no repository method for a calendar day or an ordered CRS pair. The local timetable page needs both reads. The contract is in `api-documentation.md`.

### Broader (preserved from the prior store)

1. POC duplication across Schedule, Kafka, and Live-sched stacks
2. No CI or coverage; Pyright does not include schedule or live-sched
3. Ephemeral Darwin `serviceID` is still central on Android; the server `serviceGUID` bridge is unfinished
4. AWS-oriented PLAN versus local SQLite POC reality

## Health summary

| Component | Rating |
|-----------|--------|
| Android Application | healthy |
| Darwin LDB Clients | healthy |
| Live-sched POC | healthy (MCA, CFA, and JSON Kafka paths covered by the unit tests); at-risk (parser copies, UID-wide scan, no CI or coverage gate). This run counted 45 tests and did not execute them. The 2026-10-20 store said 44. |
| Schedule POC | healthy |
| Kafka PubSub POC | at-risk |
| Timetable CIF Notes | degraded |
| Server Harvest Plan | design-only |
| Product Documentation | healthy |

The local timetable server and UI have no health rating. They are not in the tree.
