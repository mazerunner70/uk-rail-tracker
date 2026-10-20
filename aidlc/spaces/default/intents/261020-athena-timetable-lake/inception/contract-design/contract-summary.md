# Contract Summary

Formal agreements across the three spec-unit edges. Mechanism is shared schema in `docs/athena-timetable-lake.md` (Q1 A). Later CfaIntake / KafkaOverlay → PlanQuery sync calls are deferred (Q2 A). No public runtime API in this intent.

Upstream: `unit-of-work.md`, `unit-of-work-dependency.md`, `components.md`, `requirements.md`, `contract-design-questions.md`.

## Contracts

| # | Provider Unit | Consumer | Mechanism | Owner |
|---|---------------|----------|-----------|-------|
| C1 | cfa-receipt-apply | kafka-archive-overlay | shared schema | cfa-receipt-apply |
| C2 | cfa-receipt-apply | cheap-lookups | shared schema | cfa-receipt-apply |
| C3 | kafka-archive-overlay | cheap-lookups | shared schema | kafka-archive-overlay |

Deferred, not in this set: CfaIntake → PlanQuery and KafkaOverlay → PlanQuery (later intent). External CFA supply drop and Kafka broker are runtime boundaries of that later package, not this document.

## C1 — current-plan names for overlay bind

Provider `cfa-receipt-apply` names the file, `{env}`, and the grains overlay bind needs. Consumer `kafka-archive-overlay` must not invent a second identity or scan all journeys.

```yaml
shared-schema:
  id: C1
  file: docs/athena-timetable-lake.md
  provider: cfa-receipt-apply
  consumer: kafka-archive-overlay
  headings_owned_by_provider:
    - environments
    - heading map
    - CFA receipt → validate → apply
  names:
    env_pattern: "{env} in {dev, staging, prod}"
    service_guid: "SVC- plus stable function of (train_uid, run_date, stp_indicator)"
    grains:
      - name: journey
        identifier: serviceGuid
        attributes: [serviceGuid, trainUid, runDate, stpIndicator, toc, originCrs, destCrs, status]
      - name: service_day
        identifier: trainUidPlusCalendarDate
        attributes: [trainUid, calendarDate, serviceGuid, cancelled]
      - name: location_map
        identifier: tiploc
        attributes: [tiploc, crs]
  bind_keys_the_consumer_may_use:
    - rid via service_aliases (consumer writes aliases; join target is serviceGuid)
    - "(train_uid, scheduled start date) via service_day"
  forbidden:
    - UID-wide list_journeys scan
    - creating a journey on bind miss
    - rewriting booked times
  on_bind_miss:
    keep_archive_object: true
    create_journey: false
    booked_times: unchanged
  publish_set_the_consumer_relies_on:
    overlay_and_aliases: merge-preserve
    journey_status: merge-preserve
    calling_point_forecast_columns: merge-preserve
```

## C2 — current-plan names for cheap lookups

Provider names grain identifiers, STP winner, and the file shell. Consumer names Athena partition keys and projected columns for those grains (US3.1). Units review R-01 is accepted risk; this contract records that split so Construction does not guess.

```yaml
shared-schema:
  id: C2
  file: docs/athena-timetable-lake.md
  provider: cfa-receipt-apply
  consumer: cheap-lookups
  headings_owned_by_provider:
    - environments
    - heading map
    - CFA receipt → validate → apply
  headings_owned_by_consumer:
    - cheap lookups
  names:
    env_pattern: "{env} in {dev, staging, prod}"
    glue_tables_named_by_provider: [journey, calling_points, service_day, apply_history, location_map]
    stp_winner: [C, O, P, N]
    grains:
      - name: journey
        identifier: serviceGuid
        attributes: [serviceGuid, trainUid, runDate, stpIndicator, toc, originCrs, destCrs, status]
      - name: calling_points
        identifier: serviceGuidPlusSeq
        attributes: [serviceGuid, seq, crs, bookedArrival, bookedDeparture]
      - name: service_day
        identifier: trainUidPlusCalendarDate
        attributes: [trainUid, calendarDate, serviceGuid, cancelled]
  consumer_authors:
    - partition keys for dates and TOCs on the plan grains
    - projected columns for CIF identity, stop sequence, and service day lookups
    - fixture query for one train on one calendar date (no full journey scan)
  not_a_cheapest_target:
    - station plus time window
  fourth_lookup:
    listed_by_consumer: live overlay
    specified_by: C3
```

## C3 — overlay names for the fourth cheap lookup

Provider names the overlay row and alias table. Consumer lists live overlay as the fourth lookup and projects those columns. Overlay worked example stays on the provider chapter (US4.1 / AC4.1.5).

```yaml
shared-schema:
  id: C3
  file: docs/athena-timetable-lake.md
  provider: kafka-archive-overlay
  consumer: cheap-lookups
  headings_owned_by_provider:
    - Kafka archive/overlay
  headings_owned_by_consumer:
    - cheap lookups
  names:
    glue_tables_named_by_provider: [overlay, service_aliases]
    overlay_identifier: trainUidPlusCalendarDate
    overlay_columns: [trainUid, calendarDate, forecastArrival, forecastDeparture, deactivated, schedulePayload]
    type_skip: column projection on the single overlay row (not partition-by-overlayType)
  consumer_authors:
    - listing live overlay as the fourth cheapest lookup
    - Athena projection of overlay columns for one train on one calendar date
  provider_authors:
    - overlay worked example (AC4.1.5)
    - bind and archive rules (not consumed as a lookup contract)
  reserved_types_not_in_this_schema:
    - association
    - scheduleFormations
    - formationLoading
    - serviceLoading
    - trainOrder
    - OW
    - trainAlert
    - trackingID
    - alarm
    - stationInformation
```

## Ownership and change policy

- C1 and C2 specs are owned by `cfa-receipt-apply`. C3 is owned by `kafka-archive-overlay`. `cheap-lookups` owns no provider spec (Q3 A).
- Additive headings and new optional columns are safe. Consumers ignore names they do not read.
- Renaming or removing a heading, grain, identifier, or column needs an edit both consumer chapters accept before it lands (Q4 A).
- Exact bucket strings stay an author choice in the file. Sharing is already locked: receipt and archive are separate buckets.
- Cloud controls (KMS, bucket policies, workgroup encryption) stay Deferred.

## Open questions

| Contract | Question | Blocks |
|----------|----------|--------|
| C2 | Units review R-01 (accepted): partition-key author was ambiguous on the unit page. This summary assigns lookup keys to `cheap-lookups` and grain identifiers to `cfa-receipt-apply`. Confirm at Construction if the unit page should be edited in a later intent. | Functional Design of U3 |
| — | Later CfaIntake → PlanQuery and KafkaOverlay → PlanQuery sync shapes | Later service intent |

## Sources

- `unit-of-work.md`, `unit-of-work-dependency.md` — U1–U3 edges
- `components.md` — entity identifiers and attributes
- `requirements.md` — FR3, FR4, NFR1, NFR4
- `contract-design-questions.md` — Q1 A, Q2 A, Q3 A, Q4 A

## Assumptions & Open Questions

- The `SVC-` suffix encoding stays a Functional Design pick; C1 only locks the CIF triple inputs.
- None.
