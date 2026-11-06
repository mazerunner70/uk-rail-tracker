<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->
- 2026-10-20T20:46:28Z — Question 5 C deferred mid-apply failure; Question 7 A kept AC2.1.7. Functional Design follows Q7: previous plan queryable, no partial grain. Question 4 C stores apply-history identities as CIF triples only.
- 2026-10-20T21:17:31Z — Overlay Q1–Q6 all A. Keep-as-lock; this Bolt edits only the Kafka archive/overlay heading; partition keys stay in that heading; last successful persist wins; C3 names unchanged; fail-open after the archive object exists.
- 2026-10-20T21:17:31Z — Cheap-lookups Q1–Q6 all A. Keep-as-lock with no heading rewrite; this Bolt edits only Cheap lookups; plan keys stay toc + run_date / calendar_date; four-item list with overlay pointing at Kafka; C2/C3 Glue names unchanged; fixture LM / C12345 / 261020.

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->
- 2026-10-20T20:46:28Z — Q1 A keep-as-lock: only two in-place sentences on the CFA heading (CIF-triple identities; no partial grain). Did not rewrite overlay or cheap-lookups headings.
- 2026-10-20T21:17:31Z — Overlay Q1 A: only two in-place sentences on the Kafka heading (fail-open after archive; last persist wins). Did not rewrite CFA or cheap-lookups headings.
- 2026-10-20T21:17:31Z — Cheap-lookups Q1 A: no architecture heading rewrite. Partition keys, four-item list, and fixture stay as the existing Cheap lookups lock.

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->
- 2026-10-20T21:17:31Z — Overlay Q4 A last-persist-wins rather than merge; Q6 A skip overlay persist after archive rather than retry-block consume.
- 2026-10-20T21:17:31Z — Cheap-lookups Q4 A lists live overlay as item 4 and points at the Kafka chapter instead of copying overlay columns into this heading.

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
