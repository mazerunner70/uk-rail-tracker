# Integration test instructions

## Scope (Standard test strategy)

Cross-package checks that are not fully covered by a single unit glob: **server HTTP + SQLite read paths** together (already exercised by `test/timetable-query/` integration-style tests), and **screen + server contracts** via mocked `fetch` in FE tests. Full browser E2E and Docker compose smoke tests are optional manual follow-ups (no CI pipeline per requirements NFR5 carve-out).

## Server integration command

From `apps/local-timetable/server`, the package test script runs **mca-load** and **timetable-query** suites together:

```bash
cd /home/william/code/personal/2025/uk-rail-tracker/apps/local-timetable/server
PATH=/home/william/.nvm/versions/node/v22.18.0/bin:$PATH pnpm test
```

Expected: all tests pass; last lines report total test count (69 pass in the 2026-11-06 run).

## Front-end against API contract (mocked)

Screen tests use **happy-dom** and mock `fetch`; run:

```bash
cd /home/william/code/personal/2025/uk-rail-tracker/apps/local-timetable/fe
PATH=/home/william/.nvm/versions/node/v22.18.0/bin:$PATH pnpm test
```

Expected: 9 pass (2026-11-06 run).

## Optional manual check (not required for matrix)

- `docker compose` up and open `http://localhost:8081` with server on `8080` per `compose.yaml` — validates nginx proxy wiring; defer if no Docker available.
