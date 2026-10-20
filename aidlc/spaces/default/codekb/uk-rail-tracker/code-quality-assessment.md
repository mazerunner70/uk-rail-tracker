# Code Quality Assessment — uk-rail-tracker

## Test coverage

| Suite | Location | Framework | Notes |
|-------|----------|-----------|-------|
| Android JVM | `android/app/src/test/` | JUnit 4, coroutines-test, Robolectric | Session/commute/parsers/use cases; ~28 files on the prior inventory. Not opened this run. |
| Android simulator | `android/app/src/testSimulator/` | same family | GPS-gated. Prior scan. |
| Android instrumented | — | disabled (`enableAndroidTest = false`) | Not blocking this POC scan |
| Live-sched POC | `server/poc/live-sched/tests/` | pytest 8 (`>=8.0,<9`) | 44 test functions. Deep this run. |
| Schedule POC | `server/poc/schedule/tests/` | pytest | loader, db, identity, harvest_replay. Not opened this run. |
| Kafka POC | — | none | Manual only. Prior scan. |

**Coverage tooling**: absent (no JaCoCo, Kover, coverage.py, or pytest-cov). No `[tool.coverage]` section and no coverage threshold in `server/poc/live-sched/pyproject.toml`.

### Live-sched tests (this run)

Shared fixtures in `conftest.py`: in-memory SQLite `repo`, `fixtures/lm_tiny.mca`, `fixtures/kafka_payloads.jsonl`. Synthetic HD, CFA, and zip builders live in `tests/cfa_fixtures.py`. Tests do not open the multi-GB production MCA. No test opens a live broker. `iter_kafka_messages` is untested beyond the pure offset helper. The real update-zip demonstration is separate from these synthetic fixtures.

| Module | What the tests lock |
|--------|---------------------|
| `test_loader.py` | LM filter, guid `SVC-CREBHM2605170925`, progress log, docs markdown |
| `test_service_day.py` | O beats P, C cancels, G43975 overlay versus the later Sunday |
| `test_cfa_zip.py` | Lexicographic zips, `.CFA` member, `RJTTC991CFA.txt` among companions, missing member |
| `test_cfa_apply.py` | Seed 990 then accept 991; refuse leaves rows; N duplicate; R override and a new origin time; CRS from stored stops; unmatched D; matched D; wrap 999 to 000; member-name sequence; empty folder; refuse continues and leaves zips |
| `test_cfa_cli.py` | CLI nothing / accepted / refused, zip left on disk, sequence unchanged on refuse |
| `test_feed_sequence.py` | Seed once, wrap, HD slice, refuse a header without digits, member-name fallback |
| `test_apply.py` | TS forecast columns, booked times unchanged, schedule and deactivated logged, non-LM ignored, non-apply types ignored, CSV archive and duration stop |
| `test_kafka_offsets.py` | Resume map, next offset after handle, Ctrl+C partial counts, overwrite |
| `test_config.py` | Default MCA path, relative `SCHEDULE_DIR` from the workspace root, `--mca` override |
| `test_db.py` | Seven tables exist |
| `test_repository.py` | Upsert, replace stops, `known_lm_uids` |

No test covers XML or Avro, a zip with more than one CFA member, or `applied_updates` when a journey is deleted. README command: `.venv/bin/pytest tests/ -q` from the package root.

## Linting and typecheck

- Kotlin official style (`kotlin.code.style=official`); no Detekt or ktlint project configs found on the prior scan
- Pyright: `server/pyrightconfig.json` scoped to `poc/kafka` — schedule and live-sched excluded (prior scan)
- Live-sched: no Ruff, Flake8, Black, or mypy config in this tree. Three `# noqa: SLF001` on `repo._conn` in `cfa_apply.py`. Two `# noqa: E402` for `sys.path` inserts in `demo/run_demo.py` and `scripts/compare_mca_to_db.py`

## CI/CD

No workflow, pipeline, or Dockerfile under `server/poc/live-sched`. The prior full scan found no repo CI. Delivery confidence for this package is local pytest only.

## Documentation quality

Product docs (`docs/`, READMEs) on the prior scan include vision, milestones, and Darwin client KDoc, with a Retrofit-versus-OkHttp drift note. Not re-opened this run.

Live-sched `README.md` covers setup, env vars, the three commands, outputs, and the resume rule. Public functions have docstrings. `docs_gen.py` embeds the per-code algorithm text and rewrites it on every `load`. The checked-in `docs/schedule-code-summary.md` is a full-MCA artifact, not the tiny fixture; the record counts are in `architecture.md`. Naming is consistent (`train_uid`, `service_guid`, `stp_indicator`, `YYMMDD`, `HHMM`). No TODO, FIXME, or HACK comments were reported.

## Technical debt

### Live-sched (this run)

The choice to keep full-load and CFA parsing independent is recorded in `architecture.md`. What remains:

1. CIF fixed-width parsing is copied in `cif_loader.py` (352 lines), `cfa_apply.py` (474 lines), and `scripts/compare_mca_to_db.py`. The full-load parser ignores the BS transaction byte; the CFA parser implements N/R/D. Column indexes are literals, with no shared layout table.
2. `cfa_apply.py` uses `ScheduleRepository._conn` for `feed_meta` (three `noqa: SLF001` sites). Sequence updates are split between `feed_sequence.set_feed_sequence` (its own commit) and a raw `INSERT` inside the apply batch.
3. `apply.py` `resolve_service_guid` (file 327 lines) falls back to `list_journeys()` and returns the first UID match. That reads every journey and can attach a `rid` to the wrong schedule when several STP rows share a UID. The path is not indexed.
4. `upsert_journey` on `IntegrityError` calls `rollback()` and forces `_auto_commit = True`, which ends a surrounding CFA batch early if a unique-key clash escapes the N/R/D logic.
5. `applied_updates` does not cascade when a journey is deleted. A later delete can fail the foreign key or leave apply history, depending on SQLite foreign-key timing. Not covered by tests.
6. `--format xml` and `--format avro` select topics and then pass bytes through a JSON decoder.
7. `load` unlinks the SQLite file, so offsets, feed sequence, aliases, forecasts, and applied updates go with it. `cfa-update` assumes the MCA load has already filled TIPLOC to CRS. There is no schema version; `CREATE TABLE IF NOT EXISTS` will not alter an older file.
8. CFA intake is a one-shot directory listing. Nothing moves or marks a processed zip, so a second run of an accepted sequence refuses it. A refuse does not stop the rest of the lexicographic list.
9. TOC filter `LM` and seed `990` are constants (`LM_TOC`, `DEFAULT_FEED_SEQUENCE_SEED`).
10. `repository.py` is 442 lines. The checked-in schedule-code summary is a large-load artifact and is not produced by the unit tests.

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
| Live-sched POC | healthy (MCA, CFA, and JSON Kafka paths covered by the 44 unit tests); at-risk (parser copies, UID-wide scan, no CI or coverage gate) |
| Schedule POC | healthy |
| Kafka PubSub POC | at-risk |
| Timetable CIF Notes | degraded |
| Server Harvest Plan | design-only |
| Product Documentation | healthy |
