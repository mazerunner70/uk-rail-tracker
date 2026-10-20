# Units of Work

Three `spec` units that together write `docs/athena-timetable-lake.md`. They are document chapters, not running services. The later sibling package under `server/` is named here as three independently deployable services and is not a unit in this graph.

Upstream: `components.md`, `decisions.md`, `stories.md`, `units-generation-questions.md` (Q1 B, Q2 B, Q3 A, Q4 A, Q5 B, Q6 C).

## Unit IDs

| Unit ID | Name | Directory | Kind |
|---------|------|-----------|------|
| U1 | cfa-receipt-apply | u1-cfa-receipt-apply | spec |
| U2 | kafka-archive-overlay | u2-kafka-archive-overlay | spec |
| U3 | cheap-lookups | u3-cheap-lookups | spec |

## U1 — cfa-receipt-apply

**Kind:** spec  
**Directory:** `u1-cfa-receipt-apply`  
**Deployment:** embedded in `docs/athena-timetable-lake.md`  
**Complexity:** L

**Boundaries.** Owns the CFA receipt → validate → apply chapter and the shared file shell: environments, heading map, TOC extension, later package layering, and Deferred / not in this document. Creates the file at that path. Does not own Kafka overlay projection or cheap-lookup partition keys.

**Responsibilities.**

- Name `{env}` for `dev` / `staging` / `prod` on every logical S3, Glue, and Athena object
- Specify MCA-or-CFA zip door, HD sequence columns 33–39, refuse paths, LM persist, STP winner, apply history, and the CfaIntake publish set
- Name TOC extension as the same intake without a new sequence mechanism
- Name later package layering: sibling under `server/`, no `schedule_poc`, fail-closed CFA / fail-open overlay, **three independently deployable services** (CfaIntake, KafkaOverlay, PlanQuery) for the Later Implementer
- List Deferred items (US7.1–US7.5)

**Implementation notes.** Markdown only. Does not stand up buckets, Glue, or a process. Object-store clients stay out of CIF parsers. Exact bucket strings stay an author choice in the file.

## U2 — kafka-archive-overlay

**Kind:** spec  
**Directory:** `u2-kafka-archive-overlay`  
**Deployment:** embedded in `docs/athena-timetable-lake.md`  
**Complexity:** M

**Boundaries.** Owns the Kafka archive / overlay chapter. Projects `TS`, `schedule`, and `deactivated` onto the single OverlayProjection row. Writes ServiceAlias when Darwin `rid` binds. Does not rewrite booked grains or own the cheap-lookups heading.

**Responsibilities.**

- Archive-before-classify on a separate env-qualified archive bucket
- Bind rid-first then service_day; log fallback and miss
- Name overlay columns and the Glue `overlay` / `service_aliases` tables
- Keep reserved Kafka types in the archive only

**Implementation notes.** Overlay is outside the CfaIntake transaction. Fail-open after the archive object exists. Forbids a UID-wide `list_journeys()` scan.

## U3 — cheap-lookups

**Kind:** spec  
**Directory:** `u3-cheap-lookups`  
**Deployment:** embedded in `docs/athena-timetable-lake.md`  
**Complexity:** M

**Boundaries.** Owns the cheap-lookups heading: the four Athena lookups (CIF identity, stop sequence, service day, live overlay). Overlay column names come from U2; this unit names how Athena reads them. Does not own intake or archive apply rules.

**Responsibilities.**

- Name partition keys, prefix/grain, and projected columns for journey, calling points, and service day
- Name the overlay cheap lookup as column projection on the single `(trainUid, calendarDate)` row
- Show one worked example per plan lookup and one for overlay
- Name the fixture query that must not scan every journey or every Kafka object
- State station-plus-time-window is not a cheapest-scan target

**Implementation notes.** Parquet plus one Glue table per grain is already locked (Domain Design Q6 / ADR-006). This unit specifies the lookup contracts, not a new format.

## Constraints (all units)

- Product path is `docs/athena-timetable-lake.md` only
- Kind `spec`: Construction writes the markdown and the contracts it needs, not a service scalability pack
- Later `server/` services are out of this graph (Q3 A)
- No `schedule_poc` import; no Athena modules inside `live_sched`
- This stage does not pick what to write first

## Sources

- `components.md`, `decisions.md` — CfaIntake, KafkaOverlay, PlanQuery; ADR-001–007
- `stories.md` — US1.1–US7.5
- `units-generation-questions.md` — Q1 B, Q2 B, Q3 A, Q4 A, Q5 B, Q6 C

## Assumptions & Open Questions

- Heading collisions in the one file are resolved by the ownership above; Functional Design names the exact heading strings.
- None.
