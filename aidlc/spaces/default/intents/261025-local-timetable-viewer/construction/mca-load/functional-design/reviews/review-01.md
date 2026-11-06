## Review

**Verdict:** READY
**Reviewer:** aidlc-architecture-reviewer-agent
**Date:** 2026-10-26T21:14:00Z
**Iteration:** 1

### Findings

| ID | Severity | Location | Finding | Required action | Status |
|---|---|---|---|---|---|
| R-01 | Major | aidlc/spaces/default/intents/261025-local-timetable-viewer/construction/mca-load/functional-design/functional-spec.md > W4 — Replace; rules.md > BR3.3, BR3.5 | The replace workflow names the outcome (seven tables, London Midland only, empty forecasts, empty overlay tables) but does not specify the business mapping from a day's MCA onto those tables. There is no rule for which CIF services become Journey rows (including STP / overlay choice when several BS records share a trainUid), how ServiceDay rows are produced from a running period and days-run, how serviceGuid is assigned, how CallingPoint.crs is obtained from TIPLOC so the query's CRS match can work, or what if anything is written to ServiceAlias and FeedMeta. A developer cannot implement the persist without inventing those rules, and a wrong guess breaks today's lists. | Add the persist mapping as business rules: which services are stored, how one running-day row is chosen, how serviceGuid is formed, how CRS is populated, and which optional tables stay empty versus receive load metadata. | New |
| R-02 | Major | aidlc/spaces/default/intents/261025-local-timetable-viewer/construction/mca-load/functional-design/entities.md > TimetableSqlite.path; functional-spec.md > BR1.3 | This unit owns the SQLite file shape, but the locked path `apps/local-timetable/db` is unresolved as file versus directory. unit-of-work.md says the database container bind-mounts the file under that directory; contract-summary.md and this entity treat that same string as TimetableSqlite.path. A writer that opens the directory as the database file fails; a writer that invents a filename can disagree with the query reader. | Name the actual SQLite file this unit writes (directory plus filename) and state that the query reads that same file. Keep the locked host location under `apps/local-timetable/db`. | New |
| R-03 | Minor | aidlc/spaces/default/intents/261025-local-timetable-viewer/construction/mca-load/functional-design/rules.md > BR3.2 | "Valid sequence" for CIF columns 33 to 39 is the refuse trigger and is never defined (non-blank, seven digits, or some other check). The unit-of-work sentence is copied, not made testable. | Define the accept/refuse check for those seven columns in one sentence. | New |
| R-04 | Minor | aidlc/spaces/default/intents/261025-local-timetable-viewer/construction/mca-load/functional-design/functional-spec.md > LoadProgress.state | After a server-process restart, LoadProgress is implied idle and does not inspect an existing TimetableSqlite. BR1.4 keeps the file, and the screen contract waits for succeeded before asking for trains, so a restart hides a good file until start runs again. | State the progress value on process start (idle, or succeeded when the file is already the seven-table store) so query and screen do not have to guess. | New |

### Validation Tool Results

| Tool | Result | Interpretation |
|---|---|---|
| sensor-required-sections (functional-spec.md) | NOT RUN (review-scope hook blocked shell). Manual read: Workflows, State machines, derived ER diagram, derived rules summary, Sources, and Assumptions are present. | Structural sections look complete. No tool verdict. |
| sensor-required-sections (entities.md) | NOT RUN (same hook). Manual read: fenced yaml entity block, Summary, Sources, Assumptions present. | Entity source-of-truth block is present and internally consistent aside from R-01/R-02. |
| sensor-required-sections (rules.md) | NOT RUN (same hook). Manual read: fenced yaml rules block with id, statement, category, applies_to, trigger, logic, violation, and source; summary table present. | Rule records are complete. BR3.2 remains underspecified (R-03). |
| sensor-upstream-coverage | NOT RUN (same hook). Manual read: functional-spec Sources lists unit-of-work, unit-of-work-story-map, requirements, components, and contract-summary. | Consumed contracts are cited. Cross-unit start/progress and seven-table ownership match contract-summary. |
| sensor-traceability | NOT RUN (same hook). Manual read: coverage lists AC3.1.1–AC3.1.6; OK targets BR1.3, BR1.4, BR3.1, BR5.1, BR5.2 exist in rules.md; remaining rules are reverse-explained. AC text is not in the passed contracts. | No orphan BR IDs in the file. Mechanical AC existence was not tool-verified. |
| linter / type-check | NOT RUN | No TypeScript or JavaScript in these artifacts. None invented to scan. |

### Summary

Boundaries, start/progress, fail-before-delete, and London Midland / no-CFA / empty-overlay rules match the locked Q&A and the shared contracts. The two Majors to weigh are the missing MCA-to-store mapping (R-01) and the unresolved SQLite filename under `apps/local-timetable/db` (R-02). Zero Critical and two Major, so READY for the gate; those two are the items to request changes on if the persist must be implementable without a later architect conversation.
