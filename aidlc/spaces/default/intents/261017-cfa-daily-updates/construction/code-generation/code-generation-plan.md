# Code Generation Plan — CFA Daily Updates (stage-level, zero-Unit)

**Target:** `server/poc/live-sched`  
**Scope:** poc / Minimal / brownfield  
**Requirements:** `inception/requirements-analysis/requirements.md` (FR1–FR5, NFR1–NFR4)

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
    },
    {
      "layer": "project",
      "text": "- venv should be built correctly when needed so dependency install does not pollute the system (learned 2026-10-11)"
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
  "input_sha256": "sha256:af9c3cb71c04b9fec41fa3dd3214facbdd701f518520ffb678ca7a9a185a53fe",
  "contract_sha256": "sha256:627f1c6c881c1bed89da0d92cd14c4fe5cf2f76b7e8131f1c0abd963bb2c7a71"
}
```

## Engineering assumptions (open requirements findings accepted at gate)

- **3-digit code source (R-01):** Read the feed/update sequence from the CFA **HD** record (CIF header). If HD is missing/unparseable, refuse with a clear error (do not guess from zip basename alone).
- **Eligible zips (R-02):** Process `*.zip` in the watch folder, lexicographic order. Empty folder → success exit with “nothing to process”. After a refuse, continue remaining zips; process exit code non-zero if any zip was refused or failed.
- **Delete count (R-03):** Report a distinct `deletes` count for BS `D` (in addition to total/new/override/duplicate).
- **Report contract (R-04):** Plain labeled CLI stdout lines are the only report format for this PoC.
- **Package:** Must not import `schedule_poc`.

## Layers omitted as out of requirements

- HTTP API / endpoint layer — N/A (CLI only)
- Frontend — N/A

## Implementation steps

### Step 1 — Project structure and production configuration skeleton
- [x] Add CFA-update modules under `live_sched/` (e.g. `feed_sequence`, `cfa_zip`, `cfa_apply`, `cfa_report`) without breaking existing `load` / `consume`
- [x] Wire a new CLI subcommand (e.g. `live-sched cfa-update` or `apply-daily`) for one-shot watch-folder processing
- Trace: FR1.1, FR1.3, NFR3

### Step 2 — Verify test runner
- [x] Confirm package `.venv` + pytest for `server/poc/live-sched`
- [x] Record exact unit-scoped command in `unit-test-instructions.md` (already drafted; re-verify at generation)
- Trace: Testing Contract runner step

### Step 3 — Data model / database behavior — implement
- [x] Persist previous-load 3-digit code in SQLite (new table or key-value meta); bootstrap seed **990** when absent
- [x] Never unlink/wipe DB on CFA path
- Trace: FR2.1, FR2.2, FR3.1

### Step 4 — Data model / database behavior — tests
- [x] Test seed 990 when empty; test update after successful apply
- Trace: FR2.1–FR2.2, FR2.5

### Step 5 — Repository / data access — implement
- [x] Repository helpers for get/set feed sequence; apply N/R/D schedule mutations on existing journey/calling-point store
- Trace: FR3.3, FR4.2

### Step 6 — Repository / data access — tests
- [x] Happy-path get/set sequence; apply new/revise/duplicate/delete outcomes for counting
- Trace: FR4.1–FR4.2

### Step 7 — Business logic — implement
- [x] Open zip → find CFA → parse HD sequence → gate (+1 mod 1000) → refuse or apply → update sequence → build report
- [x] Atomic refuse: no schedule mutations committed when sequence mismatches
- Trace: FR1.3, FR2.3–FR2.5, FR3.1, FR4, NFR1

### Step 8 — Business logic — tests
- [x] Accept expected next (e.g. 991 after seed 990); refuse wrong code; report counts; wrap 999→000
- Trace: FR2, FR4, FR5.1 (fixture/synthetic CFA where real zip unavailable in unit tests)

### Step 9 — CLI / intake — implement
- [x] One-shot scan of configured watch folder; leave zips in place
- [x] Env: watch folder (+ any bootstrap knobs) via root `.env` / `.env.example`
- Trace: FR1.1–FR1.4, NFR2, NFR4

### Step 10 — CLI / intake — tests
- [x] Empty folder; single eligible zip happy path (temp dirs); refuse path leaves DB sequence unchanged
- Trace: FR1, FR2.4

### Step 11 — Environment / build configuration
- [x] Document new env keys in root `.env.example`
- [x] Ensure venv/dev extras still install cleanly (project practice)
- Trace: NFR4

### Step 12 — Documentation and traceability
- [x] Update `code-summary.md` and `traceability.json` (FR/NFR → files/tests)
- [x] Keep existing suite green
- Trace: FR5.1, scope floor

## Story / requirement → step map

| Requirement | Plan steps |
|-------------|------------|
| FR1.* | 1, 9, 10 |
| FR2.* | 3–8 |
| FR3.* | 3, 5, 7 |
| FR4.* | 5–8 |
| FR5.1 | 8, 12 (real zip at Build-and-Test / manual demo) |
| NFR1–NFR4 | 7, 9, 11 |
