# Code summary — mca-load

In-process TypeScript MCA load under `apps/local-timetable/server`. `start` and `progress` replace `timetable.sqlite` from that day's MCA (London Midland only). No HTTP. No UI.

## Files created/modified

Layout: `apps/local-timetable/{db,mca,server}` and `apps/local-timetable/compose.yaml`.

Server sources: `src/index.ts` exports `start` and `progress`; `src/start.ts` is the load controller; `src/loadProgressStore.ts`, `src/dayMcaLocator.ts`, `src/headerGate.ts`, `src/mcaStream.ts`, `src/sqliteReplacer.ts`, `src/sqliteSchema.ts`, `src/cif.ts`, `src/serviceDays.ts`, `src/paths.ts`.

Tests: `test/mca-load/*.test.ts` plus synthetic fixtures. Command:

```bash
cd /home/william/code/personal/2025/uk-rail-tracker/apps/local-timetable/server && node --import tsx --test test/mca-load/*.test.ts
```

Result (this machine, Node 22.18.0 via nvm): **57 pass, 0 fail**.

## Key implementation decisions

- Node + TypeScript, not Bun. SQLite via `better-sqlite3`.
- In-container paths `/mca` and `/db/timetable.sqlite`. Compose volumes `./mca` (ro) and `./db` beside the compose file.
- HD columns 33–39 must be three digits before unlink. Then unlink and write seven tables.
- `service_guid` is `trainUid_runDate_stpIndicator`. Schema column names match the existing store; this package does not import `live-sched` or `schedule_poc`.
- TIPLOC→CRS from TI/TA/AA when the CRS slice is three letters; unknown calling-point CRS stays null. Journeys without origin and dest CRS are skipped because those columns are NOT NULL.
- Overlay tables exist and stay empty. Forecast columns stay empty after this load.
- `screen` is an alpine keep-alive stub.

## Test coverage summary

| Suite | Tests |
| --- | --- |
| DayMcaLocator | 7 |
| HeaderGate | 7 |
| LoadProgressStore | 7 |
| McaStream | 7 |
| sqlite schema | 8 |
| SqliteReplacer | 8 |
| start and progress | 8 |
| integration | 1 |
| **Total** | **57 pass** |

## Deviations from the plan

- Tests could not run inside the delegated developer session (Node/npm blocked there). The parent session ran them; all 57 passed.
- `better-sqlite3` instead of `node:sqlite`.
- `calling_points.seq` is INTEGER, matching the copied store.
- `service_aliases` stays empty on MCA-only load (BR3.9); table exists, no rows inserted.

## Sources

- `aidlc/spaces/default/intents/261025-local-timetable-viewer/construction/mca-load/code-generation/code-generation-plan.md`
- `aidlc/spaces/default/intents/261025-local-timetable-viewer/construction/mca-load/code-generation/unit-test-instructions.md`

## Assumptions & Open Questions

None.
