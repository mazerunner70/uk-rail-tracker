# Risk and Sequencing Rationale

A Bolt is one build pass over a piece of `docs/athena-timetable-lake.md`. This file is why the three Bolts run in CFA → overlay → cheap lookups order, and why that order was not re-scored.

## Heuristic

**Risk-first** (Boehm spiral): lock the highest-uncertainty shared names before dependent chapters consume them. Question 2 A. Formal WSJF scoring (value + urgency + risk-reduction, divided by size) was offered and not chosen.

The unit graph in `unit-of-work-dependency.md` already has only one topological order. Bolt sequencing does not deviate from that order. The economic pick is which proof the first Bolt must make: CFA identity locked so overlay bind and lookups cannot invent a second identity.

Architect view on the graph: C1 and C2 are provided by `cfa-receipt-apply`; C3 is provided by `kafka-archive-overlay`; `cheap-lookups` provides none. Any overlay-first or lookups-first Bolt would consume names that do not yet have a provider. That would be a topological deviation, and none is proposed.

## Why this order

| Bolt | Risk it retires | What still waits |
|------|-----------------|------------------|
| 1 CFA chapter | Second identity; `{env}` and file-shell drift; Deferred list left unspecified | Overlay bind keys; lookup partition keys |
| 2 Overlay chapter | Bind inventing keys; overlay columns drifting ahead of the fourth lookup | Partition keys and fixture query on plan grains |
| 3 Cheap lookups | Lookups scanning every journey or every Kafka object; R-01 partition-key authorship leaking into other chapters | Later `server/` services (out of this intent) |

Walking skeleton (Cockburn thin end-to-end running slice) is off per `team-practices.md`. None of these Bolts is that slice. A first Functional Design review is not a working skeleton.

## Tension: in-place edit vs overwrite

Question 3 A says Construction edits `docs/athena-timetable-lake.md` in place. Question 5 A says the top worry is Construction overwriting decisions already in that file. Those do not cancel each other: each Bolt may edit only its owned headings, against unit boundaries and C1–C3. It must not replace the file from blank (Question 3 C) and must not rewrite sibling chapters.

Mitigation: Functional Design names exact heading strings from `mockups.md` and diffs only the owned block. Overlay and cheap-lookups headings stay untouched during Bolt 1.

## Risk register

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| Functional Design overwrites locked decisions in the existing file | Medium | High | In-place chapter lock only; Bolt 1 owns the file shell and Deferred list, not overlay or lookups headings |
| Open contract finding R-01 (`service_aliases` / overlay partition keys) leaks into the wrong chapter | Medium | Medium | C2 assigns lookup partition keys to `cheap-lookups`; C3 overlay identifier stays on the overlay chapter; confirm during Bolt 3 |
| Heading collisions in the one file | Low | Medium | `mockups.md` heading map; U1 owns the map; later Bolts fill reserved slots |
| Scope creep into standing up S3, Glue, or Athena | Low | High | Remaining Construction is Functional Design only; US7.4 stays Deferred; `requirements.md` FR1 is document-only |

No WSJF weights are recorded. Job sizes are one spec chapter each (U1 L, U2 M, U3 M in `unit-of-work.md`).

## Sources

- `unit-of-work-dependency.md` — only valid order is CFA → overlay → lookups
- `contract-summary.md` — C1–C3 ownership; R-01 accepted
- `team-practices.md` — walking skeleton off
- `stories.md`, `mockups.md` — document check, heading map
- `components.md` — later CfaIntake / KafkaOverlay / PlanQuery not in this walk
- `delivery-planning-questions.md` — Q1 A, Q2 A, Q3 A, Q5 A, Q6 A
