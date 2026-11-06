## Review

**Verdict:** NOT-READY
**Reviewer:** aidlc-product-lead-agent
**Date:** 2026-11-01T08:51:00Z
**Iteration:** 1

### Findings

| ID | Severity | Location | Finding | Required action | Status |
|---|---|---|---|---|---|
| R-01 | Critical | aidlc/spaces/default/intents/261025-local-timetable-viewer/inception/user-stories/stories.md > US1.1 opening paragraph and Out of the stories | The kept stories assume a pre-filled database and start at the pair view. Current requirements say done is the empty screen, the MCA load, the pair page, and the network page. FR8.1–FR8.6, FR9.1–FR9.6, and the load-success gate in FR1.1 and FR5.1 have no story, no acceptance criterion, and no row in traceability.json. A developer cannot implement the empty screen, load button, progress poll, MCA file pick, refused header, in-flight singleton, or replace from these stories. QA cannot write a pass/fail test for that flow. | Add Given/When/Then stories for FR8 and FR9, and change US1.1 and US2.1 so train asks happen only after a successful load. Until then, do not treat the kept stories as covering approved done. | New |
| R-02 | Major | aidlc/spaces/default/intents/261025-local-timetable-viewer/inception/user-stories/stories.md > AC2.1.4 and US2.1 | FR5.5–FR5.7 are not in the stories. AC2.1.4 says a later failed ask leaves the screen unchanged. FR5.6 requires the status bar to say `comms is lost` on a hang, a closed connection, or an unreadable body, with no retry until the next interval. FR5.5 (only the latest success may paint; an older success is discarded) and FR5.7 extra empty sentences (`No problems`, `Nothing in this filter`, `Nothing matches`) have no criteria. QA cannot test those rules. A developer following AC2.1.4 would omit the comms status line. | Add acceptance criteria for FR5.5–FR5.7. Replace AC2.1.4's "the screen does not change" with the comms-lost status rule. | New |
| R-03 | Major | aidlc/spaces/default/intents/261025-local-timetable-viewer/inception/user-stories/stories.md > US1.2 AC1.2.5 and US3.1 | FR2.3, FR6.4, FR7.1–FR7.3, and the MCA-writer and MCA-test clauses of NFR3 and NFR4 have no story coverage. AC1.2.5 names station codes on the network view but never says that view asks on open and on each interval after a successful load even when those boxes are empty. US3.1 keeps "the file" but does not name `apps/local-timetable/db/timetable.sqlite` or the post-load empty overlay tables. A developer reusing the pair-view "do not ask until both codes are filled" rule would get FR2.3 wrong. | State FR2.3 on US1.2. State FR6.4 as a successful empty pair list. Name the store path and post-load overlay emptiness. Extend US3.1 or the load story with the NFR3/NFR4 MCA-writer and synthetic-MCA test checks. | New |

### Summary

The kept stories still describe a reader of an already-filled file. The approved requirements now make the empty screen and that day's MCA replace half of done, and they add comms-lost, last-success paint, and a network poll that does not wait for station codes. Do not start engineering from these stories unless the human either requests those stories or explicitly accepts the gap.
