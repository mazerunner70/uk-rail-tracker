# Test Results — CFA Daily Updates

## Build

| Check | Result |
|-------|--------|
| `import live_sched` | `import_ok` |
| `live-sched --help` | lists `load`, `consume`, `cfa-update` |

## Unit tests

Command:

```bash
/home/william/code/personal/2025/uk-rail-tracker/server/poc/live-sched/.venv/bin/pytest \
  /home/william/code/personal/2025/uk-rail-tracker/server/poc/live-sched/tests -q
```

**Result:** `35 passed in 0.11s` (exit 0).

## CLI smoke (synthetic CFA zip)

Temp inbox + DB under `/tmp/cfa-bt-*`:

1. `cfa-update` with synthetic `RJTTC991.ZIP` after seed 990 → `status: accepted`, `new: 1`, exit 0.
2. Second run with wrong sequence zip present → `status: refused` for mismatch; zips remain on disk; exit non-zero.

No Network Rail **real** daily-update zip was present in the workspace; FR5.1 real-zip acceptance remains an ops follow-up when a live zip is available.

## Security checklist

| Check | Result |
|-------|--------|
| `schedule_poc` imports | none |
| `.env.example` CFA keys | `CFA_WATCH_DIR`, `FEED_SEQUENCE_SEED` documented |

## Target Verification Matrix (final)

See `build-and-test-summary.md`.
