<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
2026-10-26T21:05:00Z — Construction questions for mca-load are the five gaps prior stages left: MCA path, start while running, start after success, howFar while running, and a missing MCA at start. Everything else for this unit is already locked.
2026-10-26T21:10:00Z — Q1 C directory of MCA files for today. Q2 A return running progress. Q3 A a later start replaces again. Q4 A howFar is a percent as text. Q5 A missing MCA fails and leaves the SQLite file. Follow-up on how the filename or header matches today, and where the directory lives.
2026-10-26T21:12:00Z — Q6 B today's date in the filename as YYYYMMDD. Q7 B the directory is the fixed host path apps/local-timetable/mca. Exactly one filename containing today's YYYYMMDD in Europe/London is required; zero or more than one fails and leaves SQLite unchanged. Locate and open the MCA, and accept the HD sequence, before any unlink.

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
