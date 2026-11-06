# Code Generation Plan — timetable-query

**Target:** `apps/local-timetable/server` (REST query + read-only SQLite views)  
**Scope:** classic / Standard / brownfield  
**Stories:** US1.1, US2.1, US3.1 (train views and load delegation)  
**Requirements:** FR1–FR6, FR9 (REST load/progress); NFR3 read-only file

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

- **Frontend behavior** — N/A. The screen unit owns `apps/local-timetable/fe`.
- **Database migrations** — N/A. This unit only reads the seven-table shape mca-load writes.

## Engineering locks

- Node HTTP on port 8080 inside the server container (`http://server:8080` on Compose network).
- REST: `POST/GET /load`, `GET /network`, `POST /pair` per approved contract-summary.
- In-process `start` / `progress` from mca-load; wire JSON omits internal byte counters.
- Read-only SQLite open; classify missing, deleted, locked, older-schema (FR6.3).
- Train logic from requirements FR1–FR3 and domain TimetableQuery behaviour (no `list_journeys()`).
- Optional gzip when `Accept-Encoding: gzip` and body is large enough.
- Tests use throwaway SQLite fixtures; never mutate the host `apps/local-timetable/db/timetable.sqlite`.
- Keep mca-load tests green; extend `package.json` test script to run both unit globs.

## Implementation steps

### Step 1 — Project structure and HTTP skeleton

- [ ] Add `londonClock`, `stopTime`, `sqliteReader`, `trainViews`, `httpServer` under `src/`.
- [ ] Replace `main.ts` stub with HTTP listener on 8080.
- [ ] Publish port 8080 on the `server` service in `compose.yaml`.

### Step 2 — Verify test runner and record unit command

- [ ] Extend `package.json` `test` script for `test/timetable-query/*.test.ts`.
- [ ] Document exact command in `unit-test-instructions.md`.

### Step 3 — Repository / read access

- [ ] Implement read-only open + schema gate (`sqliteReader.ts`).
- [ ] Write tests: missing file, older schema, happy open.

### Step 4 — Business logic (network + pair)

- [ ] Implement stop-time, reached/next, problem/issue, cancellations (`trainViews.ts`).
- [ ] Write tests: network list, pair match, empty pair, deactivated, forecast diff, cancellation row.

### Step 5 — API / endpoint layer

- [ ] Wire routes, JSON shapes, 503 failures, 202 on load start (`httpServer.ts`).
- [ ] Write HTTP-level tests with injectable dependencies.

### Step 6 — Integration

- [ ] End-to-end test: fixture DB → GET `/network` and POST `/pair` without changing file bytes.

### Step 7 — Documentation and traceability

- [ ] Update server README for REST surface.
- [ ] Fill `code-summary.md`, `traceability.json`, `source-manifest.json`.

## Story traceability

| Step | Stories | Requirements |
| --- | --- | --- |
| 3–4 | US1.1, US2.1 | FR1, FR2, FR3, FR4, FR6 |
| 5 | US3.1 | FR9, contract REST |
| 6 | US1.1, US2.1, US3.1 | NFR3, NFR4 |
