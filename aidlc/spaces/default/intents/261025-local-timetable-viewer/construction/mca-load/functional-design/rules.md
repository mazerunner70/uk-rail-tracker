# Rules — mca-load

```yaml
rules:
  - id: BR1.1
    statement: The MCA directory is the fixed host path apps/local-timetable/mca
    category: constraint
    applies_to: McaDirectory
    trigger: start
    logic: IF start is asked THEN look only in that directory
    violation: start fails and the SQLite file is left as it was
    source: Q7
  - id: BR1.2
    statement: Today's MCA is the unique file in that directory whose filename contains today's date as YYYYMMDD in Europe/London
    category: validation
    applies_to: DayMca
    trigger: start
    logic: IF zero files match OR more than one file matches THEN there is no day's MCA
    violation: progress failed; sentence says the MCA cannot be read; SQLite unchanged
    source: Q1 Q6 Q5
  - id: BR1.3
    statement: The SQLite file this unit replaces is apps/local-timetable/db/timetable.sqlite in the database container; the query reads that same file
    category: constraint
    applies_to: TimetableSqlite
    trigger: successful replace
    logic: IF a replace succeeds THEN the new file is at that path
    violation: the query cannot read today's timetable
    source: NFR2
  - id: BR1.4
    statement: A restart or a rebuild of the database container keeps the same SQLite file
    category: constraint
    applies_to: TimetableSqlite
    trigger: database container restart or image rebuild
    logic: IF the container comes back THEN the file and its journey rows are still there
    violation: the file did not survive
    source: AC3.1.2 NFR2
  - id: BR2.1
    statement: Start returns at once with LoadProgress
    category: policy
    applies_to: LoadProgress
    trigger: start
    logic: IF start is asked THEN the caller receives progress immediately and the load continues without waiting for the caller
    violation: the caller is blocked on the replace
    source: contract-summary
  - id: BR2.2
    statement: If a load is already running, start returns that running progress and does not start a second load
    category: policy
    applies_to: LoadProgress
    trigger: start while running
    logic: IF state is running THEN return the current progress AND do not start another replace
    violation: two replaces at once
    source: Q2
  - id: BR2.3
    statement: If a load has already succeeded, a later start begins a new replace from that day's MCA
    category: policy
    applies_to: LoadProgress TimetableSqlite
    trigger: start after succeeded
    logic: IF state is succeeded THEN start a new replace
    violation: a later start is ignored
    source: Q3
  - id: BR2.4
    statement: While running, howFar is a percent from 0 to 100 as text, and sentence is always present
    category: constraint
    applies_to: LoadProgress
    trigger: running progress
    logic: IF state is running THEN howFar is present as 0 to 100 text AND sentence is present
    violation: running progress without howFar
    source: Q4
  - id: BR3.1
    statement: This unit is the only writer of the SQLite file
    category: policy
    applies_to: TimetableSqlite
    trigger: any replace
    logic: IF the file is created or rewritten THEN this unit did it
    violation: another unit wrote the file
    source: AC3.1.3
  - id: BR3.2
    statement: A header whose CIF columns 33 to 39 are not exactly seven decimal digits is refused before the SQLite file is touched
    category: validation
    applies_to: DayMca TimetableSqlite LoadProgress
    trigger: after the day's MCA is opened
    logic: IF columns 33 to 39 are not exactly seven decimal digits THEN progress failed AND sentence says the header sequence was refused AND SQLite unchanged
    violation: the file was replaced from a refused header
    source: unit-of-work FR8.3
  - id: BR3.3
    statement: Only London Midland journeys are stored
    category: policy
    applies_to: Journey
    trigger: write of journeys
    logic: IF a journey's TOC is not LM THEN it is not stored
    violation: a non-LM journey is in the file
    source: FR
  - id: BR3.4
    statement: No CFA is applied
    category: policy
    applies_to: AppliedUpdate
    trigger: load
    logic: IF the load runs THEN no CFA is read or applied
    violation: CFA rows in applied_updates
    source: domain-design
  - id: BR3.5
    statement: After the day's MCA is readable and the header is accepted, the load deletes the current SQLite file and writes a new one with the seven tables
    category: policy
    applies_to: TimetableSqlite
    trigger: replace
    logic: IF header accepted THEN delete the current file THEN write the seven tables
    violation: old and new rows mixed, or a table missing
    source: unit-of-work contract-summary
  - id: BR3.6
    statement: kafka_offsets and applied_updates exist after a successful load and have no rows
    category: constraint
    applies_to: KafkaOffset AppliedUpdate
    trigger: successful replace
    logic: IF replace succeeds THEN both tables exist AND both are empty
    violation: missing table (older schema) or overlay rows from this load
    source: contract-summary
  - id: BR4.1
    statement: If the day's MCA is missing or unreadable at start, progress becomes failed, the sentence says the MCA cannot be read, and the SQLite file is left as it was
    category: validation
    applies_to: DayMca TimetableSqlite LoadProgress
    trigger: start with no readable day's MCA
    logic: IF the directory is missing OR unreadable OR zero matching files OR more than one matching file OR the chosen file cannot be read THEN failed AND SQLite unchanged
    violation: SQLite replaced or removed before a readable MCA existed
    source: Q5
  - id: BR2.5
    statement: When the server process starts, LoadProgress state is idle even if timetable.sqlite already exists from an earlier load
    category: policy
    applies_to: LoadProgress TimetableSqlite
    trigger: process start
    logic: IF the process has just started THEN state is idle AND the query must call start before a load runs again
    violation: the process invents running or succeeded without a load in this process
    source: contract-summary
  - id: BR3.7
    statement: From the accepted MCA, only London Midland CIF schedules are persisted into Journey and CallingPoint rows using the seven-table shape in contract-summary
    category: calculation
    applies_to: Journey CallingPoint
    trigger: replace after header accepted
    logic: IF a BS schedule row is LM THEN map train_uid, run_date, days_run, origin and destination CRS, origin_std, stp_indicator, and status into Journey AND map each LI stop into CallingPoint with seq, tiploc, crs, public times, and empty forecast times
    violation: a non-LM journey is stored or a required column is missing
    source: FR7 contract-summary
  - id: BR3.8
    statement: serviceGuid is assigned deterministically for each stored journey so the same CIF identity always maps to the same serviceGuid within one successful load
    category: calculation
    applies_to: Journey ServiceDay ServiceAlias
    trigger: persist
    logic: IF a journey is stored THEN serviceGuid is unique per journey row AND ServiceDay rows for today's calendar date YYMMDD point at that serviceGuid or record cancellation without a running journey
    violation: duplicate serviceGuid or a service_day row with no matching journey when not cancelled
    source: FR7 DATA-MODEL STP resolution
  - id: BR3.9
    statement: feed_meta holds non-secret load metadata keys only; service_aliases and overlay tables stay empty on this load
    category: constraint
    applies_to: FeedMeta ServiceAlias KafkaOffset AppliedUpdate
    trigger: successful replace
    logic: IF replace succeeds THEN feed_meta may record load timestamps and source file name AND service_aliases has no rows AND kafka_offsets and applied_updates have no rows
    violation: a secret value or overlay row appears from this MCA load
    source: BR3.4 BR3.6 AC3.1.6
    statement: A test of this unit uses a synthetic MCA and does not open a production timetable file
    category: policy
    applies_to: DayMca
    trigger: unit test
    logic: IF a test runs THEN its MCA is synthetic
    violation: a production MCA was opened
    source: AC3.1.4 NFR4
  - id: BR5.2
    statement: This unit holds no Kafka or Darwin secret values
    category: policy
    applies_to: FeedMeta
    trigger: any stored value
    logic: IF a value is stored THEN it is not a Kafka or Darwin secret
    violation: a secret in the file or the design
    source: AC3.1.6
```

