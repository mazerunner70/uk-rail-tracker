# Code Generation Plan — mca-load

**Target:** `apps/local-timetable/server` (MCA load) plus `apps/local-timetable/compose.yaml`, `apps/local-timetable/db`, `apps/local-timetable/mca`  
**Scope:** classic / Standard / brownfield (new tree under `apps/local-timetable/`)  
**Stories:** US3.1  
**Requirements:** FR/NFR for MCA replace; this unit does not serve the network or pair lists.

## Testing Contract

```json
{
  "version": 1,
  "methodology": "test-after",
  "source": "project",
  "ordering": "Implement each testable layer, then write and run that layer's tests.",
  "scope": "classic",
  "test_strategy": "standard",
  "project_type": "brownfield",
  "applicable_notes": [
    {
      "layer": "org",
      "text": "We treat tests as a first-class deliverable in every Bolt. The specific\nmethodology (TDD, BDD, ATDD, or classic test-after) is affirmed at\npractices-discovery and recorded in `team.md` under this heading with explicit\n`Methodology` and `Ordering` fields; Code Generation resolves those fields\nindependently from coverage, tooling, and scope notes.\n\nWhen no posture has been affirmed, our default per scope is:\n- **Methodology**: test-after\n- **Ordering**: implement each applicable testable layer, then write and run\n  that layer's tests.\n- `mvp`, `enterprise`, `feature`, `infra`, `classic` add an 80% line-coverage\n  floor and CI execution before merge.\n- `bugfix`, `security-patch` add a targeted regression for the specific\n  bug/vulnerability and require the existing suite to remain green.\n- `express` uses the Minimal strategy: requirement-driven unit tests (one per\n  requirement, with a happy-path floor per component); existing tests remain\n  green.\n- `poc`, `refactor`, `workshop` add no extra new-test floor and require the\n  existing suite to remain green.\n\nThe active `Test Strategy` still applies in every scope and determines test\nvolume/types. Scope floors are additive; they never reduce or replace the\nselected strategy.\n\nBuild and Test verifies defined coverage floors and affirmed quality targets;\nthey may not be weakened to make a step pass.\n\nAffirm a stricter posture in `team.md` if the team commits to one."
    },
    {
      "layer": "team",
      "text": "- **Methodology**: test-after\n- **Ordering**: Implement each applicable testable layer, then write and run that layer's tests.\n- **Coverage**: There is no 80% line-coverage gate until continuous integration exists. The bar for the new reader is that its own suite stays green. The repo has no `coverage.py`, pytest-cov, JaCoCo, or Kover today.\n- The new reader gets tests now: unit tests, plus a thin check of the three views (all trains today, trains that deviate, and a chosen pair of stations). This server answers those views. `list_journeys()` does not.\n- Screen tests are in scope once the TypeScript UI exists, on that stack. They do not wait on an unnamed toolkit.\n- Live-sched tests run only when `server/poc/live-sched` changes. Before an edit there, run `.venv/bin/pytest tests/ -q` and record pass, fail, and skip totals. Re-run after, and treat new failures as regressions.\n- Reader tests must not change the SQLite file.\n- Given/When/Then on Inception stories is the story format. We do not switch to TDD or BDD, and we do not add Gherkin or feature files.\n- Live-sched stays on pytest 8 from a project-local `.venv`. The Android tree keeps JUnit 4 and Robolectric, with instrumented tests off. The screen suite for this service is the TypeScript UI, once that UI exists.\n- Timetable tests use synthetic fixtures. They do not open the multi-GB production MCA or a live broker. A real update-zip demo stays later manual ops."
    },
    {
      "layer": "project",
      "text": "- venv should be built correctly when needed so dependency install does not pollute the system (learned 2026-10-11) \n\n- Kept the classic 80% coverage floor carved out because Q4 chose suite-green until CI exists; that is an explicit team carve-out, not silence. (learned 2026-10-20) \n\n- Kept the prior test-lock table instead of deleting locks for modules this run did not open. A rebuild from only the four opened modules would drop CFA and service-day behaviour the 2026-10-20 scan recorded. (learned 2026-10-25)"
    }
  ],
  "obligations": {
    "strategy": "standard",
    "strategy_volume": [
      "Five to eight tests per component.",
      "Unit tests plus integration tests for key boundaries.",
      "Add E2E, performance, or security tests when requirements demand them."
    ],
    "scope_floor": [
      "Keep the existing test suite green.",
      "This scope adds no extra new-test floor beyond the selected test strategy."
    ],
    "combination_rule": "Apply every selected-strategy obligation and every scope-floor obligation; neither replaces the other, and a targeted scope regression may add the narrowest necessary test type beyond the strategy default."
  },
  "plan_profile": {
    "methodology": "test-after",
    "runner_step": "Verify the existing test runner/configuration and record the exact unit-scoped command.",
    "runner_ready_before_first_test": true,
    "testable_layers": [
      "Data model / database behavior",
      "Repository / data access",
      "Business logic",
      "API / endpoint",
      "Frontend behavior"
    ],
    "steps": [
      "Project structure and production configuration skeleton.",
      "Verify the existing test runner/configuration and record the exact unit-scoped command.",
      "Data model / database behavior - implement.",
      "Data model / database behavior - write and run its tests after implementation.",
      "Repository / data access - implement.",
      "Repository / data access - write and run its tests after implementation.",
      "Business logic - implement.",
      "Business logic - write and run its tests after implementation.",
      "API / endpoint - implement.",
      "API / endpoint - write and run its tests after implementation.",
      "Frontend behavior - implement.",
      "Frontend behavior - write and run its tests after implementation.",
      "Environment/build configuration.",
      "Documentation and traceability."
    ]
  },
  "input_sha256": "sha256:fa58da0de469152c3e7e3d17fd88a124dcabbbac1a5d3ed15e9f120f4003822e",
  "contract_sha256": "sha256:39906fca9c791d3fca38608be76c7a829a421509c262d5d144fd2f72d46e4e67"
}
```

