# Build Instructions — CFA Daily Updates

## Prerequisites

- Python ≥ 3.12
- Package-local venv under `server/poc/live-sched/.venv` (do not pollute system Python)

## Dependency installation

```bash
cd /home/william/code/personal/2025/uk-rail-tracker/server/poc/live-sched
python3 -m venv .venv
.venv/bin/pip install -e ".[dev]"
```

## Environment setup

| Item | Path / notes |
|------|----------------|
| Package root | `server/poc/live-sched/` |
| Default SQLite | `server/poc/live-sched/data/live_sched.sqlite` |
| CFA watch folder | `CFA_WATCH_DIR` (default `server/poc/live-sched/data/cfa-inbox`) |
| Sequence seed | `FEED_SEQUENCE_SEED` (default `990`) |
| Root env template | `.env.example` |

## Build / install verification

```bash
cd /home/william/code/personal/2025/uk-rail-tracker/server/poc/live-sched
.venv/bin/python -c "import live_sched; print('import_ok')"
.venv/bin/live-sched --help
```

Expected: `import_ok` and CLI help listing `load` / `consume` / `cfa-update`.

## Ops commands (CFA daily update)

```bash
# One-shot: process *.zip in watch folder (zips left in place)
.venv/bin/live-sched cfa-update --watch /path/to/inbox --db data/live_sched.sqlite
```

## Troubleshooting

- Missing venv / import errors → recreate `.venv` and reinstall `[dev]`
- Sequence refuse → check DB `feed_meta.feed_sequence` vs HD Current File Reference digits
- No CFA in zip → ensure member ends with `.CFA`
