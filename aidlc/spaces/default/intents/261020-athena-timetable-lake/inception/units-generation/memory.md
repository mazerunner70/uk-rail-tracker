<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->
- 2026-10-20T18:20:00Z — Units Generation questions cover only leftover packaging: this intent's unit set, later sibling process-versus-services, whether later impl sits in this graph, and spec versus full design matrix. Locked Domain Design components and architecture-first practices are not re-asked. This stage will not pick a build order.
- 2026-10-20T18:38:00Z — Human answered Q1 B three chapter specs, Q2 B later package as three deployable services, Q3 A this graph is this intent only, Q4 A spec design set. Follow-ups Q5 (shared heading owner) and Q6 (chapter depends_on) because one file plus US1.1/US5.1/US6.1/US7 have no chapter home, and cheap overlay lookup needs a named overlay grain.
- 2026-10-20T18:42:00Z — Q5 B fold shared headings into the CFA receipt chapter (three units, not four). Q6 C overlay depends on CFA chapter; lookups depend on CFA chapter and overlay chapter. Later three-service packaging is named in the CFA unit's later-package heading; those services are not units in this graph.
- 2026-10-20T18:44:00Z — Plan approved. Three spec units cfa-receipt-apply, kafka-archive-overlay, cheap-lookups. Shared shell folded into U1. DAG: U2→U1, U3→U1+U2. No parallel pair. Delivery view: spec kind keeps Construction on the markdown contracts; later three services stay named, not built.

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
