# Rules — kafka-archive-overlay

```yaml
rules:
  - id: BR4.1
    statement: Every Kafka message, including undecodable payload, writes an env-qualified archive object before classify or apply, and a later classify or apply failure does not erase that object
    category: policy
    applies_to: ArchiveObject
    trigger: Kafka message arrival
    logic: IF a message arrives THEN write ArchiveObject first AND keep it on later failure
    violation: dropped door or erased archive
    source: FR4.1 FR4.5
  - id: BR4.2
    statement: Queryable overlay projects only TS, schedule, and deactivated, and only when the message binds to a journey already in the current plan
    category: constraint
    applies_to: OverlayProjection
    trigger: overlay apply
    logic: IF type is TS THEN update forecast arrival and departure only. IF type is schedule THEN write schedule_payload and do not rewrite booked rows. IF type is deactivated THEN set overlay deactivated and Journey.status. IF the message does not bind THEN no overlay row
    violation: booked times rewritten or overlay without a journey
    source: FR4.2 FR4.3
  - id: BR4.3
    statement: association, scheduleFormations, formationLoading, serviceLoading, trainOrder, OW, trainAlert, trackingID, alarm, and stationInformation remain in the archive, are reserved for later uses, are not queryable overlay, and are not dropped at the door
    category: policy
    applies_to: ArchiveObject
    trigger: classify
    logic: IF type is reserved THEN keep ArchiveObject AND do not project overlay
    violation: reserved type dropped or treated as overlay
    source: FR4.4
  - id: BR4.4
    statement: Bind tries Darwin rid via service_aliases first; if rid is missing, bind via service_day (train_uid, scheduled start date); log fallback and miss; a miss keeps the archive, creates no journey, and leaves booked times unchanged; UID-wide list_journeys scan is forbidden
    category: constraint
    applies_to: ServiceAlias OverlayProjection
    trigger: overlay bind
    logic: IF rid present THEN try service_aliases. IF rid missing THEN try service_day. IF both miss THEN keep archive AND create no journey. IF bind succeeds AND message carries rid THEN write ServiceAlias
    violation: UID-wide scan or journey created on miss
    source: FR4.2
  - id: BR4.5
    statement: Live-overlay cheap lookup is column projection on the single overlay row keyed (train_uid, calendar_date), with partition keys toc and calendar_date, prefix curated/overlay/toc=…/calendar_date=…/, and one worked example in this Kafka heading
    category: constraint
    applies_to: OverlayProjection
    trigger: cheap overlay lookup
    logic: IF the fourth lookup is specified THEN keys, columns, and the worked example stay in this heading AND overlayType is not a partition key
    violation: type partition or keys moved out of this heading
    source: FR3.1 FR4.2 NFR1
  - id: BR4.6
    statement: Last successful overlay persist wins — a later bound TS, schedule, or deactivated replaces the matching columns on that one current row
    category: policy
    applies_to: OverlayProjection
    trigger: second bound overlay persist for the same train and calendar date
    logic: IF a later bound message persists THEN replace matching columns AND leave unmatched columns
    violation: first-write-wins or event-time reordering required
    source: FR4.2 FR4.3
  - id: BR4.7
    statement: Fail-open after the archive object exists — classify or overlay persist failure is logged, the archive stays, consuming continues, and no overlay row is required for that message
    category: policy
    applies_to: ArchiveObject OverlayProjection
    trigger: classify or overlay persist failure
    logic: IF archive exists AND later apply fails THEN log AND continue AND do not require an overlay row
    violation: retry-until-success or erased archive
    source: FR4.5 FR6.3 NFR4
  - id: BR4.8
    statement: Overlay mutations are not required to share the CfaIntake apply transaction
    category: constraint
    applies_to: OverlayProjection
    trigger: overlay persist
    logic: IF overlay is written THEN it is outside the CFA atomic publish
    violation: overlay rolled back with CFA refuse
    source: NFR4
  - id: BR4.9
    statement: This Bolt does not rename C1 bind keys or C3 overlay names — Glue overlay and service_aliases; identity (train_uid, calendar_date); columns train_uid, calendar_date, forecast_arrival, forecast_departure, deactivated, schedule_payload
    category: constraint
    applies_to: OverlayProjection ServiceAlias
    trigger: this Bolt
    logic: IF this Bolt writes THEN those names stay
    violation: renamed grain or column
    source: FR4.2
  - id: BR4.10
    statement: This Bolt edits only the Kafka archive/overlay heading, including Overlay cheap lookup and the overlay worked example
    category: constraint
    applies_to: document
    trigger: this Bolt
    logic: IF this Bolt writes THEN CFA, Environments, and cheap-lookups headings are untouched
    violation: sibling heading rewrite
    source: FR1.2
```

## Summary

| ID | Category | One-line |
|----|----------|----------|
| BR4.1 | archive | archive-before-classify; object kept |
| BR4.2 | overlay apply | TS / schedule / deactivated only, bound journey required |
| BR4.3 | reserved | named types archived, not overlay |
| BR4.4 | bind | rid then service_day; miss creates no journey |
| BR4.5 | lookup | keys and example stay in this heading |
| BR4.6 | current row | last persist wins |
| BR4.7 | fail-open | archive is the success line |
| BR4.8 | transaction | overlay outside CFA apply |
| BR4.9 | names | C1/C3 lock |
| BR4.10 | this Bolt | owned heading only |

## Sources

- `stories.md` — US4.1 AC4.1.1–AC4.1.5
- `requirements.md` — FR4 FR6.3 NFR1 NFR4
- `functional-design-questions.md` — Q1 A Q2 A Q3 A Q4 A Q5 A Q6 A
- `contract-summary.md` — C1 C3
