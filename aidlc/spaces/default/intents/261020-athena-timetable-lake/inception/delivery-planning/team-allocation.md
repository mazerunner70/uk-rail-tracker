# Team Allocation

A Bolt is one build pass over a piece of `docs/athena-timetable-lake.md`. A mob is the group that owns that pass. Team formation (stage 1.5) was skipped on this classic workflow, so there is no named human mob.

## Ownership

All three Bolts run from this session. Remaining Construction is Functional Design only (NFR, infrastructure, code generation, and build-and-test are out of this intent), so each Bolt's remaining pass is the Functional Design lock of that unit's chapter, still from this session.

| Bolt | Unit | Owner |
|------|------|-------|
| 1 CFA receipt → validate → apply | U1 `cfa-receipt-apply` | this session (Functional Design) |
| 2 Kafka archive/overlay | U2 `kafka-archive-overlay` | this session (Functional Design) |
| 3 Cheap lookups | U3 `cheap-lookups` | this session (Functional Design) |

Walk is already recorded as serial and unit-major: Bolt 1 finishes before Bolt 2 starts; Bolt 2 finishes before Bolt 3 starts.

## Program Board

One session, one owner. No Program Board analog (that board appears when more than one mob shares the walk).

Trunk-based working from `team-practices.md`: work lands on `main` through short-lived branches and squash-merge. There is no deploy in this intent.

## Sources

- `team-practices.md` — Way of Working; Walking Skeleton off; Deployment skipped
- `unit-of-work.md` — three spec units
- `bolt-plan.md` — three serial Bolts
- `delivery-planning-questions.md` — Q1 A (one Unit per Bolt)
