# Delivery Planning Questions

These settle the build-pass order for the three architecture chapters. Already locked and not re-asked: walking skeleton off (architecture-first, no running intake-to-query slice); three spec units of one file (`cfa-receipt-apply`, then `kafka-archive-overlay`, then `cheap-lookups`); later `server/` services are later intents; remaining Construction after this plan is Functional Design only (markdown chapter lock, no NFR, infrastructure, code generation, or build-and-test); serial unit-major walk; team-formation skipped so Construction is AI-run from this session.

A Bolt here is one build pass over a piece of that file, ending in a document check of the assigned chapter(s). Overlay cannot be specified before the CFA chapter; cheap lookups cannot be specified before both. Parallel chapter writes are not on the table.

Upstream: `unit-of-work.md`, `unit-of-work-dependency.md`, `unit-of-work-story-map.md`, `contract-summary.md`, `team-practices.md`.

### Question 1: How big is one Bolt?

The dependency graph allows only CFA chapter → overlay chapter → cheap lookups. We can still bundle adjacent chapters into one pass.

A. Three Bolts in that order: CFA receipt → validate → apply, then Kafka archive/overlay, then cheap lookups
B. One Bolt that finishes the whole of `docs/athena-timetable-lake.md` in a single pass
C. Two Bolts: CFA chapter first, then overlay and cheap lookups together
X. Other (please specify)

[Answer]:A

### Question 2: What should the first Bolt prove?

Practices already skipped a running lake slice. This is the remaining economic pick: which chapter-write we do first, and why.

A. Risk-first — lock the CFA receipt chapter first so overlay bind and lookups cannot invent a second identity
B. File-value — the existing architecture markdown is the product; finish it in one pass (pairs with Question 1 B)
C. Score the three chapters with WSJF (value + urgency + risk-reduction, divided by size) before locking order
X. Other (please specify)

[Answer]:A

### Question 3: What does Construction do with the file that already exists at `docs/athena-timetable-lake.md`?

The chapters are already on disk. Remaining Construction still runs per unit unless we say otherwise here.

A. Edit that file in place — each Bolt locks its chapter against the unit boundaries and shared-schema contracts (C1–C3)
B. Leave the current file as the product — remaining Construction only adds plan and traceability around it, no chapter rewrite
C. Replace the file from blank during Construction
X. Other (please specify)

[Answer]:A

### Question 4: Is anything outside this session going to hold up finishing the chapters?

This intent does not stand up buckets, Glue, Athena, or a Kafka client. External CFA supply and the broker belong to a later package.

A. Nothing outside this session — finishing the three chapters needs no live zip, broker, or AWS account
B. We need the Timetable Information / CIF layout spec at hand before the CFA chapter is done
C. We need a sample CFA zip (synthetic is fine) as a fixture for the worked example
X. Other (please specify)

[Answer]:A sample data is available if questions arise, look in the pdfs for details

### Question 5: What worries you most about this build, so we tackle it in the first Bolt?

A. Construction overwriting decisions already in `docs/athena-timetable-lake.md`
B. The open contract findings — `service_aliases` columns and who authors overlay partition keys — leaking into chapter writes
C. Heading collisions in the one file
D. Scope creep into standing up S3, Glue, or Athena in this intent
X. Other (please specify)

[Answer]: A

### Question 6: When is a Bolt done?

Acceptance for this intent is a document check of `docs/athena-timetable-lake.md`, not a running lake.

A. The assigned chapter(s) match that unit's boundaries and C1–C3, and the assigned stories (US1.1–US7.5 as mapped) can be checked by reading the file
B. Same as A, plus the worked fixture query for one LM train on one calendar date is in the cheap-lookups chapter when that Bolt runs
C. Same as A, and the Inception phase-check report has no remaining GAP or ORPHAN rows
X. Other (please specify)

[Answer]:A

### Question 7: How do you want to staff Construction?

Remaining Construction is Functional Design of the three chapters, already set to run one unit after another. I can keep that in this session, or each of your teams can own a unit and approve its work independently.

A. This session — I run the work from here
B. Each team owns a Unit and approves independently
X. Other (please specify)

[Answer]: A

## Sources

- `unit-of-work.md`, `unit-of-work-dependency.md` — U1–U3, DAG CFA → overlay → lookups
- `unit-of-work-story-map.md` — US1.1–US7.5
- `contract-summary.md` — C1–C3; later PlanQuery calls deferred
- `team-practices.md` — walking skeleton off; serial Construction; no deploy in this intent
- `requirements.md`, `stories.md` — markdown-only product

## Learnings

Which notes from this Delivery Planning pass should we keep as a practice? Reply with all that apply, or none.

- Did not re-ask walking skeleton or parallel Bolts. Practices already skip a running intake-to-query slice
- Architect view: three Bolts one Unit each in DAG order is the only valid topology
- Folded WSJF into Question 2 option C instead of a standalone scoring-weight question. Three spec chapters of one markdown file do not need a weight-split unless the human asks for formal scoring.
- Question 4 answer was "A sample data is available if questions arise, look in the pdfs for details". Treated as A (nothing outside this session holds the chapters) plus on-hand sample/PDF references, not as a live-zip or spec-at-hand gate (B/C).
- Question 3 A (edit the architecture file in place) vs Question 5 A (top worry is overwriting that file). Kept in-place chapter lock rather than leaving the file untouched or replacing it from blank. Each Bolt may edit only its owned headings against C1–C3.

[Answer]: keep learnings (all five, this project)

### Anything to add for next time?

- Nothing to add
- Add a note

[Answer]: Nothing to add

## Assumptions & Open Questions

- Construction Checkpoints stay enabled and unit-major/serial as already recorded. Staffing is this session (Question 7 A).
- None.
