# Entities — kafka-archive-overlay

Provider names for C3. This Bolt consumes C1 bind keys and grains; it does not rename Journey, ServiceDay, or `service_guid`. Cheap-lookups may list live overlay as item 4 and point here for columns.

```yaml
entities:
  - name: ArchiveObject
    description: One Kafka message written to the env-qualified archive location before classify or apply
    identifier: objectKey
    attributes:
      - { name: objectKey, type: text, required: true, unique: true }
      - { name: env, type: enum, required: true, allowed: [dev, staging, prod] }
      - { name: payload, type: bytes, required: true, constraints: "raw bytes including undecodable payload" }
      - { name: classifiedType, type: text, required: false, constraints: "TS, schedule, deactivated, a reserved type, or unclassified" }
    constraints:
      - Written before classify or apply
      - A later classify or overlay persist failure does not erase this object
    relationships: []

  - name: OverlayProjection
    description: Single current overlay row for one train on one calendar date
    identifier: trainUidPlusCalendarDate
    attributes:
      - { name: trainUid, type: text, required: true }
      - { name: calendarDate, type: text, required: true, constraints: "YYMMDD" }
      - { name: forecastArrival, type: text, required: false, constraints: "from TS; last successful persist wins" }
      - { name: forecastDeparture, type: text, required: false, constraints: "from TS; last successful persist wins" }
      - { name: deactivated, type: boolean, required: false, default: false, constraints: "from deactivated; last successful persist wins" }
      - { name: schedulePayload, type: text, required: false, constraints: "retained overlay payload from schedule; last successful persist wins" }
    constraints:
      - overlayType is not an identity or partition key
      - Type skip is column projection on this one row
      - Booked public times are not attributes of this row
    relationships:
      - { to: ServiceDay, cardinality: "N:1", direction: OverlayProjection-to-ServiceDay, constraints: "bind via (trainUid, calendarDate)" }

  - name: ServiceAlias
    description: Darwin rid (or later alias) bound to a current-plan service_guid
    identifier: aliasTypePlusValue
    attributes:
      - { name: aliasType, type: text, required: true, allowed: [rid] }
      - { name: aliasValue, type: text, required: true, constraints: "Darwin rid when aliasType is rid" }
      - { name: serviceGuid, type: text, required: true, constraints: "C1 service_guid already written; this unit does not rename it" }
    constraints:
      - Written when a message carries rid and bind succeeds (rid hit or service_day fallback)
    relationships:
      - { to: Journey, cardinality: "N:1", direction: ServiceAlias-to-Journey }

  - name: JourneyStatus
    description: Overlay-owned Journey.status field; merge-preserved on CfaIntake publish
    identifier: serviceGuid
    attributes:
      - { name: serviceGuid, type: text, required: true }
      - { name: status, type: text, required: false, constraints: "deactivated sets this to deactivated" }
    relationships:
      - { to: OverlayProjection, cardinality: "1:1", direction: JourneyStatus-to-OverlayProjection }

  - name: CallingPointForecast
    description: Per-stop forecast columns on calling_points; overlay-owned and merge-preserved
    identifier: serviceGuidPlusSeq
    attributes:
      - { name: serviceGuid, type: text, required: true }
      - { name: seq, type: integer, required: true }
      - { name: forecastArrival, type: text, required: false }
      - { name: forecastDeparture, type: text, required: false }
    constraints:
      - Cheapest overlay lookup is OverlayProjection, not a rescan of these columns
    relationships:
      - { to: OverlayProjection, cardinality: "N:1", direction: CallingPointForecast-to-OverlayProjection }
```

## Summary

KafkaOverlay writes ArchiveObject, OverlayProjection, and ServiceAlias. Glue names this unit provides: `overlay`, `service_aliases`. Overlay identity is `(train_uid, calendar_date)`. Columns: `train_uid`, `calendar_date`, `forecast_arrival`, `forecast_departure`, `deactivated`, `schedule_payload`. Partition keys `toc` and `calendar_date` stay in this Kafka heading. `{env}` qualifies the archive location. Journey booked identity remains C1.

## Sources

- `docs/athena-timetable-lake.md` — Kafka archive/overlay chapter lock
- `components.md` — KafkaOverlay, OverlayProjection, ServiceAlias
- `contract-summary.md` — C1 C3
- `functional-design-questions.md` — Q3 A partition keys here; Q4 A last persist; Q5 A names
