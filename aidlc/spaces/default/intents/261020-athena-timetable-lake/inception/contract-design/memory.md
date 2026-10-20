<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->
- 2026-10-20T18:58:00Z — Contract Design questions cover leftover boundary mechanism, whether later PlanQuery sync calls are in this set, spec ownership, and breaking-change policy. Units Q1–Q6 and accepted review risks R-01–R-03 are not re-asked.
- 2026-10-20T19:05:00Z — Q1–Q4 all A. Three shared-schema contracts C1–C3 in the one architecture file. Later PlanQuery sync calls deferred. C2 records U3 as author of lookup keys to close the accepted R-01 gap without editing the approved unit page. Overlay example stays on C3 provider (U2).

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
