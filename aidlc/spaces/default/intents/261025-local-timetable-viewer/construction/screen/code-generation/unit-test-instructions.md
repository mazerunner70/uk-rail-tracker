# Unit test instructions — screen

## Framework

- **Language:** TypeScript on Node
- **Runner:** Node test runner via `tsx` (`node --import tsx --test`), scoped to this unit
- **Location:** `apps/local-timetable/fe/test/`
- **DOM:** Use `happy-dom` or jsdom registered in test setup; no browser E2E required for this unit gate

## Setup (once)

```bash
cd /home/william/code/personal/2025/uk-rail-tracker/apps/local-timetable/fe
corepack enable
pnpm install
```

## Exact unit-scoped command

Runnable before the first post-implementation test (may report zero tests until files exist):

```bash
cd /home/william/code/personal/2025/uk-rail-tracker/apps/local-timetable/fe && PATH=/home/william/.nvm/versions/node/v22.18.0/bin:$PATH node --import tsx --test test/*.test.ts
```

When touching shared compose or server contracts, also run server suites:

```bash
cd /home/william/code/personal/2025/uk-rail-tracker/apps/local-timetable/server && PATH=/home/william/.nvm/versions/node/v22.18.0/bin:$PATH node --import tsx --test test/mca-load/*.test.ts test/timetable-query/*.test.ts
```

## Strategy and volume (Standard)

- Five to eight tests per UI module: `apiClient`, empty/load view, network view, pair view, interval/status.
- Mock `fetch` for REST; assert DOM text and `data-testid` presence.
- Cover FR5.4 held answer, FR5.6 comms lost, FR5.7 loading vs empty copy, FR9 load-before-trains.

## Coverage targets

- No 80% line-coverage gate until CI exists. This unit’s command must stay green alongside server units.
