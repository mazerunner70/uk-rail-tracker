# Entities — cheap-lookups

Lookup contracts this unit authors over C2 plan grains and the C3 overlay row. Grain identifiers and Glue names stay as C2/C3 already wrote them. Overlay keys, columns, and the overlay worked example stay in Kafka archive/overlay (overlay Q3 A).

```yaml
entities:
  - name: JourneyLookup
    description: Cheap Athena lookup of one current-plan journey by CIF identity or service_guid
    identifier: serviceGuid
    attributes:
      - { name: serviceGuid, type: text, required: true, constraints: "SVC- plus {train_uid}-{run_date}-{stp_indicator}; C2 name, not renamed here" }
      - { name: trainUid, type: text, required: true }
      - { name: runDate, type: text, required: true, constraints: "YYMMDD; also a partition key" }
      - { name: stpIndicator, type: enum, required: true, allowed: [C, O, P, N] }
      - { name: toc, type: text, required: true, constraints: "also a partition key" }
      - { name: originCrs, type: text, required: false }
      - { name: destCrs, type: text, required: false }
      - { name: status, type: text, required: false }
    constraints:
      - Partition keys toc and run_date
      - Prefix curated/journey/toc=…/run_date=…/
      - Glue table journey
    relationships: []

  - name: CallingPointLookup
    description: Cheap Athena lookup of booked stops on one journey
    identifier: serviceGuidPlusSeq
    attributes:
      - { name: serviceGuid, type: text, required: true }
      - { name: seq, type: integer, required: true }
      - { name: crs, type: text, required: true }
      - { name: bookedArrival, type: text, required: false }
      - { name: bookedDeparture, type: text, required: false }
      - { name: toc, type: text, required: true, constraints: "stored so Athena can skip without joining every journey" }
      - { name: runDate, type: text, required: true, constraints: "stored so Athena can skip without joining every journey" }
    constraints:
      - Partition keys toc and run_date
      - Prefix curated/calling_points/toc=…/run_date=…/
      - Glue table calling_points
      - Forecast columns are overlay-owned and are not this lookup
    relationships:
      - { to: JourneyLookup, cardinality: "N:1", direction: CallingPointLookup-to-JourneyLookup }

  - name: ServiceDayLookup
    description: Cheap Athena lookup of the STP-winning journey for one train on one calendar date
    identifier: trainUidPlusCalendarDate
    attributes:
      - { name: trainUid, type: text, required: true }
      - { name: calendarDate, type: text, required: true, constraints: "YYMMDD; also a partition key" }
      - { name: serviceGuid, type: text, required: true, constraints: "STP winner C then O then P then N" }
      - { name: cancelled, type: boolean, required: true, default: false }
      - { name: toc, type: text, required: true, constraints: "stored so Athena can skip without joining every journey" }
    constraints:
      - Partition keys toc and calendar_date
      - Prefix curated/service_day/toc=…/calendar_date=…/
      - Glue table service_day
    relationships:
      - { to: JourneyLookup, cardinality: "N:1", direction: ServiceDayLookup-to-JourneyLookup }

  - name: OverlayLookup
    description: Fourth cheap lookup listed here; keys, columns, and worked example specified in Kafka archive/overlay
    identifier: trainUidPlusCalendarDate
    attributes:
      - { name: trainUid, type: text, required: true }
      - { name: calendarDate, type: text, required: true }
    constraints:
      - This heading lists the lookup and may point at Kafka archive/overlay
      - Partition keys, projected columns, and the overlay worked example are not authored here
      - Glue table overlay is named by C3
    relationships: []

  - name: FixtureQuery
    description: Later query-contract check for one locked train on one locked calendar date
    identifier: exampleTrainDate
    attributes:
      - { name: toc, type: text, required: true, default: LM }
      - { name: trainUid, type: text, required: true, default: C12345 }
      - { name: calendarDate, type: text, required: true, default: "261020" }
      - { name: stpIndicator, type: enum, required: true, default: P, allowed: [C, O, P, N] }
      - { name: serviceGuid, type: text, required: true, default: "SVC-C12345-261020-P" }
    constraints:
      - Must not scan every journey file group or every Kafka archive object
      - Reads only toc=LM plus date partitions here, plus the overlay partition in the Kafka chapter
    relationships: []
```

## Summary

This unit names how Athena reads plan grains. Partition keys for items 1–3 are `toc` plus `run_date` or `calendar_date`. Item 4 is listed here and specified under Kafka. Station-plus-time-window is not a cheapest-scan target. Glue names this unit consumes from C2: `journey`, `calling_points`, `service_day`. Overlay Glue name `overlay` is C3.

## Sources

- `docs/athena-timetable-lake.md` — Cheap lookups chapter lock
- `unit-of-work.md`, `unit-of-work-story-map.md` — U3 / US3.1
- `contract-summary.md` — C2 C3
- `components.md` — PlanQuery
- `functional-design-questions.md` — Q3 A keys; Q4 A four-item list; Q5 A names; Q6 A fixture
