# Code Summary — Schedule Tracking POC

## Files created

Under `server/poc/schedule/`:

- `schedule_poc/` — `db.py`, `repository.py`, `cif_loader.py`, `identity.py`, `kafka_classify.py`, `harvest.py`, `replay.py`
- `tests/` — `test_db.py`, `test_loader.py`, `test_harvest_replay.py`, `test_identity.py`
- `fixtures/lm_tiny.mca`, `fixtures/harvest_corpus.jsonl`
- `demo/run_demo.py`, `README.md`, `requirements.txt`, `pyproject.toml`, `.env.example`, `.gitignore`

Record artifacts: `source-manifest.json`, `traceability.json` (plan checkboxes ticked).

## Key decisions

- **serviceGUID**: `SVC-{originCrs}{destCrs}{YYMMDD}{HHMM}` (PLAN §3.1); fixture journey `SVC-CREBHM2605170925`.
- **Harvest/replay**: classify Push Port `uR`/`sR` children as in kafka FINDINGS; LM filter via TOC or known LM UIDs; stop at 20 LM events or 2 hours (injectable clock).
- **Step 10 optional mapping**: **implemented for Darwin `rid`** (FINDINGS document `rid`/`uid`/`ssd`); **LDB `serviceId` skipped** (no usable traces in kafka POC).

## Tests

Command: `cd server/poc/schedule && .venv/bin/pytest tests/ -q`

Result (conductor verification): **10 passed** in 0.05s.

## Deviations

- Live Kafka consumer CLI not wired; offline harvest corpus + injectable harvest API cover FR2 demo/tests.
- API/Frontend layers omitted per plan scope notes.
- Host lacked `ensurepip` / `python3-venv`; `.venv` is a portable site-packages + `bin/pytest` wrapper so the documented unit-scoped command still works.
