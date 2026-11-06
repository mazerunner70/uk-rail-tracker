# Code Generation Plan — screen

**Target:** `apps/local-timetable/fe` (TypeScript UI in the screen container)  
**Scope:** classic / Standard / brownfield  
**Stories:** US4.1–US4.4 (empty screen, load, network page, pair page)  
**Requirements:** FR1, FR2, FR3, FR5, FR9; contract REST to timetable-query

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

- **API / endpoint** — N/A. timetable-query owns REST on port 8080.
- **Database** — N/A. Screen never mounts SQLite.

## Engineering locks

- TypeScript UI under `apps/local-timetable/fe`; static assets served from the screen container (nginx or Node static server in Dockerfile).
- Base URL for API: `http://server:8080` on Compose network; document host port `8080` for browser dev via published server port.
- Four asks only: `POST /load`, `GET /load`, `GET /network`, `POST /pair` per contract-summary.
- FR5 polling: default 30s interval, editable, reload resets to 30; refuse blank/non-positive interval.
- FR9 empty screen until load succeeds; then network auto-refresh; pair only when both CRS filled.
- FR5.4–FR5.7: held answers, comms lost, loading vs empty copy per requirements.
- Add `data-testid` on load button, interval input, station fields, status bar, train lists.
- Compose: replace screen `sleep infinity` with built image serving `fe/`; no SQLite volume on screen service.
- Keep server mca-load + timetable-query tests green when touching shared compose.

## Implementation steps

### Step 1 — FE project skeleton

- [ ] Add `apps/local-timetable/fe/package.json`, `tsconfig.json`, build script (esbuild or vite) emitting static `dist/`.
- [ ] Add `apiClient.ts` wrapping fetch to the four REST asks with JSON parse errors → failed ask.

### Step 2 — Verify test runner and record unit command

- [ ] Add `test/` with Node test runner + jsdom or happy-dom for DOM behaviour.
- [ ] Document exact command in `unit-test-instructions.md`.

### Step 3 — Empty screen and load flow (FR9, FR5.1)

- [ ] Empty view: load button, progress polling until `LoadProgress.state` succeeded or failed.
- [ ] Hide load button on success; show network + pair chrome.

### Step 4 — Network page (FR2, FR5)

- [ ] Poll `GET /network` on interval; render rows with reached/next/issue flags.
- [ ] Tests: success list, empty success, 503 failure copy, comms lost.

### Step 5 — Station pair page (FR1, FR3.2)

- [ ] Two CRS inputs; ask `POST /pair` only when both filled and load succeeded.
- [ ] Tests: pair ask gating, cancellation row display, empty list sentence.

### Step 6 — Interval control and status (FR5.2–FR5.7)

- [ ] Interval input validation; status bar messages; no stale answer overwrite.

### Step 7 — Container and traceability

- [ ] `fe/Dockerfile` + compose `screen` service build context.
- [ ] Fill `code-summary.md`, `traceability.json`, `source-manifest.json`.

## Story traceability

| Step | Stories | Requirements |
| --- | --- | --- |
| 3 | US4.1, US4.2 | FR9, FR8 (via REST) |
| 4 | US4.3 | FR2, FR5 |
| 5 | US4.4 | FR1, FR3 |
| 6 | US4.3, US4.4 | FR5 |