## Layers omitted as out of this unit

- **API / endpoint** — N/A. This unit exposes in-process `start` and `progress` only. REST `http://server:8080` belongs to timetable-query.
- **Frontend behavior** — N/A. The screen unit owns `apps/local-timetable/fe`.

## Engineering locks for this pass

- TypeScript, functional style where possible, under `apps/local-timetable/server`. Runtime is Node (not Bun).
- Do not import `schedule_poc` or `live-sched`. Copy the seven-table SQLite *shape* (column names and types) from the existing store so the later query can read it: `journeys`, `calling_points`, `service_day`, `service_aliases`, `feed_meta`, `kafka_offsets`, `applied_updates`.
- Host file is `apps/local-timetable/db/timetable.sqlite`. Compose volume sources are `./db` and `./mca` beside `apps/local-timetable/compose.yaml`.
- Locate, open, and accept the HD sequence (CIF columns 33–39, three digits) **before** unlink. Then unlink and write (W4 / BR3.5).
- `howFar` is percent of MCA bytes read, as text. After the stream finishes it may already be `100` while the write is still `running`. `succeeded` also carries `howFar` `100`.
- Tests use synthetic MCA fixtures and a throwaway SQLite path. They must not unlink the host `timetable.sqlite`.
- No Kafka client, no Darwin keys, no `.env` read in this unit.
- The `screen` Compose service is a keep-alive stub in this unit so the three-container layout exists; the real UI lands in the screen unit.

## Implementation steps

### Step 1 — Project structure and production configuration skeleton

- [x] Create `apps/local-timetable/{db,mca,server}` (empty `db` and `mca` with gitkeep as needed).
- [x] Add `apps/local-timetable/server/package.json`, `tsconfig.json`, and a small `src/` tree: `loadProgressStore`, `dayMcaLocator`, `headerGate`, `mcaStream`, `sqliteReplacer`, `start` / `progress` entry.
- [x] In-container paths map host `./mca` (read-only) and `./db` (read-write). Exact container paths chosen here; the process only opens those two trees.
- [x] `TZ=Europe/London` on the server process.
- Trace: US3.1, BR1.1, BR1.3, NFR1.8, NFR1.12

### Step 2 — Verify the test runner and record the unit-scoped command

- [x] Add Node + TypeScript test config for this package only (`node:test` via `tsx`, or Vitest scoped to this folder).
- [x] Record the exact command in `unit-test-instructions.md` and confirm it is runnable (may report zero tests before Step 4).
- Trace: Testing Contract runner step, BR5.1

### Step 3 — Data model / database behavior — implement

