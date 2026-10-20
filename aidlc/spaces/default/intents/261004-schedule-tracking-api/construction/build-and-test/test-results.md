# Test Results — Schedule Tracking POC

**Executed**: 2026-10-11T12:06:10Z (re-run; same result as 2026-10-04T17:25:57Z)  
**Host Python**: 3.14.4 (`/usr/bin/python3`)

## Build

### Package import

```bash
cd server/poc/schedule
PYTHONPATH="$PWD/.venv/lib/python3.14/site-packages:$PWD" python3 -c "import schedule_poc; print('import_ok')"
```

- **Status**: success
- **Output**: `import_ok`

### Offline demo

```bash
cd server/poc/schedule
PYTHONPATH="$PWD/.venv/lib/python3.14/site-packages:$PWD" python3 demo/run_demo.py --fresh
```

- **Status**: success (exit 0)
- **Output (abridged)**:
  - Loaded 1 LM journey from `fixtures/lm_tiny.mca`
  - Replayed 3 update items from `fixtures/harvest_corpus.jsonl`
  - Journey `SVC-CREBHM2605170925` printed with `alias rid=202605178765432`, `applied_updates=3`
  - DB path: `server/poc/schedule/data/demo.db`

## Unit tests

Command (deduplicated from stage-level `unit-test-instructions.md`):

```bash
cd server/poc/schedule && .venv/bin/pytest tests/ -q
```

- **Status**: success
- **Totals**: 10 passed, 0 failed, 0 skipped
- **Duration**: 0.02s
- **Output**: `.......... [100%] 10 passed in 0.02s`

## Other checks

| Check | Result |
|-------|--------|
| Integration suite | Not applicable (Minimal) |
| Performance suite | Not applicable (Minimal / no NFR stage) |
| Security lightweight review | Pass — offline path; `.env.example` placeholders; dependency = pytest |

## Coverage

No coverage gate for `poc`. Framework supports coverage optionally; not collected this run.

## Target Verification Matrix (final)

| Target ID | Source | Expected | Actual | Evidence | Owning Stage | Verdict |
|-----------|--------|----------|--------|----------|--------------|---------|
| BT-UNIT-GREEN | unit-test-instructions / Testing Contract | pytest unit command exits 0 | 10 passed, exit 0 | this file § Unit tests | build-and-test | Met |
| BT-IMPORT | build-instructions | import schedule_poc | import_ok | this file § Build | build-and-test | Met |
| BT-DEMO-LOCAL | NFR1.1 / FR4 | Offline demo shows stable id journey | exit 0; SVC-CREBHM2605170925 | this file § Demo | build-and-test | Met |
| BT-SECRETS | security checklist | No secrets in `.env.example` | placeholders only | `.env.example` | build-and-test | Met |
| BT-TRACE-OK | requirements + traceability.json | All FR/NFR OK + targets exist | pass | cross-unit-traceability.md | build-and-test | Met |
| BT-EXISTING-SUITE | poc scope floor | Do not break existing suites | schedule-only changes | code-summary / package tree | build-and-test | Met |

## Failure details

None.
