# Units Generation Questions

These settle how CfaIntake, KafkaOverlay, and PlanQuery are grouped into units this piece of work will build. Already locked and not re-asked: three components (Domain Design Q1 A), architecture first with implementation later (practices Q2), walking skeleton off, document-delivery stories, `docs/athena-timetable-lake.md` as this intent’s product, fail-closed CFA / fail-open overlay, separate receipt and archive buckets.

This stage describes what can depend on what. It does not pick what to ship first.

Upstream: `components.md`, `decisions.md`, `requirements.md`, `stories.md`.

### Question 1: What units does this piece of work produce?

Construction for this intent writes the architecture file, not a running lake. The unit list here is what later design and code-generation stages will fill.

A. One spec unit: `docs/athena-timetable-lake.md` covering CfaIntake, KafkaOverlay, and PlanQuery
B. Three spec units: one per document chapter (CFA receipt → validate → apply, Kafka archive/overlay, cheap lookups) that together make the one file
C. Start the sibling package now as three service units (CfaIntake, KafkaOverlay, PlanQuery as code)
X. Other (please specify)

[Answer]:B

### Question 2: How should the later sibling package under `server/` be packaged?

Domain Design left process-versus-services to this stage. The answer is named in the architecture for the Later Implementer; this intent still does not stand the package up unless Question 1 is C.

A. One process, three code blocks over shared grains (same grain as live-sched today)
B. Three independently deployable services
C. Name one sibling package under `server/` and leave process-versus-services to the later implementer
X. Other (please specify)

[Answer]:B

### Question 3: Do later implementation slices sit in this unit graph?

Practices said implementation can land in stages against the architecture. Those stages can be later pieces of work, or units in this graph that Construction would then try to fill.

A. This graph is only what this intent builds. Later package work is later intents.
B. Add dependent service units now so a later plan can sequence them, even if this intent only writes the spec
X. Other (please specify)

[Answer]:A

### Question 4: What design set should the architecture unit carry into Construction?

A `spec` unit writes the contract in place and skips service-only design packs (scalability, a running business-logic model). The full matrix still writes those packs even when the only repo output is markdown.

A. spec — Construction writes the architecture markdown and the contracts it needs, not a service scalability pack
B. Full design matrix (untagged) — still markdown-only output, but Construction also writes entity and NFR design docs for the later package
X. Other (please specify)

[Answer]:A

### Question 5: Who owns the shared headings in the one architecture file?

Question 1 is three chapter units that together make `docs/athena-timetable-lake.md`. The heading map also has environments, TOC extension, later package layering, and Deferred — plus the file existing at that path (US1.1, US5.1, US6.1, US7). Those need an owner so stories are not left on the floor.

A. A fourth spec unit for the file shell (environments, heading map, deferred, later package layering); the three chapters depend on it
B. Fold shared headings into the CFA receipt chapter
C. Fold shared headings into cheap lookups
X. Other (please specify)

[Answer]: B

### Question 6: How do the chapter units depend on each other?

This is topology only — what must exist for another chapter to be specified — not the order Delivery Planning will ship work.

A. Independent — no depends_on between chapters
B. Overlay and lookups both depend on the CFA / current-plan chapter only
C. Overlay depends on the CFA / current-plan chapter; lookups depend on both (plan grains and the overlay table)
X. Other (please specify)

[Answer]:C
