# Build Instructions — Live Schedule POC

## Prerequisites

- Python ≥ 3.12
- Network only for the initial `pip install` into the local venv
- Optional: workspace-root `.env` with Kafka keys for the live `consume` demo (not required for unit tests)

## Dependency installation

```bash
cd /home/william/code/personal/2025/uk-rail-tracker/server/poc/live-sched
python3 -m venv .venv
.venv/bin/pip install -e ".[dev]"
```

If `[dev]` extras are missing, use:

```bash
.venv/bin/pip install -e . pytest
```

Do **not** install into system Python.

## Environment setup

| Item | Path / notes |
|------|----------------|
| Package root | `server/poc/live-sched/` |
| Default SQLite | `server/poc/live-sched/data/live_sched.sqlite` (created on load) |
| Docs output | `server/poc/live-sched/docs/` |
| Kafka CSV / change log | `server/poc/live-sched/output/` |
| Live Kafka credentials | Workspace-root `.env` (gitignored); names in root `.env.example` |
| MCA source (ops) | `server/poc/timetable/RJTTF944MCA.txt` — not required for unit tests |

## Build / install verification

```bash
cd /home/william/code/personal/2025/uk-rail-tracker/server/poc/live-sched
.venv/bin/python -c "import live_sched; print('import_ok')"
.venv/bin/live-sched --help
```

Expected: `import_ok` and CLI help listing `load` / `consume`.

## Ops commands (not part of the gated unit suite)

```bash
# LM load from MCA (full file is large; fixture path for smoke)
.venv/bin/live-sched load --mca fixtures/lm_tiny.mca

# Live consume (requires root .env); use short duration for dry-run
.venv/bin/live-sched consume --duration 10
```

## Troubleshooting

| Symptom | Likely cause | Fix |
|---------|--------------|-----|
| `ModuleNotFoundError: live_sched` | Editable install missing or wrong venv | Re-run `pip install -e ".[dev]"` in package `.venv` |
| Editable install picks up `demo`/`data` as packages | Old setuptools find | Ensure `pyproject.toml` has `tool.setuptools.packages.find` include `live_sched*` only |
| Kafka consume fails immediately | Missing/invalid root `.env` | Copy keys from `.env.example`; never commit `.env` |
| Unit tests try to read full MCA | Wrong working directory or missing fixtures | Run pytest from `server/poc/live-sched` with package fixtures |
