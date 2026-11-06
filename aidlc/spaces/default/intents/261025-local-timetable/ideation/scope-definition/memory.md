<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->
- 2026-10-25T13:20:00Z — Two questions only: file paths, and what is in versus out. Who it is for, the three views, and docs-only were already answered.
- 2026-10-25T13:25:00Z — File answer says one document under docs/future-intents named lcocal-timetable.md. Intent says two pages, future-intents note and local-timetable.md. Asked which files and which name before writing the scope.
- 2026-10-25T13:30:00Z — Confirmed one file, docs/future-intents/local-timetable.md, holding both the future-intents note and the service description. Ranked the page with MoSCoW because this pass is one fixed page and there are no usage numbers to score.

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
