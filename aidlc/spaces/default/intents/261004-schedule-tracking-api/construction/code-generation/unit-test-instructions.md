# Unit Test Instructions — Schedule Tracking POC (stage-level)

## Framework

- **Language**: Python 3.12
- **Runner**: pytest
- **Location**: `server/poc/schedule/`
- **Strategy**: Minimal (requirement-driven; happy-path floor per component)
- **Methodology**: test-after (implement layer, then write/run that layer’s tests)
- **Coverage floor**: none beyond Minimal strategy; keep existing repo suites green (do not break Android/Kafka POC tests)

## Setup

```bash
cd server/poc/schedule
python3 -m venv .venv
.venv/bin/pip install -r requirements.txt
```

## Exact unit-scoped test command

Run **only** this package’s tests (not the whole monorepo):

```bash
cd server/poc/schedule && .venv/bin/pytest tests/ -q
```

Optional verbose:

```bash
cd server/poc/schedule && .venv/bin/pytest tests/ -v
```

Do **not** use a bare project-wide `pytest` from the repo root.

## What to test (Minimal map)

| Requirement | Tests (minimum) |
|-------------|-----------------|
| FR1 offline LM schedule load | Load tiny LM fixture → rows in DB; non-LM filtered out |
| FR2 Kafka harvest + replay | Harvest stop rule (20 LM events **or** 2 hours); apply harvested/fixture updates → store reflects overlay on copy |
| FR3 stable journey id | Lookup by stable id before/after update; optional Darwin `serviceId`→`serviceGUID` mapping test only if POC evidence supports it |
| FR4 DB demo/tests path | Query path works without HTTP (covered by above + demo script smoke if unit-testable) |
| NFR1 locality | Tests/demo use local fixtures/DB only (no live AWS/RDM required) |
| NFR2 testability | Command above runs green |
| NFR3 `server/poc` grounding | Fixtures/helpers may reuse kafka/timetable findings patterns |

Each of `db`, `loader`, `replay`, `identity` modules gets at least one happy-path unit test.

## Mocking / fixtures

- Prefer file fixtures under `server/poc/schedule/fixtures/` (tiny CIF excerpt + harvested Kafka JSON).
- Unit tests must cover the harvest stop rule with injected clocks/counters (20 events **or** 2 hours) without waiting real wall-clock time.
- Live Kafka may be used for optional real harvest runs; unit tests must not require a live broker or SFTP.
- Use a temp SQLite file or `:memory:` per test.

## Expected coverage

- No numeric coverage gate for `poc`.
- Every FR above has at least one automated test.
- Existing Android and `server/poc/kafka` suites remain untouched/green if run separately.
