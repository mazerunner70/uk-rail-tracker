# Story Map

Every `USx.y` maps to a Unit ID and directory. Acceptance remains a document check of `docs/athena-timetable-lake.md`.

## Assignments

| Story | Priority | Unit ID | Directory | Notes |
|-------|----------|---------|-----------|-------|
| US1.1 | Must | U1 | u1-cfa-receipt-apply | File exists, `{env}`, heading map |
| US2.1 | Must | U1 | u1-cfa-receipt-apply | CFA receipt → validate → apply |
| US3.1 | Must | U3 | u3-cheap-lookups | Four cheap lookups; overlay columns specified on US4.1 |
| US4.1 | Must | U2 | u2-kafka-archive-overlay | Archive, bind, three overlay types, overlay cheap-lookup columns |
| US5.1 | Should | U1 | u1-cfa-receipt-apply | LM first; TOC extension heading |
| US6.1 | Should | U1 | u1-cfa-receipt-apply | Sibling package; three later services; fail-closed / fail-open |
| US7.1 | Won't | U1 | u1-cfa-receipt-apply | Deferred list; reserved types also named in U2 |
| US7.2 | Won't | U1 | u1-cfa-receipt-apply | Deferred list; also stated in U3 cheap-lookups list |
| US7.3 | Won't | U1 | u1-cfa-receipt-apply | Deferred; apply history stays counts-only in U1 |
| US7.4 | Won't | U1 | u1-cfa-receipt-apply | Deferred; no runtime or CI in this intent |
| US7.5 | Won't | U1 | u1-cfa-receipt-apply | Deferred; Android and `docs/architecture.md` out |

## Cross-cutting

- **One file.** All three units write sections of `docs/athena-timetable-lake.md`. U1 owns the path and the heading map.
- **US3.1 / US4.1.** The fourth cheap lookup (live overlay) is listed in U3 and specified (keys, columns, example) in U2. U3 depends on U2 for those names.
- **US7.1.** Deferred on U1; reserved-type names also appear under the U2 overlay chapter (AC4.1.3).
- **US7.2.** Deferred on U1; U3 restates it is not a cheapest-scan target (AC3.1.3).

## Order inside each unit

Within-unit story order only (what a chapter must specify together). Not a Delivery Planning sequence.

- **U1:** US1.1 → US2.1 → US5.1 → US6.1 → US7.1–US7.5 (Deferred section)
- **U2:** US4.1
- **U3:** US3.1

## Coverage

| Check | Result |
|-------|--------|
| Every story assigned | Yes — US1.1–US7.5 |
| Every unit has stories | U1: US1.1, US2.1, US5.1, US6.1, US7.1–US7.5; U2: US4.1; U3: US3.1 |

## Sources

- `stories.md`
- `unit-of-work.md`

## Assumptions & Open Questions

- None.