- [x] Encode the seven-table schema and indexes as TypeScript SQL owned by this unit (same store tokens: `service_guid`, `train_uid`, `stp_indicator`, `origin_crs`, `dest_crs`, `run_date`, `days_run`, dates `YYMMDD`, times `HHMM`).
- [x] Foreign keys on. Overlay tables `kafka_offsets` and `applied_updates` created empty. No CFA rows. Forecast columns on calling points empty after this load.
- Trace: US3.1, BR3.1, BR3.4, BR3.6, NFR3.1

### Step 4 — Data model / database behavior — tests after implementation

- [x] 5–8 tests: schema creates all seven tables; overlays empty; older-schema detection is not this unit’s query job but a missing file after failed write is observable; throwaway path only.
- Trace: BR3.6, BR5.1, NFR4.2

### Step 5 — Repository / data access — implement

- [x] `SqliteReplacer`: after header accepted, unlink `timetable.sqlite` then write journeys (LM only), calling points, service days, aliases, feed meta. `service_guid` = `trainUid + "_" + runDate + "_" + stpIndicator`.
- [x] TIPLOC→CRS from TI/AA records in the same MCA stream; unknown CRS stays null. Do not import live-sched lookup tables.
- [x] Feed meta may store load facts (day, HD sequence), never secrets.
- Trace: US3.1, BR3.3, BR3.5, BR5.2, NFR2.2, NFR2.3

### Step 6 — Repository / data access — tests after implementation

- [x] 5–8 tests: LM kept, non-LM dropped; overlays empty; write-fail after unlink leaves a missing file; tests never touch host `apps/local-timetable/db/timetable.sqlite`.
- Trace: BR3.3, BR3.5, BR5.1, NFR4.1, NFR4.2

### Step 7 — Business logic — implement

- [x] `LoadProgressStore`: one in-process value; process death → idle; `start` returns at once; `progress` does not start a load.
- [x] `DayMcaLocator`: unique filename containing today’s `YYYYMMDD` (Europe/London); zero or many → failed, SQLite unchanged.
- [x] `HeaderGate`: CIF columns 33–39 must be three digits; else failed, SQLite unchanged.
- [x] `McaStream`: stream, do not buffer the whole file; `howFar` = bytes read / size.
- [x] One load flag: start while running returns current progress; start after succeeded begins a new replace.
- Trace: US3.1, W1–W5, BR1.2, BR2.1–BR2.4, BR3.2, BR4.1, NFR1.1–NFR1.4, NFR1.13, NFR1.14

### Step 8 — Business logic — tests after implementation

- [x] 5–8 tests per component (`LoadProgressStore`, `DayMcaLocator`, `HeaderGate`, `McaStream`) covering happy path plus at least two failures each: missing MCA, duplicate match, unreadable file, bad HD, start-while-running, start-after-success.
- [x] One integration test: synthetic MCA → start → poll progress → succeeded → throwaway SQLite has LM rows.
- Trace: BR2.2, BR2.3, BR3.2, BR4.1, BR5.1, NFR1.10

### Step 9 — API / endpoint — omit

- [x] No HTTP server in this unit. Export `start` and `progress` for the later query to import in-process.
- Trace: NFR1.5, NFR1.7, contract 2

### Step 10 — Frontend behavior — omit

- [x] No UI in this unit.
- Trace: unit-of-work U3

### Step 11 — Environment / build configuration

- [x] `apps/local-timetable/compose.yaml`: services `db` (`alpine:3.20`, `sleep infinity`, volume `./db`), `server` (this package, volumes `./mca` read-only and `./db` read-write, `TZ=Europe/London`, `unless-stopped`), `screen` stub keep-alive.
- [x] Dockerfile for `server` only. No published port for this unit. No CI workflow.
- Trace: US3.1, BR1.4, NFR1.12, NFR2.1, NFR5.1, infrastructure Q1 A / Q2 A

### Step 12 — Documentation and traceability

- [x] Short `apps/local-timetable/server/README.md`: how to `docker compose -f apps/local-timetable/compose.yaml up --build`, where to put today’s MCA, how to open `apps/local-timetable/db/timetable.sqlite`.
- [x] `code-summary.md`, `traceability.json`, `source-manifest.json` listing every application path this unit created or modified.
- Trace: US3.1, NFR1.15
