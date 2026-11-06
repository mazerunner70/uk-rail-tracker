# Entities — cfa-receipt-apply

Provider names for C1 and C2. This Bolt does not specify overlay or `service_aliases` grains (U2 / C3). Overlay, rid aliases, journey status, and calling-point forecast columns exist on PlanQuery and are merge-preserved on CfaIntake publish; their columns are not authored here.

```yaml
entities:
  - name: ReceiptZip
    description: One supplier zip object in the env-qualified CFA receipt location
    identifier: objectKey
    attributes:
      - { name: objectKey, type: text, required: true, unique: true }
      - { name: env, type: enum, required: true, allowed: [dev, staging, prod] }
      - { name: memberKind, type: enum, required: true, allowed: [MCA, CFA, none] }
      - { name: sequence, type: text, required: false, constraints: "three-digit HD columns 33–39 on CFA path; absent on MCA and refuse-before-parse" }
      - { name: status, type: enum, required: true, allowed: [accepted, refused, failed, nothing] }
      - { name: reason, type: text, required: false }
    constraints:
      - Zip basename is never the sequence
      - Member-name sequence fallback is forbidden
    relationships: []

  - name: FeedSequence
    description: Stored previous CFA sequence code for one environment
    identifier: env
    attributes:
      - { name: env, type: enum, required: true, unique: true, allowed: [dev, staging, prod] }
      - { name: currentCode, type: text, required: false, constraints: "zero-padded three digits; empty store is the 990 seed on the next CFA" }
    constraints:
      - CFA path advances currentCode on accept
      - MCA accept clears currentCode so the next CFA seeds 990
      - Refuse leaves currentCode unchanged
    relationships: []

  - name: Journey
    description: Current-plan journey grain; CIF identity plus derived service_guid
    identifier: serviceGuid
    attributes:
      - { name: serviceGuid, type: text, required: true, unique: true, constraints: "SVC- plus {train_uid}-{run_date}-{stp_indicator}" }
      - { name: trainUid, type: text, required: true }
      - { name: runDate, type: text, required: true, constraints: "YYMMDD" }
      - { name: stpIndicator, type: enum, required: true, allowed: [C, O, P, N] }
      - { name: toc, type: text, required: true }
      - { name: originCrs, type: text, required: false }
      - { name: destCrs, type: text, required: false }
      - { name: status, type: text, required: false, constraints: "merge-preserved on CfaIntake publish; overlay-owned" }
    constraints:
      - Origin, dest, and booked times are attributes, not part of service_guid
      - Same CIF triple always yields the same service_guid
    relationships:
      - { to: CallingPoint, cardinality: "1:N", direction: Journey-to-CallingPoint }

  - name: CallingPoint
    description: Booked stop on a journey; forecast columns are overlay-owned and merge-preserved
    identifier: serviceGuidPlusSeq
    attributes:
      - { name: serviceGuid, type: text, required: true }
      - { name: seq, type: integer, required: true }
      - { name: crs, type: text, required: true }
      - { name: bookedArrival, type: text, required: false, constraints: "HHMM" }
      - { name: bookedDeparture, type: text, required: false, constraints: "HHMM" }
      - { name: forecastArrival, type: text, required: false, constraints: "merge-preserved; overlay-owned" }
      - { name: forecastDeparture, type: text, required: false, constraints: "merge-preserved; overlay-owned" }
    relationships:
      - { to: Journey, cardinality: "N:1", direction: CallingPoint-to-Journey }

  - name: ServiceDay
    description: Winning journey for one train on one calendar date after STP
    identifier: trainUidPlusCalendarDate
    attributes:
      - { name: trainUid, type: text, required: true }
      - { name: calendarDate, type: text, required: true, constraints: "YYMMDD" }
      - { name: serviceGuid, type: text, required: true }
      - { name: cancelled, type: boolean, required: true, default: false }
    constraints:
      - STP winner on this grain is C then O then P then N
    relationships:
      - { to: Journey, cardinality: "N:1", direction: ServiceDay-to-Journey }

  - name: LocationMap
    description: TIPLOC to CRS map; written on MCA full update; intact on CFA accept
    identifier: tiploc
    attributes:
      - { name: tiploc, type: text, required: true, unique: true }
      - { name: crs, type: text, required: true }
    relationships: []

  - name: ApplyHistory
    description: Counts and CIF identities changed after an accepted apply
    identifier: sequence
    attributes:
      - { name: sequence, type: text, required: true, unique: true }
      - { name: total, type: integer, required: true }
      - { name: newCount, type: integer, required: true }
      - { name: overrideCount, type: integer, required: true }
      - { name: duplicateCount, type: integer, required: true }
      - { name: deleteCount, type: integer, required: true }
      - { name: identitiesChanged, type: list, required: true, constraints: "CIF triples (train_uid, run_date, stp_indicator) only; not service_guid" }
    constraints:
      - Not a versioned copy of every journey row
    relationships: []
```

## Summary

CfaIntake owns ReceiptZip and FeedSequence. PlanQuery stores the current-plan grains this unit names: Journey, CallingPoint, ServiceDay, LocationMap, ApplyHistory. Glue names this unit provides: `journey`, `calling_points`, `service_day`, `apply_history`, `location_map`. `{env}` qualifies every logical S3, Glue, and Athena object in `{dev, staging, prod}`. Overlay tables are named by U2.

## Sources

- `docs/athena-timetable-lake.md` — CFA chapter lock
- `components.md` — CfaIntake and PlanQuery entities
- `contract-summary.md` — C1 C2
- `functional-design-questions.md` — Q3 A status enum; Q4 C CIF triples; Q6 A names
