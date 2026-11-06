# Unit test instructions — mca-load

## Framework

- **Language:** TypeScript on Node
- **Runner:** Node’s test runner via `tsx` (`node --import tsx --test`), scoped to this package
- **Location:** `apps/local-timetable/server/`
- **Do not** run live-sched pytest, Android tests, or a repo-root package test script outside this server package

## Setup (once)

From the repo root:

```bash
cd /home/william/code/personal/2025/uk-rail-tracker/apps/local-timetable/server
corepack enable
pnpm install
```

## Exact unit-scoped command

This command must be runnable before the first post-implementation test step (zero tests is acceptable until Step 4):

```bash
cd /home/william/code/personal/2025/uk-rail-tracker/apps/local-timetable/server && pnpm test
```

Build and Test must use this same command for this unit. Do not substitute a workspace-wide test script.

## Strategy and volume (Standard)

- Five to eight tests per component: `LoadProgressStore`, `DayMcaLocator`, `HeaderGate`, `McaStream`, `SqliteReplacer` (schema/write).
- One integration test across start → progress → throwaway SQLite.
- Happy path plus at least two error cases per component (missing MCA, duplicate day’s file, bad HD sequence, start while running, write after unlink).
- Keep existing live-sched and other suites green by not opening them.

## Coverage targets

- No 80% line-coverage gate until CI exists (team carve-out). This unit’s own command must stay green.
- No coverage flags that weaken or skip tests to make a step pass.

## Mocking / fixtures

- Synthetic MCA files under `apps/local-timetable/server/test/fixtures/` only. Never open a production multi-GB MCA.
- Throwaway SQLite under the test temp directory. Never unlink `apps/local-timetable/db/timetable.sqlite`.
- No Kafka broker, no Darwin API, no `.env`.
- Freeze “today” in locator tests by injecting a calendar date, so unique-match tests do not depend on the wall clock.

## Test data

- Tiny CIF-shaped fixtures: one valid HD (columns 33–39 three digits) + one LM BS/LO/LT journey; one non-LM journey; one unreadable/missing; one duplicate-date pair; one HD with a non-digit sequence.
- After a successful replace, assert overlay tables exist and have zero rows, and forecast columns are empty.
