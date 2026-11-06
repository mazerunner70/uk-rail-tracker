## Review

**Verdict:** NOT-READY
**Reviewer:** aidlc-architecture-reviewer-agent
**Date:** 2026-11-01T09:46:00Z
**Iteration:** 1

### Findings

| ID | Severity | Location | Finding | Required action | Status |
|---|---|---|---|---|---|
| R-01 | Major | aidlc/spaces/default/intents/261025-local-timetable-viewer/construction/mca-load/functional-design/functional-spec.md > W4 — Replace; rules.md > BR3.3, BR3.5 | The replace workflow states outcomes (seven tables, London Midland only, empty forecasts, empty overlay tables) but does not define the business mapping from CIF/MCA content into `journeys`, `calling_points`, `service_day`, `service_aliases`, or `feed_meta`. There is no rule for which BS records become rows, how `serviceGuid` is formed, how `service_day` rows are derived for “today”, how `calling_points.crs` is populated from TIPLOC for downstream CRS matching, or what load metadata (if any) belongs in `feed_meta`. A developer must invent this mapping; a wrong choice breaks network and pair lists in contract-summary. | Add explicit persist rules (or a referenced algorithm section) covering service selection, STP/overlay choice, `serviceGuid`, `service_day` for the Europe/London calendar day, CRS on calling points, and `feed_meta` keys written on success. | New |
| R-02 | Major | aidlc/spaces/default/intents/261025-local-timetable-viewer/inception/requirements-analysis/requirements.md > FR8.3; construction/mca-load/functional-design/functional-spec.md > W3 — Fail before any replace step 2; rules.md > BR3.2 | FR8.3 requires failed-load sentences to distinguish an unreadable MCA (“the MCA cannot be read”) from a refused header (“the header was refused”). W3 step 2 and BR3.2 specify fail-before-delete and unchanged SQLite but never assign `LoadProgress.sentence` for header refusal; BR4.1 covers only the unreadable/missing MCA path. The query REST contract exposes `sentence` to the screen with no second channel. | Specify the exact `sentence` text (or a closed enum of allowed phrases) for header refusal in BR3.2 and W3, distinct from BR4.1. | New |
| R-03 | Major | aidlc/spaces/default/intents/261025-local-timetable-viewer/construction/mca-load/functional-design/traceability.json > coverage AC3.1.1, AC3.1.4 | AC3.1.1 is three-container topology on this machine; mapping it to BR1.3 (SQLite path after a successful replace) does not trace that criterion. AC3.1.4 is reader test breadth (unit tests plus thin checks of all three views); mapping it only to BR5.1 (synthetic MCA for load tests) leaves most of the AC unowned in this unit’s functional design while marked `OK`. US3.1 spans mca-load, timetable-query, and screen per unit-of-work-story-map. | Reconcile coverage: mark container-topology and reader-test portions `N/A` or `Deferred` with explicit owning unit/stage, or add rules that actually implement the mca-load slice of each AC; do not mark `OK` without a matching BR. | New |
| R-04 | Minor | aidlc/spaces/default/intents/261025-local-timetable-viewer/construction/mca-load/functional-design/functional-spec.md > LoadProgress.state | The state machine defines transitions from `idle` but not when `idle` is returned after process restart while BR1.4 keeps an on-disk `TimetableSqlite`. contract-summary `start` may return `idle`, `running`, or `failed`; the screen waits for `succeeded` before train asks. Without an initial-progress rule, query and load can disagree after restart. | State the progress value on cold start (always `idle`, or `succeeded` when a valid seven-table file already exists). | New |
| R-05 | Minor | aidlc/spaces/default/intents/261025-local-timetable-viewer/construction/mca-load/functional-design/rules.md > BR3.2 statement vs logic | The rule statement still says “valid sequence” while logic requires columns 33–39 to be exactly seven decimal digits. Implementers can follow logic, but the statement contradicts requirements/unit-of-work wording and invites alternate interpretations in tests. | Align the BR3.2 statement with the seven-digit check used in logic (or document equivalence to upstream “valid sequence”). | New |

### Validation Tool Results

| Tool | Result | Interpretation |
|---|---|---|
| sensor-required-sections | NOT RUN (shell blocked by harness hook) | Manual read: functional-spec has Workflows, State machines, derived ER diagram, rules summary, Sources, Assumptions; entities and rules have fenced YAML source blocks plus summaries. |
| sensor-upstream-coverage | NOT RUN | Manual read: functional-spec Sources cite unit-of-work, requirements, components, and contract-summary; in-process start/progress and seven-table writer ownership match contract-summary §2–3. |
| sensor-traceability | NOT RUN | Manual read: all BR ids appear in coverage or reverse; OK targets reference existing BR ids. Semantic gaps remain (R-03). |
| linter / type-check | NOT RUN | No TS/JS snippets in these artifacts. |
| aidlc engine sensor traceability CLI | NOT RUN (same hook) | No mechanical orphan/gap output; manual review applied above. |

### Summary

Cross-unit boundaries (same-process start/progress, sole writer, fail-before-delete, LM-only, no CFA, empty overlay tables, full SQLite path) align with contract-summary and unit-of-work. Three Major gaps block implementability: missing MCA-to-store mapping (R-01), FR8.3 failure sentences for header refusal (R-02), and overstated AC traceability for US3.1 slices owned elsewhere (R-03). Verdict NOT-READY until those are addressed.
