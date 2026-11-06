<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
2026-10-25T14:21:00Z — human chose Focused scan. Snapshot paths server/poc/live-sched, store_generation sha256:125731cd588e65aa541d751cf6f93b0f83e2fad194ce475567120ae7e4a9a540, source_fingerprint git:99dd493302d01daca4b62fdaa26dee42ac41aca2. Developer scan limited to that path.
- 2026-10-25T14:55:00Z — Kept server/poc/live-sched as the only analyzed path and did not also list it under shallow; this run re-verified that directory, so copying the prior analyzed path into shallow would mark the same directory as both deep and skimmed. Prior shallow paths stay. fixtures, data, output, and docs under live-sched were added. The duplicate server/poc/schedule entry was collapsed to one.
- 2026-10-25T14:55:00Z — Preserved 2026-10-20 live-sched prose this run did not re-read, and labeled it. That includes the per-module test locks for modules inventoried by name only, and the full-MCA counts. This run counted 45 def test_ functions. The prior store said 44. Which function was added was not reconciled.
- 2026-10-25T14:55:00Z — Recorded the local timetable server and UI as absent. The page's three views are described against the existing SQLite file. No new component was invented for them.
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->

## Deviations
2026-10-25T14:30:00Z — minted the scope fingerprint in the parent session after the architect shell could not run aidlc; pasted 99dd493302d01daca4b62fdaa26dee42ac41aca2 into both timestamp copies. Focused STALE merge kept server/poc/live-sched as the only analyzed path with no trailing slash. Compare returned COVERS. Published generation sha256:9fa93b2cba5ec0496040f2ba4d2cc57dd613df3d6af223b3d649d0ae82d5f211.
- 2026-10-25T14:55:00Z — Did not run codekb-scope-diff --compare or codekb-publish; the brief assigns compare and publish to the conductor and forbids writing the shared store.
- 2026-10-25T14:55:00Z — Left fingerprint empty. The mint command was blocked by the delegated-agent shell guard before it started. It did not fail for a missing repo flag. No hash was invented. Architecture security text does not name Kafka credential environment variables; those setting names stay in api-documentation.
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->

## Tradeoffs
- 2026-10-25T14:55:00Z — Kept the prior test-lock table instead of deleting locks for modules this run did not open. A rebuild from only the four opened modules would drop CFA and service-day behaviour the 2026-10-20 scan recorded.
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->

## Open questions
2026-10-25T14:16:00Z — project-root store is STALE (athena-timetable-lake; server/poc/live-sched changed). No repos array, so this is one workspace scan. Reuse is not offered. Waiting on full rescan versus focused scan.
- 2026-10-25T14:55:00Z — Conductor must paste mint stdout into both timestamp copies before publish. The fingerprint line is empty until then.
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
