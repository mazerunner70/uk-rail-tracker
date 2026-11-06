## Review

**Verdict:** READY
**Reviewer:** aidlc-architecture-reviewer-agent
**Date:** 2026-11-01T09:23:00Z
**Iteration:** 1

### Findings

| ID | Severity | Location | Finding | Required action | Status |
|---|---|---|---|---|---|
| R-01 | Major | aidlc/spaces/default/intents/261025-local-timetable-viewer/inception/domain-design/components.md > NetworkPage and StationPairPage `behaviour` | **FR5.6** requires a failed comms ask (hang, closed connection, unreadable response) to keep held trains, show status bar text `comms is lost`, skip retry until the next interval, and clear the line on a later success. Neither page component’s behaviour mentions comms failure, the status bar, or how pair and network views share that rule. | Add explicit comms-lost behaviour to the page block(s) that own paint rules (at minimum `NetworkPage`, with `StationPairPage` tied to the same rule as other failure sentences). | New |
| R-02 | Major | aidlc/spaces/default/intents/261025-local-timetable-viewer/inception/domain-design/components.md > Component Summary table, NetworkPage row, Dependents column | The summary table lists **StationPairPage** as a dependent of **NetworkPage**, but the YAML catalogue sets `NetworkPage.dependents: []` and the Rationale states the pair page’s entity references are not a call back to `NetworkPage`. That contradicts the stage rule that dependents are components that **call** this one. | Align the summary table with the YAML (empty dependents for `NetworkPage`) or document a deliberate non-call relationship in the human view without labelling it a dependent. | New |
| R-03 | Minor | aidlc/spaces/default/intents/261025-local-timetable-viewer/inception/domain-design/components.md > entity NetworkAnswer | `NetworkAnswer` uses `calendarDate` alone as its identifier, but the network view is a list of many journeys on one calendar day; uniqueness of rows is not implied at this stage. | At Functional Design, define row identity (for example `serviceGuid` per row) or clarify that `NetworkAnswer` is a container entity whose children are keyed separately. | New |
| R-04 | Minor | aidlc/spaces/default/intents/261025-local-timetable-viewer/inception/domain-design/components.md > NetworkPage `behaviour` | **FR5.7** names several successful-empty sentences (`No problems`, `Nothing in this filter`, `Nothing matches`); behaviour only defers to mockup rules and notes headcode/Fleet filters have no data. | When mockups are wired in Functional Design, trace each FR5.7 sentence to the owning page or filter state so implementers do not infer from FR5.7 alone. | New |

### Validation Tool Results

| Tool | Result | Interpretation |
|---|---|---|
| Stage sensors (`required-sections`, `upstream-coverage`, `traceability`) | Not run (shell unavailable in review session) | Manual check: `components.md` includes YAML catalogue, mermaid diagram, summary, entity ownership, external dependencies, and rationale. `traceability.json` covers all four upstream story IDs with `OK`. |
| YAML well-formedness (manual) | PASS | Four unique components; all `depends_on` / `owned_by` refs resolve; no self-deps; call-graph `depends_on` / `dependents` symmetric in YAML; acyclic call graph (entity-only cross-page refs on `PairViewContext` do not add edges). |
| Requirements FR7–FR9 / team rules (manual) | PASS | **FR8** maps to `McaInitialisation` (sole SQLite writer, MCA-only, no CFA). **FR9** maps to `NetworkPage` + `TimetableQuery` (empty screen, load via query, no direct screen→writer calls). **FR7** store path and seven tables appear as `TimetableSqlite` / overlay-empty behaviour. **NFR3** / project **Forbidden**: train asks and query tests leave the file unchanged on `TimetableQuery`; no `live-sched` / `schedule_poc`. `list_journeys()` exclusion stated on `TimetableQuery`. |

### Summary

The four-block split (writer `McaInitialisation`, orchestrating read-only query, two screen blocks) matches approved requirements and cleanly separates MCA replace from train reads. Two Major gaps remain for the human at the gate: comms-lost (**FR5.6**) is not yet owned in page behaviour, and the Component Summary table mislabels `StationPairPage` as a **call** dependent of `NetworkPage`. Neither blocks implementation from the YAML catalogue and ADRs; fixing them before Contract/Functional Design reduces rework on UI failure modes and dependency diagrams.
