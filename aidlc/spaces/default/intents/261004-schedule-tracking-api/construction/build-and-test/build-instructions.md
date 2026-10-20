# Build Instructions — Schedule Tracking POC

## Prerequisites

- Python 3.12+ on `PATH` as `python3` (verified on this host: 3.14.4)
- Network only needed for the first `pip install` of `pytest` (optional if the portable `.venv` tree is already present)

## Dependency installation

```bash
cd server/poc/schedule
python3 -m venv .venv
.venv/bin/pip install -r requirements.txt
```

If `python3 -m venv` / `ensurepip` is unavailable on the host, keep the checked-in portable layout:

- `.venv/lib/python3.14/site-packages` — installed packages
- `.venv/bin/pytest` — wrapper that sets `PYTHONPATH` and runs `python3 -m pytest`

## Environment setup

- No AWS, Kafka broker, or SFTP required for the pass/fail path.
- Optional live-harvest credentials: copy `.env.example` → `.env` (never commit secrets). Demo and tests do not read live credentials.
- Working directory for all commands below: `server/poc/schedule`.

## Build / install verification

There is no compile step (pure Python package). Verify the package imports:

```bash
cd server/poc/schedule
PYTHONPATH="$PWD/.venv/lib/python3.14/site-packages:$PWD" python3 -c "import schedule_poc; print('ok')"
```

## Demo build smoke (DB-only)

```bash
cd server/poc/schedule
PYTHONPATH="$PWD/.venv/lib/python3.14/site-packages:$PWD" python3 demo/run_demo.py --fresh
```

Expected: loads `fixtures/lm_tiny.mca`, replays `fixtures/harvest_corpus.jsonl`, prints journey `SVC-CREBHM2605170925`, writes `data/demo.db`.

## Unit test command (authoritative)

From Code Generation `unit-test-instructions.md`:

```bash
cd server/poc/schedule && .venv/bin/pytest tests/ -q
```

## Troubleshooting

| Symptom | Likely cause | Fix |
|---------|--------------|-----|
| `.venv/bin/python: No such file` | Portable venv has pytest wrapper only | Use `python3` + `PYTHONPATH` as above for demo/import |
| `ModuleNotFoundError: pytest` | site-packages missing | Re-run pip install into a real venv, or restore `.venv/lib/.../site-packages` |
| Demo finds 0 journeys | Wrong cwd or missing fixtures | Run from `server/poc/schedule`; confirm `fixtures/lm_tiny.mca` exists |
| Tests hang on harvest | Live clock used by mistake | Unit tests inject clocks; do not wait wall-clock 2 hours |
