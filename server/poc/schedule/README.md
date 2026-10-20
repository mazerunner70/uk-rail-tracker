# Schedule Tracking POC (London Midland)

Local proof-of-concept under `server/poc/schedule/`: load an offline LM CIF
excerpt into SQLite, harvest/replay Darwin Push Port JSON updates, and look up
journeys by a stable `serviceGUID`. No HTTP API, Android, or AWS.

Grounded in `server/poc/timetable/FINDINGS.md` and `server/poc/kafka/FINDINGS.md`.

## Layout

| Path | Role |
|------|------|
| `schedule_poc/` | Library: DB, CIF loader, harvest, replay, identity |
| `fixtures/lm_tiny.mca` | Tiny CIF excerpt (LM + non-LM for filter proof) |
| `fixtures/harvest_corpus.jsonl` | Offline harvested Push Port JSON |
| `tests/` | pytest suite (minimal / test-after) |
| `demo/run_demo.py` | DB-only demo script |

## Setup

```bash
cd server/poc/schedule
python3 -m venv .venv
.venv/bin/pip install -r requirements.txt
```

## Tests

```bash
cd server/poc/schedule && .venv/bin/pytest tests/ -q
```

## Offline demo

```bash
cd server/poc/schedule
.venv/bin/python demo/run_demo.py --fresh
```

The script loads `fixtures/lm_tiny.mca`, replays `fixtures/harvest_corpus.jsonl`
into a local SQLite file (`data/demo.db` by default), and prints journeys keyed
by `serviceGUID`.

## Stable identity

Format (PLAN §3.1 / timetable FINDINGS):

```text
SVC-{originCrs}{destCrs}{runDateYYMMDD}{originStdHHMM}
```

Example from the fixture: Crewe → Birmingham New Street, 09:25 on 2026-05-17 →
`SVC-CREBHM2605170925`.

Darwin `rid` from harvested Kafka/TS is stored as an alias when it can be tied
to a loaded journey via `uid` + `ssd`. LDB `serviceId` is not mapped — the kafka
POC findings do not provide usable `serviceId` traces.

## Harvest stop rule

Stop when **20** LM-relevant events are captured **or** **2 hours** elapse,
whichever first. Unit tests inject clocks/counters; live Kafka is optional and
not required for the pass/fail demo.

## Optional live harvest

If you have RDM credentials (see `server/poc/kafka/.env.example`), copy them
into `.env` from `.env.example` and extend a thin consumer using the same
classify/filter helpers. The checked-in demo path stays offline-only.
