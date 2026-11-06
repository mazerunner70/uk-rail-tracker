# Unit test instructions — timetable-query

## Framework

- **Language:** TypeScript on Node
- **Runner:** Node’s test runner via `tsx` (`node --import tsx --test`), scoped to this unit’s files
- **Location:** `apps/local-timetable/server/test/timetable-query/`
- **Do not** run live-sched pytest or mutate `apps/local-timetable/db/timetable.sqlite`

## Setup (once)

```bash
cd /home/william/code/personal/2025/uk-rail-tracker/apps/local-timetable/server
corepack enable
pnpm install
```

## Exact unit-scoped command

Runnable before the first post-implementation test (may report zero tests until files exist):

```bash
cd /home/william/code/personal/2025/uk-rail-tracker/apps/local-timetable/server && PATH=/home/william/.nvm/versions/node/v22.18.0/bin:$PATH node --import tsx --test test/timetable-query/*.test.ts
```

Also run the mca-load suite when touching shared entrypoints:

```bash
cd /home/william/code/personal/2025/uk-rail-tracker/apps/local-timetable/server && PATH=/home/william/.nvm/versions/node/v22.18.0/bin:$PATH node --import tsx --test test/mca-load/*.test.ts test/timetable-query/*.test.ts
```

## Strategy and volume (Standard)

- Five to eight tests per component: `sqliteReader`, `stopTime`/`londonClock`, `trainViews`, `httpServer`.
- Integration: HTTP ask against a throwaway SQLite fixture; assert file bytes unchanged after train asks.
- Error cases: missing, older-schema, locked (simulated), empty pair, cancellation-only row, deactivated, forecast mismatch.

## Coverage targets

- No 80% line-coverage gate until CI exists. This unit’s command must stay green alongside mca-load.

## Mocking / fixtures

- Build SQLite fixtures in test temp dirs using the same `SCHEMA_SQL` as mca-load.
- Inject `now`, `sqlitePath`, and in-process load controller in HTTP tests.
- No production MCA, no Kafka, no `.env` secrets.

## Test data

- At least one LM journey with three calling points and a `service_day` row for a fixed `YYMMDD`.
- One journey with `status = deactivated`.
- One calling point with non-null forecast differing from public time.
- One cancelled `service_day` row for a train whose route includes a test pair.
