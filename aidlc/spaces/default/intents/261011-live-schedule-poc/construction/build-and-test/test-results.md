# Test Results — Live Schedule POC

**Executed**: 2026-10-16T15:15:00Z  
**Package**: `server/poc/live-sched/`  
**Test strategy**: Minimal

## Build

| Check | Command | Result |
|-------|---------|--------|
| Editable install / venv | Existing `.venv`; `pip install -e ".[dev]"` previously applied | Present |
| Import | `.venv/bin/python -c "import live_sched; print('import_ok')"` | `import_ok` |
| CLI | `.venv/bin/live-sched --help` | Lists `load` / `consume` |

**Build status**: success

## Unit tests

Command (from Code Generation `unit-test-instructions.md`):

```bash
cd /home/william/code/personal/2025/uk-rail-tracker/server/poc/live-sched && .venv/bin/pytest tests/ -q
```

**Output**:

```
.......                                                                  [100%]
7 passed in 0.03s
```

| Metric | Value |
|--------|-------|
| Total | 7 |
| Passed | 7 |
| Failed | 0 |
| Skipped | 0 |

**Unit status**: success

## Integration / performance

Not executed (Minimal strategy stubs).

## Security checklist

| Check | Result |
|-------|--------|
| `**/.env` gitignored | Yes (`.gitignore` line 4) |
| `.env.example` placeholders | Yes (`your-bootstrap-server`, `your-consumer-key`, …) |
| No `schedule_poc` import under `live_sched/` / `tests/` | Confirmed (rg empty) |
| Unit tests offline | Yes (fixtures only) |

## Target Verification Matrix (final)

See `build-and-test-summary.md` — all applicable targets **Met**.
