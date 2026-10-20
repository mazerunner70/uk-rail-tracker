# live-sched — Live Schedule POC

Local proof of concept under `server/poc/live-sched/`:

1. Stream-parse the Network Rail MCA timetable, persist **London Midland (LM)** schedules only into local SQLite.
2. Write schedule-code **summary** and **algorithm** markdown under `docs/`.
3. Run a Kafka consumer for **one hour**, archive every message to CSV, apply Schedule-POC-equivalent updates (`TS`, `schedule`, `deactivated`) for matching LM journeys, and append a short markdown DB-change log.

This package does **not** import `schedule_poc` from `server/poc/schedule/`.

## Setup

```bash
cd /home/william/code/personal/2025/uk-rail-tracker/server/poc/live-sched
python3 -m venv .venv
.venv/bin/pip install -e ".[dev]"
```

## Unit tests

```bash
cd /home/william/code/personal/2025/uk-rail-tracker/server/poc/live-sched && .venv/bin/pytest tests/ -q
```

Tests use tiny fixtures under `fixtures/` (never the multi-GB production MCA).

## Credentials (workspace-root `.env`)

Copy the repo-root `.env.example` to `.env` at the workspace root (gitignored). Variable names match the Kafka POC:

- `KAFKA_BOOTSTRAP_SERVERS`
- `KAFKA_CONSUMER_KEY`
- `KAFKA_CONSUMER_SECRET`
- `KAFKA_GROUP_ID`
- Optional: `KAFKA_SSL_CA_LOCATION`, `KAFKA_TOPIC_JSON`, `KAFKA_TOPIC_XML`, `KAFKA_TOPIC_AVRO`

Schedule load reads the MCA file as `SCHEDULE_DIR` / `SCHEDULE_FILE` from that same `.env`. A relative `SCHEDULE_DIR` is resolved from the workspace root. When either variable is unset, the load uses `server/poc/timetable/RJTTF944MCA.txt`.

```
SCHEDULE_DIR=server/poc/timetable
SCHEDULE_FILE=RJTTF944MCA.txt
```

Never hardcode secrets.

## Load MCA → SQLite + docs

```bash
.venv/bin/live-sched load
# or a tiny fixture, which overrides the configured folder and file:
.venv/bin/live-sched load --mca fixtures/lm_tiny.mca
```

Outputs:

| Path | Purpose |
|------|---------|
| `data/live_sched.sqlite` | LM journeys / calling points |
| `docs/schedule-code-summary.md` | Per-code counts |
| `docs/schedule-code-algorithms.md` | Per-code algorithms |

## One-hour Kafka consumer

Default duration is **3600 seconds**. Use `--duration` for local dry-runs.

```bash
.venv/bin/live-sched consume
.venv/bin/live-sched consume --duration 30
```

The consumer assigns each partition itself. After a message is handled, the next offset for that topic partition is written to `kafka_offsets` in the same SQLite file. The next `consume` seeks back to those offsets, so a stop continues where it left off. A partition with no saved row starts at the live end of the log. `load` replaces the database file, so the following `consume` starts live again.

Outputs:

| Path | Purpose |
|------|---------|
| `output/kafka-messages.csv` | Every message received |
| `output/db-changes.md` | Short entry per applied DB change |
| `data/live_sched.sqlite` table `kafka_offsets` | Next offset to read per topic partition |

## Layout

```
live-sched/
  live_sched/     # library + CLI
  tests/          # pytest
  fixtures/       # tiny MCA + Kafka JSON samples
  docs/           # generated schedule-code docs
  output/         # CSV + change log
  data/           # SQLite
  demo/           # thin CLI wrapper
```
