# Code Summary — Live Schedule POC

## Files created

Under `server/poc/live-sched/`:

- `live_sched/` — `db.py`, `repository.py`, `identity.py`, `cif_loader.py`, `docs_gen.py`, `kafka_classify.py`, `apply.py`, `change_log.py`, `csv_archive.py`, `config.py`, `consumer.py`, `cli.py`
- `tests/` — `test_db.py`, `test_repository.py`, `test_loader.py`, `test_apply.py`, `conftest.py`
- `fixtures/lm_tiny.mca`, `fixtures/kafka_payloads.jsonl`
- `demo/run_demo.py`, `README.md`, `pyproject.toml`, `.gitignore`
- `docs/`, `output/`, `data/` placeholders

Workspace root: `.env.example` (Kafka key names matching Kafka POC).

Record artifacts: `source-manifest.json`, `traceability.json`, plan checkboxes ticked.

## Key decisions

- **Separate package**: no `schedule_poc` import; CIF parse, classify, and apply reimplemented under `live_sched`.
- **LM-only SQLite**: schema mirrors Schedule POC journeys / calling points / applied_updates / aliases; default `data/live_sched.sqlite`.
- **Docs**: `load` writes `docs/schedule-code-summary.md` (counts) and `docs/schedule-code-algorithms.md` (per-code algorithms).
- **Kafka**: credentials from workspace-root `.env`; consumer default **3600s**; every message → `output/kafka-messages.csv`; apply `TS` / `schedule` / `deactivated` for matching LM journeys; each DB change → couple-line entry in `output/db-changes.md`; other types ignored without failing. `--duration` for dry-runs.
- **CLI**: `live-sched load` and `live-sched consume`.

## Tests

Exact unit-scoped command (from `unit-test-instructions.md`):

```bash
cd /home/william/code/personal/2025/uk-rail-tracker/server/poc/live-sched
python3 -m venv .venv
.venv/bin/pip install -e ".[dev]"
.venv/bin/pytest tests/ -q
```

**Result (parent session)**: `7 passed in 0.05s` after fixing `pyproject.toml` package discovery (`tool.setuptools.packages.find` include `live_sched*` only).

## Deviations

- API/Frontend layers omitted per plan scope notes.
- Venv creation and pytest completed in the parent session (delegated-agent interpreter restriction).
- `pyproject.toml` needed an explicit setuptools package find list so editable install would not treat `demo`/`data`/`output`/`fixtures` as packages.
- Live 1h Kafka path is CLI/ops; unit tests use fixture payloads and injectable clock/duration only.
