# Code Structure — uk-rail-tracker

## Top-level layout

| Path | Role |
|------|------|
| `android/` | Kotlin `:app` — UI, domain, data, JVM tests |
| `server/poc/schedule/` | Python `schedule_poc` library, demo, pytest. Not opened this run |
| `server/poc/kafka/` | Exploratory Kafka consumers / utils. Not opened this run |
| `server/poc/timetable/` | CIF design notes + sample MCA (no parser package). Not opened this run |
| `server/poc/live-sched/` | **Present** — `live_sched` package, CLI, tests, fixtures, demo, CFA watch folder |
| `server/PLAN.md` + PDFs | Harvest/API design. Not opened this run |
| `docs/`, `README.md` | Product documentation. Not opened this run, except the local-timetable page used as intent context |
| `aidlc/`, `.cursor/` | AI-DLC harness (not product runtime) |

## Android (`android/app/src/main/…`)

Layered packages (sample-confirmed on a prior scan): `ui/navigation`, `domain/session`, `data/local/db`, `data/remote/darwin`, DI via `AppContainer`. Build: Gradle Kotlin DSL, AGP 9.3.3, Gradle 9.5.0; `preBuild` copies `stations.xml` → `assets/stations.json`.

Bulk of `ui/**` / `domain/**` / `data/**` remains prior-scan coverage only (~125 main Kotlin files per prior inventory). This run did not open `android/`.

## Live-sched POC (`server/poc/live-sched/`) — deep (this run)

```
live_sched/     cli, config, cif_loader, cfa_zip, cfa_apply, cfa_report,
                feed_sequence, identity, service_day, db, repository,
                consumer, kafka_classify, apply, csv_archive, change_log,
                docs_gen
tests/          test_loader, test_service_day, test_cfa_zip, test_cfa_apply,
                test_cfa_cli, test_feed_sequence, test_apply, test_kafka_offsets,
                test_config, test_db, test_repository, cfa_fixtures, conftest
fixtures/       lm_tiny.mca, g43975_overlay.mca, kafka_payloads.jsonl
docs/           generated schedule-code summary and algorithm markdown
demo/           run_demo.py (calls live_sched.cli.main)
scripts/        compare_mca_to_db.py (duplicate CIF flush rules)
data/           default CFA watch folder; output SQLite is gitignored
output/         gitignored Kafka CSV and db-changes.md
```

File classification for this run:

| Class | What this run did |
|-------|-------------------|
| Application package | Read the `live_sched` modules named in the tree, plus `pyproject.toml`, `README.md`, and `.gitignore` |
| Tests opened | `conftest.py`, `test_db.py`, `test_feed_sequence.py`, `test_cfa_apply.py`, `test_apply.py` |
| Tests named only | The other modules in `tests/`. `test_service_day.py` was not opened; a prior scan recorded the G43975 case |
| Scripts read | `demo/run_demo.py` (thin wrapper around `live_sched.cli.main`); `scripts/compare_mca_to_db.py` (third CIF copy) |
| Skimmed, not opened | `.venv`, `.pytest_cache`, `fixtures/`, `data/`, `output/`, `docs/` |

Packaging: `pyproject.toml` (`live-sched` 0.1.0, Python ≥3.12, setuptools `setuptools.build_meta`). Console script `live-sched` → `live_sched.cli:main`. Editable install documented as `python3 -m venv .venv` then `.venv/bin/pip install -e ".[dev]"`. Wheel excludes `tests*`, `demo*`, `data*`, `output*`, `fixtures*`, `docs*`. No Makefile, Dockerfile, lockfile, or `requirements.txt` in this tree. The on-disk `.venv` path names Python 3.14; that tree was not inspected beyond the path name.

Import edges recorded on 2026-10-20. They match the modules read on 2026-10-25. This run did not re-trace every import line. No cycles were reported:

```text
cli → cfa_apply, cfa_report, cif_loader, config, consumer, db, docs_gen, repository
cfa_apply → cfa_report, cfa_zip, feed_sequence, identity, repository
cif_loader → identity, repository
consumer → apply, config, csv_archive, kafka_classify, repository
apply → change_log, kafka_classify, repository
repository → service_day
docs_gen → cif_loader.HANDLED_CODES
config → python-dotenv
```

`cfa_zip`, `feed_sequence`, `identity`, `service_day`, `csv_archive`, `change_log`, `cfa_report`, `db`, and `kafka_classify` use the standard library only (plus `sqlite3` where they touch the database). `confluent-kafka` is imported inside `iter_kafka_messages`, so unit tests that inject messages do not import it. Nothing in this tree imports `schedule_poc` or any other workspace package. No in-repo Python file outside `server/poc/live-sched` imports `live_sched` (2026-10-20 statement; this run confirmed the live-sched side of that boundary and did not re-scan the rest of the repo).

`fixtures/g43975_overlay.mca` is named by `tests/test_service_day.py`. This run did not open that test body or the fixture bytes. The prior store records that the fixture is used there. `scripts/compare_mca_to_db.py` was read: it starts a third copy of `_slice`, `_Location`, and `_parse_ti`.

## Schedule POC (`server/poc/schedule/`)

Preserved from the prior store. This run did not open the tree. Live-sched must not import it.

```
schedule_poc/   cif_loader, db, repository, identity, kafka_classify, harvest, replay
demo/           run_demo.py
tests/          test_loader, test_db, test_identity, test_harvest_replay
fixtures/       lm_tiny.mca
```

Packaging: `pyproject.toml` (`schedule-poc` 0.1.0, Python ≥3.12); import via `pythonpath=.` / demo path injection — no wheel publish.

## Kafka POC (`server/poc/kafka/`)

Preserved from the prior store. This run did not open the tree. `live_sched/config.py` names it as the credential-name source, not as an import.

Scripts: `consume.py`, `timestamp-consumer.py`, `config.py`, `consumer_utils.py`, `offset_store.py`; sibling imports, no package. Pyright at `server/pyrightconfig.json` **scoped to kafka only**.

## Patterns

- Android: Compose UI + repository/use-case + Room; ACL for Darwin JSON.
- Schedule: stream CIF parse, repository over sqlite3, classify→harvest→replay for overlays.
- Live-sched: stream CIF parse and repository over sqlite3; CLI subcommands `load`, `cfa-update`, and `consume`; STP winner materialised in `service_day`; Kafka apply writes a CSV row and a markdown change log. Full-load parsing and CFA parsing are separate modules (`cif_loader.py` and `cfa_apply.py`), with a third copy of the flush rules in `scripts/compare_mca_to_db.py`.
- Kafka POC: manual assign + local `.offsets/`.

## Cross-references

- Components → `component-inventory.md`
- Why the parsers are separate → `architecture.md` (decision on independent full-load and CFA paths)
- Quality and the drift risk → `code-quality-assessment.md`
