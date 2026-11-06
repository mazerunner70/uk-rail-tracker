<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
2026-10-25T20:48:00Z — Three containers and four code blocks are already decided. The open points are how those blocks sit in the containers, and when the MCA replace runs.
2026-10-25T20:53:00Z — Q1 D: the MCA load is its own unit in the server container, the query is the other server unit, both pages are one screen unit, and the database container holds only the file. Q2 Other: the screen starts empty, a button starts the MCA load, and the screen polls the server for progress.
2026-10-25T20:58:00Z — Q3 B: the screen calls only the query unit, and that unit starts the MCA load and reports progress. Q4 B: the empty screen uses the same 30 second interval for load progress, and it asks for trains only after the load succeeds.
2026-10-25T21:58:00Z — Plan approved. Three units: mca-load and timetable-query share the server container. screen is its own container. The database container is not a unit. It keeps the SQLite file when the server stops, and the file is bind-mounted so a SQL client opens the host path.
2026-11-01T09:28:00Z — Modify after domain design: same three-unit chain; component mapping, comms is lost and interval refusal on screen, traceability U1–U3, db packaging under apps/local-timetable/db.

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