## Summary

| Id | Statement |
| --- | --- |
| BR1.1 | MCA directory is `apps/local-timetable/mca` |
| BR1.2 | Today's file is the unique filename containing `YYYYMMDD` |
| BR1.3 | SQLite path is `apps/local-timetable/db/timetable.sqlite` |
| BR1.4 | That file survives a database container restart and rebuild |
| BR2.1 | Start returns at once |
| BR2.2 | Start while running returns current progress |
| BR2.3 | Start after success begins a new replace |
| BR2.4 | Running howFar is a 0–100 percent as text |
| BR3.1 | This unit is the only writer |
| BR3.2 | Invalid HD sequence refuses before any replace; sentence names header refusal |
| BR3.3 | London Midland only |
| BR3.4 | No CFA |
| BR3.5 | After a good header, delete then write the seven tables |
| BR3.6 | Overlay tables exist and are empty |
| BR3.7 | LM CIF maps to Journey and CallingPoint |
| BR3.8 | serviceGuid and service_day for today |
| BR3.9 | feed_meta only; aliases empty |
| BR2.5 | Process start leaves state idle |
| BR4.1 | Missing or unreadable MCA fails and leaves SQLite |
| BR5.1 | Tests use a synthetic MCA |
| BR5.2 | No Kafka or Darwin secrets |

## Sources

- `aidlc/spaces/default/intents/261025-local-timetable-viewer/construction/mca-load/functional-design/functional-design-questions.md`
- `aidlc/spaces/default/intents/261025-local-timetable-viewer/inception/units-generation/unit-of-work.md`
- `aidlc/spaces/default/intents/261025-local-timetable-viewer/inception/user-stories/stories.md`
- `aidlc/spaces/default/intents/261025-local-timetable-viewer/inception/contract-design/contract-summary.md`

## Assumptions & Open Questions

None.
