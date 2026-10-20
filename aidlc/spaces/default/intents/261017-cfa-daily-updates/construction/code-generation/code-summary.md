# Code Summary — CFA Daily Updates

## Files created

Under `server/poc/live-sched/`:

- `live_sched/feed_sequence.py` — 3-digit feed code store (seed 990), `+1 mod 1000`, HD Current File Reference parse
- `live_sched/cfa_zip.py` — list `*.zip`, find/read `.CFA` member
- `live_sched/cfa_apply.py` — sequence gate + LM N/R/D apply + folder one-shot
- `live_sched/cfa_report.py` — plain labeled stdout report lines
- `tests/cfa_fixtures.py`, `tests/test_feed_sequence.py`, `tests/test_cfa_zip.py`, `tests/test_cfa_apply.py`, `tests/test_cfa_cli.py`, `tests/__init__.py`

## Files modified

- `live_sched/db.py` — `feed_meta` table
- `live_sched/repository.py` — batch commit helpers, CIF-identity lookup, journey delete
- `live_sched/config.py` — `CFA_WATCH_DIR`, `FEED_SEQUENCE_SEED`
- `live_sched/cli.py` — `cfa-update` subcommand
- `tests/test_db.py` — assert `feed_meta`
- Workspace root `.env.example` — document CFA env keys

## Key decisions

- Sequence source is HD **Current File Reference** (CIF cols 33–39); refuse if no 3-digit run (no zip-basename guess).
- CFA path never unlinks the SQLite file; MCA `load` wipe behaviour unchanged.
- Report columns: total / new / override / duplicate / deletes (BS N/R/duplicate/D).
- Empty watch folder exits 0 with `status: nothing`; refuse/fail sets non-zero but continues remaining zips; zips left in place.

## Tests

```bash
/home/william/code/personal/2025/uk-rail-tracker/server/poc/live-sched/.venv/bin/pytest \
  /home/william/code/personal/2025/uk-rail-tracker/server/poc/live-sched/tests -q
```

**Result:** `35 passed`.

## Deviations

- FR5.1 real update zip demo deferred to Build and Test / manual ops (unit tests use synthetic CFA/zip fixtures).
- API/Frontend layers omitted per plan.
