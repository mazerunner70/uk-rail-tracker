# Unit Test Instructions — Live Schedule POC (stage-level)

## Framework

- **Runner**: pytest
- **Python**: ≥3.12
- **Location**: `server/poc/live-sched/`
- **Venv**: create `server/poc/live-sched/.venv` and install the package + pytest there (do not install into the system Python)

## Setup (once)

```bash
cd /home/william/code/personal/2025/uk-rail-tracker/server/poc/live-sched
python3 -m venv .venv
.venv/bin/pip install -e ".[dev]"
```

If extras are not defined yet, install editable package plus pytest explicitly into that venv.

## Exact unit-scoped command

Before the first post-implementation test step, this command must be runnable:

```bash
cd /home/william/code/personal/2025/uk-rail-tracker/server/poc/live-sched && .venv/bin/pytest tests/ -q
```

Do **not** use a bare project-wide `pytest` from the monorepo root.

## Strategy and volume (Minimal + poc floor)

- One verifiable test per requirement area at the narrowest effective level (FR1 load/schema, FR2 docs generation smoke or parser counts, FR3 classify/apply with fixtures).
- At least one happy-path unit test per component (db, repository, cif/load, kafka apply).
- Keep existing repository suites green; no extra coverage floor beyond Minimal.

## Mocking / fixtures

- Use tiny MCA fixtures under `server/poc/live-sched/fixtures/` for load tests — never require the multi-GB production MCA in unit tests.
- Kafka apply tests use recorded JSON payloads on disk; do not require a live broker for unit tests.
- Live 1-hour consumer is a demo/ops path documented in README; optional short duration flag for manual dry-runs.

## Coverage targets

- No numeric coverage gate for this POC beyond the Minimal strategy obligations above.
- Prefer asserting DB rows, doc file existence/content snippets, CSV/log append behaviour, and apply outcomes.

## Test data

- LM-only fixture schedules and a handful of Push Port `uR`/`sR` samples covering `TS`, `schedule`, and deactivated (or skip with explicit assert of ignore path for non-matching TOC).
