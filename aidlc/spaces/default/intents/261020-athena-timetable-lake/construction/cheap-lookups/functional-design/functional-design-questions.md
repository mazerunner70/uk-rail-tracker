# Functional Design Questions — cheap-lookups

This Bolt locks the Cheap lookups chapter of `docs/athena-timetable-lake.md` in place. It does not rewrite CFA, Environments, or Kafka archive/overlay headings. Overlay partition keys, columns, and the overlay worked example stay in the Kafka heading (kafka-archive-overlay Q3 A). Remaining Construction is Functional Design only. No UI in this unit.

Already locked and not re-asked: four lookups (CIF identity, stop sequence, service day, live overlay); station-plus-time-window is not a cheapest-scan target; Parquet plus one Glue table per grain; STP winner C then O then P then N; `service_guid` as already written; overlay item 4 listed here and specified under Kafka; fixture query must not scan every journey or every Kafka object.

Upstream: `unit-of-work.md` U3, `unit-of-work-story-map.md` US3.1, `requirements.md` FR3 NFR1, `components.md` PlanQuery, `contract-summary.md` C2 C3.

### Question 1: What does this Bolt do to the existing Cheap lookups heading?

The file already has the Glue table list, the four-item set, plan-lookup table, worked example, and fixture query. Delivery Planning chose in-place heading lock rather than replacing the architecture file from blank.

A. Keep the current Cheap lookups heading as the lock — only edit if it drifts from U3 boundaries or C2/C3
B. Tighten the cheap-lookups chapter in place against C2/C3 even if that means rewriting paragraphs inside that owned heading
C. Replace the owned heading from a blank draft
X. Other (please specify)

[Answer]:A

### Question 2: Which headings may this Bolt edit?

U3 owns Cheap lookups. Overlay columns stay in Kafka archive/overlay. CFA and Environments stay with U1.

A. Only Cheap lookups
B. Those plus the Kafka Overlay cheap lookup table, because item 4 is listed here
C. The whole file
X. Other (please specify)

[Answer]:A

### Question 3: Do plan-grain partition keys stay as written?

The chapter already partitions journey and calling_points on `toc` + `run_date`, and service_day on `toc` + `calendar_date`. C2 assigns those keys to this unit. Units review R-01 (accepted) recorded the same split.

A. Keep `toc` + `run_date` / `calendar_date` as the plan-grain partition keys
B. Change a plan-grain partition key (please specify which)
X. Other (please specify)

[Answer]:A

### Question 4: Confirm the four-item list and what this heading does not own

Exact four: CIF identity, stop sequence, service day, live overlay. Station-plus-time-window is not a cheapest-scan target. Item 4 is listed here; overlay keys, columns, and worked example stay in the Kafka heading.

A. Confirm — keep that list and that split
B. Change the list or pull overlay keys into this heading (please specify)
X. Other (please specify)

[Answer]:A

### Question 5: Confirm C2/C3 names this Bolt must not change

C2: Glue names `journey`, `calling_points`, `service_day`; grains and identifiers as already written; STP winner C O P N. C3 overlay identity `(train_uid, calendar_date)` and overlay columns stay named by Kafka archive/overlay.

A. Confirm — do not rename or remove those in this Bolt
B. Change a name (please specify which)
X. Other (please specify)

[Answer]:A

### Question 6: Is the worked example and fixture train the lock?

The chapter already uses TOC `LM`, `train_uid` `C12345`, date `261020`, `stp_indicator` `P`, `service_guid` `SVC-C12345-261020-P`, and says the fixture query reads only those `toc=LM` + date partitions plus the overlay partition in the Kafka chapter.

A. Keep that example and fixture as the lock
B. Change the example train or date (please specify)
X. Other (please specify)

[Answer]:A

## Sources

- `docs/athena-timetable-lake.md` — existing Cheap lookups chapter
- `unit-of-work.md`, `unit-of-work-story-map.md` — U3 / US3.1
- `contract-summary.md` — C2 C3
- `components.md` — PlanQuery
- `requirements.md` — FR3 NFR1
- `delivery-planning-questions.md` — Q3 A in-place; Q5 A overwrite worry
- `kafka-archive-overlay/functional-design/functional-design-questions.md` — Q3 A overlay keys stay in Kafka

## Assumptions & Open Questions

- CFA, Environments, TOC extension, Later package layering, Deferred, and Kafka archive/overlay headings stay with their owning Bolts.
- None.
