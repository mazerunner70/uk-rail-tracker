<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
2026-10-27T19:45:00Z — NFR Design questions for mca-load are the two gaps the targets left: the SQLite filename under apps/local-timetable/db, and how howFar is counted while streaming. No hosted resilience, cache, or scale-out patterns.
2026-10-27T19:47:00Z — Q1 A the file is apps/local-timetable/db/timetable.sqlite. Q2 A howFar is percent of MCA bytes read.

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
