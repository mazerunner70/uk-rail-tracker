<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->

- 2026-10-04T14:21:43Z — Combined the stage's eight topic areas into four questions because depth is Minimal. Problem and customer share one question, success and trigger share one, and stakeholder, decision rights, and reporting share one, so a solo prototype can answer without invented roles. The fourth question keeps the proof-of-concept workflow size separate from the product boundary.
- 2026-10-04T14:51:52Z — Follow-ups closed the open assumptions: TOC is LM (London Midland), material is limited to `server/poc`, consumer is the `android` app, and the stakeholder map stays solo. Assumption sections are now None.

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->

- 2026-10-04T14:33:04Z — Kept the intent at problem level and left TOC identity, unnamed client app, and later supporting-POC detail as assumptions rather than inventing them. Architect lens: stable journey identity and local proof-before-AWS are the framing constraints, not a stack choice.

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->

- 2026-10-04T14:33:04Z — Confirm which TOC is in the first cut, and when supporting POC detail beyond `server/poc` should be brought in. Resolved by Q5–Q8.
