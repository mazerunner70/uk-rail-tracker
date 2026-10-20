# Code Generation Plan — Schedule Tracking POC (stage-level)

**Target**: zero-Unit POC under `server/poc/schedule/`  
**Requirements**: `aidlc/spaces/default/intents/261004-schedule-tracking-api/inception/requirements-analysis/requirements.md`  
**Brownfield base**: `server/poc/kafka`, `server/poc/timetable` FINDINGS + samples  
**Out of plan**: HTTP `/v1` server, Android wiring, AWS

## Testing Contract

```json
{
  "version": 1,
  "methodology": "test-after",
  "source": "org",
  "ordering": "implement each applicable testable layer, then write and run that layer's tests.",
  "scope": "poc",
  "test_strategy": "minimal",
  "project_type": "brownfield",
  "applicable_notes": [
    {
      "layer": "org",
      "text": "We treat tests as a first-class deliverable in every Bolt. The specific\nmethodology (TDD, BDD, ATDD, or classic test-after) is affirmed at\npractices-discovery and recorded in `team.md` under this heading with explicit\n`Methodology` and `Ordering` fields; Code Generation resolves those fields\nindependently from coverage, tooling, and scope notes.\n\nWhen no posture has been affirmed, our default per scope is:\n- **Methodology**: test-after\n- **Ordering**: implement each applicable testable layer, then write and run\n  that layer's tests.\n- `mvp`, `enterprise`, `feature`, `infra`, `classic` add an 80% line-coverage\n  floor and CI execution before merge.\n- `bugfix`, `security-patch` add a targeted regression for the specific\n  bug/vulnerability and require the existing suite to remain green.\n- `express` uses the Minimal strategy: requirement-driven unit tests (one per\n  requirement, with a happy-path floor per component); existing tests remain\n  green.\n- `poc`, `refactor`, `workshop` add no extra new-test floor and require the\n  existing suite to remain green.\n\nThe active `Test Strategy` still applies in every scope and determines test\nvolume/types. Scope floors are additive; they never reduce or replace the\nselected strategy.\n\nBuild and Test verifies defined coverage floors and affirmed quality targets;\nthey may not be weakened to make a step pass.\n\nAffirm a stricter posture in `team.md` if the team commits to one."
    }
  ],
  "obligations": {
    "strategy": "minimal",
    "strategy_volume": [
      "One verifiable test per requirement at the narrowest effective level.",
      "At least one happy-path unit test per component.",
      "Unit tests are the default; a bugfix/security scope floor may require an integration or E2E regression when that is the narrowest level that reproduces the defect."
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
  "input_sha256": "sha256:ddd208517f68585f816e80821872bd21e26dc15e7bf545c6a78ec96b6afc20da",
  "contract_sha256": "sha256:8780c9b6738d34215859c7d0d02b149a16944cf2942a43d2c2dee67268c80df0"
}
```

## Scope notes

- **Omit** API/endpoint and Frontend layers — requirements forbid HTTP API and Android changes (FR4, Out of scope).
- Demo proves FR4 via DB query script + automated tests (not a REST client).
- CIF: stream-parse offline MCA (or a small LM fixture derived from FINDINGS approach); TOC filter `LM`.
- Kafka: **harvest** LM-relevant events from the feed (or POC consumer path), then **replay** those harvested records into a working copy of the store. Pass/fail demo may use the harvested corpus offline after harvest completes.
- **Harvest stop rule**: stop when **20** LM events have been captured **or** **2 hours** have elapsed, whichever comes first.
- Stable id: design for later `serviceGUID` reuse (CIF UID + run date + origin public time pattern from timetable FINDINGS / PLAN notes).

## Implementation steps

- [x] **Step 1** — Create `server/poc/schedule/` package skeleton (Python 3.12): `pyproject.toml` / `requirements.txt`, `README.md`, package layout `schedule_poc/`, `tests/`, `fixtures/`, `demo/`. Trace: FR1–FR4 scaffolding.
- [x] **Step 2** — Verify pytest runner; record exact unit-scoped command in `unit-test-instructions.md` (e.g. `cd server/poc/schedule && .venv/bin/pytest tests/ -q`). Trace: NFR2.
- [x] **Step 3** — Implement SQLite schema for journeys, calling points, and applied updates (local file DB). Trace: FR1.2, FR4.1.
- [x] **Step 4** — Write/run data-model tests (schema create, insert, query by stable id). Trace: FR3.2 happy path floor.
- [x] **Step 5** — Implement offline LM schedule load: stream-parse CIF MCA (or fixture), filter TOC `LM`, persist journeys. Trace: FR1.1, FR1.2, NFR3.1.
- [x] **Step 6** — Write/run schedule-load tests against a tiny LM fixture under `fixtures/`. Trace: FR1.
- [x] **Step 7** — Implement Kafka **harvest + replay** for LM events, grounded in `server/poc/kafka`:
  - **Harvest**: consume/classify updates and persist LM-relevant events to a local harvest corpus; **stop when 20 LM events are harvested or 2 hours have passed**, whichever first.
  - **Replay**: apply the harvested Push Port JSON (`uR`/`sR` children, e.g. `TS` / `schedule` when present) into a working DB copy of the loaded schedule.
  - Trace: FR2.1, FR2.2, NFR3.1.
- [x] **Step 8** — Write/run harvest-boundary and replay tests (stop rule; baseline + harvested updates → store reflects overlay). Trace: FR2.
- [x] **Step 9** — Implement stable journey id assignment/lookup aligned with draft `serviceGUID` semantics (document mapping). Trace: FR3.1, FR3.2.
- [x] **Step 10** — Write/run identity tests (same journey remains addressable after updates without relying solely on ephemeral Darwin `serviceId`). Trace: FR3.
  - **Optional (conditional on earlier POC evidence):** if Darwin `serviceId` / `rid` (or equivalent) appears in harvested Kafka / `server/poc/kafka` findings and can be tied to a published journey, implement a mapping from that visible service id to the stable `serviceGUID` and cover it with a test. If the earlier POC shows **no usable traces**, skip the mapping and note that in the code summary — do not invent a link.

- [x] **Step 11** — Demo script: load fixture schedule, replay fixtures, print/query journeys by stable id from DB; document run steps in README. Trace: FR4.1, FR4.2, NFR1.1.
- [x] **Step 12** — Environment/build: `.env.example` if needed (no secrets hardcoded); keep demo runnable offline. Trace: NFR1.1.
- [x] **Step 13** — Documentation + `source-manifest.json` + `traceability.json` + tick plan checkboxes. Trace: all FR/NFR coverage.

## Explicitly not in this plan

- HTTP `/v1` server or OpenAPI runtime (non-binding note only)
- Live RDM Kafka / live CIF SFTP for the pass/fail demo
- Android app changes
- AWS services
