# Unit Test Instructions — CFA Daily Updates (stage-level)

## Framework

- **Runner:** pytest (dev extra in `server/poc/live-sched/pyproject.toml`)
- **Package venv:** `server/poc/live-sched/.venv` (create/refresh with package-local pip install; do not pollute system Python)
- **Config:** `[tool.pytest.ini_options]` — `testpaths = ["tests"]`, `pythonpath = ["."]`

## Exact commands (unit-scoped)

From `server/poc/live-sched` with the package venv activated (or via `.venv/bin/pytest`):

```bash
# Existing suite (must stay green)
/home/william/code/personal/2025/uk-rail-tracker/server/poc/live-sched/.venv/bin/pytest \
  /home/william/code/personal/2025/uk-rail-tracker/server/poc/live-sched/tests \
  -q

# New CFA daily-update tests only (create these files during generation)
/home/william/code/personal/2025/uk-rail-tracker/server/poc/live-sched/.venv/bin/pytest \
  /home/william/code/personal/2025/uk-rail-tracker/server/poc/live-sched/tests/test_feed_sequence.py \
  /home/william/code/personal/2025/uk-rail-tracker/server/poc/live-sched/tests/test_cfa_zip.py \
  /home/william/code/personal/2025/uk-rail-tracker/server/poc/live-sched/tests/test_cfa_apply.py \
  /home/william/code/personal/2025/uk-rail-tracker/server/poc/live-sched/tests/test_cfa_cli.py \
  -q
```

If a listed file does not exist yet, run only files that exist for that layer; do not use a bare project-wide `pytest` from the repo root.

## Coverage / volume (Minimal + poc)

- Requirement-driven: at least one verifiable test per FR area (FR1–FR4); happy-path per new component
- No extra coverage floor beyond Minimal; keep existing suite green
- FR5.1 real-zip demo may be manual / Build-and-Test; unit tests may use synthetic CFA/zip fixtures

## Mocking / fixtures

- Prefer temp SQLite DB paths (existing conftest patterns)
- Synthetic zip + CFA bytes in `tests/` or `fixtures/` for sequence/report cases
- Do not require live Kafka or real Network Rail credentials for unit tests

## Expected outcomes

- Refuse wrong sequence without mutating journeys / feed code
- Accept next code after seed 990 → 991
- Report lines include total / new / override / duplicate / deletes
