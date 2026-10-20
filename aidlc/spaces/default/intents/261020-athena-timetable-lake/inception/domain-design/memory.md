<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->
- 2026-10-20T17:05:00Z — Domain Design questions cover only leftover choices: component split, receipt/archive sharing, empty-lake MCA path, overlay bind order, service_guid mint, columnar layout. Locked FR/story answers are not re-asked.
- 2026-10-20T17:46:00Z — Q1 A three components; Q2 B separate buckets and receipt shared with the external CFA supply; Q3 zip with MCA is full update else CFA or error log; Q4 rid then service_day and log; Q5 SVC- stable id per CIF identity; Q6 Parquet one Glue table per grain. CfaIntake still owns MCA full update even though the Q1 label said CFA.
- 2026-10-20T19:10:00Z — Revision for review-01 R-01–R-06: named CfaIntake publish set with overlay/status/forecast/aliases merge-preserved; LocationMap write-on-MCA intact-on-CFA; OverlayProjection one row per (trainUid, calendarDate) with type skip by column projection; ServiceAlias and LocationMap are Glue tables; KafkaOverlay writes rid aliases; FeedSequence cleared on MCA so next CFA seeds 990; YAML intra-PlanQuery refs added; CfaIntake shapes CIF/CFA, PlanQuery persists.

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
