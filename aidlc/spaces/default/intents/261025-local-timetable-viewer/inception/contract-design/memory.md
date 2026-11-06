<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
2026-10-26T17:35:00Z — Two inter-unit edges to pin: screen to timetable-query, and timetable-query to mca-load. The SQLite file is the shared store the load writes and the query reads. There is no public API.
2026-10-26T18:38:00Z — Screen to query is REST with JSON bodies as needed and transport compression as needed. Query starts the load by a function call in the same process. Query owns the REST spec. MCA load owns the SQLite file shape. A hang is a failed ask, held trains stay, status bar says comms is lost, no retry. Progress is idle/running/succeeded/failed plus how far and a sentence. No contract version number.

## Deviations
2026-11-01T09:35:00Z — After advisory review NOT-READY (one pass only), updated contract-summary to address R-01–R-07 without a second reviewer iteration; findings quoted at the human gate per review budget.

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
