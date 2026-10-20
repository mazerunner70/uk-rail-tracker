# Contract Design Questions

These settle the formal agreements across the three spec-unit edges. Already locked and not re-asked: three chapter units of one file (Units Q1 B), later `server/` services are named not built (Q2 B / Q3 A), kind `spec`, overlay depends on CFA chapter, lookups depend on both, no public runtime API in this intent, fail-closed CFA / fail-open overlay, separate receipt and archive buckets.

Units Generation review notes R-01–R-03 were accepted as risk at Approve. This stage does not re-open who authors partition keys.

Upstream: `unit-of-work.md`, `unit-of-work-dependency.md`, `components.md`, `requirements.md`.

### Question 1: What mechanism are the three chapter boundaries?

The edges are kafka-archive-overlay → cfa-receipt-apply, cheap-lookups → cfa-receipt-apply, and cheap-lookups → kafka-archive-overlay. This intent writes markdown, not running APIs.

A. Shared schema — heading ownership plus named grains / Glue tables / columns in `docs/athena-timetable-lake.md`
B. Treat each edge as a later HTTP or event API and write OpenAPI / AsyncAPI now
X. Other (please specify)

[Answer]:A

### Question 2: Do we also pin the later service calls in this contract set?

Domain Design has CfaIntake and KafkaOverlay call PlanQuery synchronously. Those services are not units in this graph.

A. This contract set is only the three chapter edges. Name the later sync calls as deferred for a later intent.
B. Add two future contracts now (CfaIntake → PlanQuery, KafkaOverlay → PlanQuery) as named specs the Later Implementer must honour
X. Other (please specify)

[Answer]:A

### Question 3: Who owns each shared-schema spec?

A. The provider chapter owns the spec: cfa-receipt-apply owns the file shell and current-plan grain names; kafka-archive-overlay owns overlay and alias names. cheap-lookups owns no provider spec.
B. One owner for the whole file (cfa-receipt-apply) including overlay table names
X. Other (please specify)

[Answer]:A

### Question 4: How do breaking changes to the architecture file get agreed?

A. Additive headings and new optional columns are safe. Renaming or removing a heading, grain, or column needs an edit both consumer chapters accept before it lands.
B. The file has no versioning rule; Construction may rewrite headings freely
X. Other (please specify)

[Answer]:A
