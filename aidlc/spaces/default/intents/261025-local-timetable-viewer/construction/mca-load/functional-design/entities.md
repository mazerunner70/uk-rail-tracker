# Entities — mca-load

This unit replaces the SQLite file from that day's MCA. It owns the file shape. It does not own the two train lists.

```yaml
entities:
  - name: McaDirectory
    description: Host directory of MCA files visible to the load
    identifier: path
    attributes:
      - { name: path, type: text, required: true, unique: true, default: "apps/local-timetable/mca" }
    constraints:
      - The directory is on this machine under apps/local-timetable
      - The server process must be able to read files in this directory
    relationships:
      - { to: DayMca, cardinality: "1:N", direction: McaDirectory-to-DayMca }

  - name: DayMca
    description: One MCA file whose name contains today's calendar date
    identifier: filename
    attributes:
      - { name: filename, type: text, required: true, unique: true }
      - { name: calendarDate, type: text, required: true, constraints: "YYYYMMDD for today in Europe/London" }
    constraints:
      - Today's date as YYYYMMDD appears in the filename
      - Exactly one matching file is required for a successful start
    relationships:
      - { to: McaDirectory, cardinality: "N:1", direction: DayMca-to-McaDirectory }

  - name: LoadProgress
    description: What start and progress return to the query unit
    identifier: none
    attributes:
      - { name: state, type: enum, required: true, allowed: [idle, running, succeeded, failed] }
      - { name: howFar, type: text, required: false, constraints: "present while running; percent 0 to 100 as text" }
      - { name: sentence, type: text, required: true }
    constraints:
      - Start returns at once with this shape
      - howFar is present when state is running
    relationships: []

  - name: TimetableSqlite
    description: The SQLite file this unit replaces and the query later reads
    identifier: path
    attributes:
      - { name: path, type: text, required: true, unique: true, default: "apps/local-timetable/db/timetable.sqlite" }
    constraints:
      - The database container holds this file
      - A restart or rebuild of that container keeps the same file
      - Only this unit replaces it
    relationships:
      - { to: Journey, cardinality: "1:N", direction: TimetableSqlite-to-Journey }
      - { to: CallingPoint, cardinality: "1:N", direction: TimetableSqlite-to-CallingPoint }
      - { to: ServiceDay, cardinality: "1:N", direction: TimetableSqlite-to-ServiceDay }
      - { to: ServiceAlias, cardinality: "1:N", direction: TimetableSqlite-to-ServiceAlias }
      - { to: FeedMeta, cardinality: "1:N", direction: TimetableSqlite-to-FeedMeta }
      - { to: KafkaOffset, cardinality: "1:N", direction: TimetableSqlite-to-KafkaOffset }
      - { to: AppliedUpdate, cardinality: "1:N", direction: TimetableSqlite-to-AppliedUpdate }

  - name: Journey
    description: One London Midland journey after a successful load
    identifier: serviceGuid
    attributes:
      - { name: serviceGuid, type: text, required: true, unique: true }
      - { name: trainUid, type: text, required: true }
      - { name: runDate, type: text, required: true, constraints: "YYMMDD" }
      - { name: dateRunsTo, type: text, required: false, constraints: "YYMMDD" }
      - { name: daysRun, type: text, required: false }
      - { name: originCrs, type: text, required: false }
      - { name: destCrs, type: text, required: false }
      - { name: originStd, type: text, required: false, constraints: "HHMM" }
      - { name: toc, type: text, required: true, allowed: [LM] }
      - { name: stpIndicator, type: text, required: true }
      - { name: status, type: text, required: false }
    constraints:
      - Only TOC LM is stored
    relationships:
      - { to: CallingPoint, cardinality: "1:N", direction: Journey-to-CallingPoint }
      - { to: ServiceDay, cardinality: "1:N", direction: Journey-to-ServiceDay }

  - name: CallingPoint
    description: One stop on a stored journey
    identifier: serviceGuidPlusSeq
    attributes:
      - { name: serviceGuid, type: text, required: true }
      - { name: seq, type: text, required: true, constraints: "zero-padded three-digit string" }
      - { name: tiploc, type: text, required: false }
      - { name: crs, type: text, required: false }
      - { name: publicArr, type: text, required: false, constraints: "HHMM" }
      - { name: publicDep, type: text, required: false, constraints: "HHMM" }
      - { name: forecastArr, type: text, required: false, constraints: "HHMM; empty after a fresh MCA load" }
      - { name: forecastDep, type: text, required: false, constraints: "HHMM; empty after a fresh MCA load" }
      - { name: activity, type: text, required: false }
    relationships:
      - { to: Journey, cardinality: "N:1", direction: CallingPoint-to-Journey }

  - name: ServiceDay
    description: Which journey runs on one calendar date
    identifier: trainUidPlusCalendarDate
    attributes:
      - { name: trainUid, type: text, required: true }
      - { name: calendarDate, type: text, required: true, constraints: "YYMMDD" }
      - { name: serviceGuid, type: text, required: false }
      - { name: cancelled, type: boolean, required: true, default: false }
    relationships:
      - { to: Journey, cardinality: "N:1", direction: ServiceDay-to-Journey }

  - name: ServiceAlias
    description: Alternate identity pointing at a stored journey
    identifier: aliasTypePlusValue
    attributes:
      - { name: aliasType, type: text, required: true }
      - { name: aliasValue, type: text, required: true }
      - { name: serviceGuid, type: text, required: true }
    relationships:
      - { to: Journey, cardinality: "N:1", direction: ServiceAlias-to-Journey }

  - name: FeedMeta
    description: Key and value about the load that wrote the file
    identifier: key
    attributes:
      - { name: key, type: text, required: true, unique: true }
      - { name: value, type: text, required: true }
    relationships: []

  - name: KafkaOffset
    description: Overlay cursor table. An MCA load creates it empty. This unit does not consume a live feed.
    identifier: topicPlusPartition
    attributes:
      - { name: topic, type: text, required: true }
      - { name: partition, type: text, required: true }
      - { name: nextOffset, type: text, required: false }
      - { name: updatedAt, type: text, required: false }
    constraints:
      - After a successful MCA load this table has no rows
    relationships: []

  - name: AppliedUpdate
    description: Overlay history table. An MCA load creates it empty. This unit applies no CFA and no live overlay.
    identifier: id
    attributes:
      - { name: id, type: text, required: true, unique: true }
      - { name: serviceGuid, type: text, required: false }
      - { name: updateType, type: text, required: false }
      - { name: rid, type: text, required: false }
      - { name: payloadJson, type: text, required: false }
      - { name: appliedAt, type: text, required: false }
      - { name: sourceOffset, type: text, required: false }
    constraints:
      - After a successful MCA load this table has no rows
    relationships: []
```

## Summary

The load reads one MCA from `apps/local-timetable/mca` whose filename contains today's `YYYYMMDD`. It reports `LoadProgress` to the query. It replaces `TimetableSqlite` under `apps/local-timetable/db` with the seven tables. Journeys are London Midland only. Forecast columns and the two overlay tables are empty after a fresh load.

## Sources

- `aidlc/spaces/default/intents/261025-local-timetable-viewer/inception/units-generation/unit-of-work.md`
- `aidlc/spaces/default/intents/261025-local-timetable-viewer/inception/domain-design/components.md`
- `aidlc/spaces/default/intents/261025-local-timetable-viewer/inception/contract-design/contract-summary.md`
- `aidlc/spaces/default/intents/261025-local-timetable-viewer/construction/mca-load/functional-design/functional-design-questions.md`

## Assumptions & Open Questions

None.
