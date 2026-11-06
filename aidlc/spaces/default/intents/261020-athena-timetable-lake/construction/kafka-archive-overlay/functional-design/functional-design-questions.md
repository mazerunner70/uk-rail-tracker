# Functional Design Questions — kafka-archive-overlay

This Bolt locks the Kafka archive/overlay chapter of `docs/athena-timetable-lake.md` in place. It does not rewrite CFA, Environments, or cheap-lookups headings. Remaining Construction is Functional Design only. No UI in this unit.

Already locked and not re-asked: archive-before-classify on a separate env-qualified archive bucket; fail-open after the archive object exists; overlay outside the CfaIntake transaction; bind Darwin `rid` first then `service_day` `(train_uid, scheduled start date)`; log fallback and miss; miss keeps the archive, creates no journey, leaves booked times unchanged; no UID-wide `list_journeys()` scan; project only `TS` / `schedule` / `deactivated`; one overlay row per `(train_uid, calendar_date)`; `overlayType` is not an identity or partition key; reserved types stay in the archive and are not queryable overlay; `service_guid` as already written; C1 bind keys and grains.

Upstream: `unit-of-work.md` U2, `unit-of-work-story-map.md` US4.1, `requirements.md` FR4, `components.md` KafkaOverlay, `contract-summary.md` C1 C3.

### Question 1: What does this Bolt do to the existing Kafka archive/overlay heading?

The file already has Archive-before-classify, Overlay apply, Bind keys, Reserved types, and Overlay cheap lookup. Delivery Planning chose in-place heading lock rather than replacing the architecture file from blank.

A. Keep the current Kafka archive/overlay heading as the lock — only edit if it drifts from U2 boundaries or C1/C3
B. Tighten the Kafka chapter in place against C1/C3 even if that means rewriting paragraphs inside that owned heading
C. Replace the owned heading from a blank draft
X. Other (please specify)

[Answer]:A

### Question 2: Which headings may this Bolt edit?

U2 owns Kafka archive/overlay. Cheap lookups lists live overlay as item 4 and points at this chapter for columns. CFA and Environments stay with U1.

A. Only Kafka archive/overlay (including Overlay cheap lookup and the overlay worked example)
B. Those plus the cheap-lookups heading’s overlay row, because item 4 is listed there
C. The whole file
X. Other (please specify)

[Answer]:A

### Question 3: Where do overlay partition keys stay written?

Story US4.1 (AC4.1.5) says this Kafka chapter names partition keys, projected columns, and one worked example for the live-overlay cheap lookup. Contract C3 also says cheap-lookups authors the Athena projection of those overlay columns, and Bolt 3 authors plan-grain partition keys. The chapter today already names overlay keys `toc` and `calendar_date` with prefix `curated/overlay/toc=…/calendar_date=…/`.

A. Keep partition keys, projected columns, and the overlay worked example in this Kafka heading (cheap-lookups only lists item 4 and may point here)
B. Move overlay partition keys into cheap-lookups; this heading keeps bind, archive, and column names only
C. Duplicate the overlay lookup table in both headings
X. Other (please specify)

[Answer]:A

### Question 4: When a later `TS`, `schedule`, or `deactivated` message binds to the same train and calendar date, what happens to the one current overlay row?

Queryable overlay is one current row per `(train_uid, calendar_date)`. The chapter does not say whether a second live message overwrites, is ignored, or is ordered by event time.

A. Last successful overlay persist wins — later bound `TS` / `schedule` / `deactivated` replace the matching columns on that row
B. First successful persist wins — later messages for the same row are ignored (archive still kept)
C. Order by Kafka/event time in the payload when present; otherwise last persist wins
X. Other (please specify)

[Answer]:A

### Question 5: Confirm C1 consumer / C3 provider names this Bolt must not change

Consumer lock from C1: bind via `service_aliases` (`rid`) then `service_day` `(train_uid, calendar date)`; no UID-wide scan; no journey on bind miss; no rewrite of booked times. Provider lock from C3: Glue tables `overlay` and `service_aliases`; overlay identity `(train_uid, calendar_date)`; columns `train_uid`, `calendar_date`, `forecast_arrival`, `forecast_departure`, `deactivated`, `schedule_payload`; type skip is column projection, not partition-by-type; reserved types stay out of this schema.

A. Confirm — do not rename or remove those in this Bolt
B. Change a name (please specify which)
X. Other (please specify)

[Answer]:A

### Question 6: What does fail-open mean after the archive object exists?

CFA intake is fail-closed. Kafka overlay is fail-open after the raw archive object exists: a later classify or apply failure must not erase that object. The chapter does not say whether overlay persist is retried or skipped.

A. Archive object is the success line — classify or overlay persist failure is logged, the archive stays, consuming continues, and no overlay row is required for that message
B. Retry overlay persist until it succeeds (archive already exists either way)
C. Leave retry versus skip as a later implementer choice (Deferred)
X. Other (please specify)

[Answer]:A

## Sources

- `docs/athena-timetable-lake.md` — existing Kafka archive/overlay chapter
- `unit-of-work.md`, `unit-of-work-story-map.md` — U2 / US4.1
- `contract-summary.md` — C1 C3
- `components.md` — KafkaOverlay, OverlayProjection, ServiceAlias
- `requirements.md` — FR4 FR6.3 NFR4
- `delivery-planning-questions.md` — Q3 A in-place; Q5 A overwrite worry
- `cfa-receipt-apply/functional-design/functional-design-questions.md` — Q1 A keep-as-lock; Q2 A owned headings only

## Assumptions & Open Questions

- CFA, Environments, TOC extension, Later package layering, and Deferred headings stay with U1.
- Cheap-lookups heading waits for Bolt 3. Overlay partition keys stay in this Kafka heading (Q3 A).
- None.
