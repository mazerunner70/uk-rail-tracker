# Reverse Engineering Questions

## Code knowledge base scan

A code knowledge base exists for this project, but its analyzed paths have changed since it was built (intent athena-timetable-lake; changed path: server/poc/live-sched). A full rescan replaces it. A focused scan merges into it. Reuse is not offered because the store is out of date.

### Question 1: How should the scan run?

A. Full rescan — Rebuild the store covering the whole repo (replaces all 9 artifacts)
B. Focused scan — Scan this intent's area and extend the store; preserve prior prose outside it, demoting unverifiable deep coverage to shallow
X. Other (please specify)

[Answer]: B. Focused scan

## Learnings keep

Which notes should I keep for next time? (select all that apply)

A. human chose Focused scan. Snapshot paths server/poc/live-sched, store_generation sha256:125731cd588e65aa541d751cf6f93b0f83e2fad194ce475567120ae7e4a9a540, source_fingerprint git:99dd493302d01daca4b62fdaa26dee42ac41aca2. Developer scan limited to that path.
B. Kept server/poc/live-sched as the only analyzed path and did not also list it under shallow
C. Preserved 2026-10-20 live-sched prose this run did not re-read, and labeled it. That includes the per-module test locks for modules inventoried by name only, and the full-MCA counts. This run counted 45 def test_ functions. The prior store said 44. Which function was added was not reconciled.
D. Recorded the local timetable server and UI as absent. The page's three views are described against the existing SQLite file. No new component was invented for them.
E. minted the scope fingerprint in the parent session after the architect shell could not run aidlc
F. Did not run codekb-scope-diff --compare or codekb-publish
G. Left fingerprint empty. The mint command was blocked by the delegated-agent shell guard before it started. It did not fail for a missing repo flag. No hash was invented. Architecture security text does not name Kafka credential environment variables
H. Kept the prior test-lock table instead of deleting locks for modules this run did not open. A rebuild from only the four opened modules would drop CFA and service-day behaviour the 2026-10-20 scan recorded.
I. None
X. Other (please specify)

[Answer]: A, B, C, D, E, F, G, H (project only) — human said "keep notes"

## Anything to add

Anything to add for next time?

A. Nothing to add
B. Add a note
X. Other (please specify)

[Answer]: A. Nothing to add
