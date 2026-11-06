# Rules — cheap-lookups

```yaml
rules:
  - id: BR3.1
    statement: Journey cheap lookup names partition keys toc and run_date, prefix curated/journey/toc=…/run_date=…/, and projected columns service_guid, train_uid, run_date, stp_indicator, toc, origin_crs, dest_crs, status; identity is CIF triple or service_guid
    category: constraint
    applies_to: JourneyLookup
    trigger: CIF identity lookup
    logic: IF this lookup is specified THEN those keys and columns are used AND columnar format is not the story done
    violation: missing keys or format treated as done
    source: FR3.1 NFR1
  - id: BR3.2
    statement: Calling-point cheap lookup is keyed (service_guid, seq) with partition keys toc and run_date and prefix curated/calling_points/toc=…/run_date=…/; toc and run_date are stored on the files so Athena can skip without joining every journey
    category: constraint
    applies_to: CallingPointLookup
    trigger: stop-sequence lookup
    logic: IF this lookup is specified THEN those keys and booked-time columns are used
    violation: missing seq key or full journey join required
    source: FR3.1 FR3.2
  - id: BR3.3
    statement: Service-day cheap lookup is one row per train per calendar date after STP, keyed (train_uid, calendar_date), winner C then O then P then N, partition keys toc and calendar_date
    category: constraint
    applies_to: ServiceDayLookup
    trigger: service-day lookup
    logic: IF this lookup is specified THEN it returns the STP winner AND uses those keys
    violation: wrong STP order or missing date key
    source: FR3.1 FR3.3
  - id: BR3.4
    statement: The cheap-lookups list is exactly CIF identity, stop sequence, service day, and live overlay; station-plus-time-window is not a cheapest-scan target; overlay columns are specified under Kafka archive/overlay
    category: policy
    applies_to: OverlayLookup
    trigger: read of the cheap-lookups list
    logic: IF the list is read THEN those four items exist AND station-plus-time-window is Deferred
    violation: fifth cheapest target or overlay keys pulled into this heading
    source: FR3.1 FR3.2
  - id: BR3.5
    statement: The fixture query for TOC LM, train_uid C12345, date 261020, stp_indicator P, service_guid SVC-C12345-261020-P must not scan every journey file group or every Kafka archive object
    category: constraint
    applies_to: FixtureQuery
    trigger: later query-contract test
    logic: IF the fixture runs THEN it reads only toc=LM plus those date partitions AND the overlay partition named in the Kafka chapter
    violation: full-table or full-archive scan
    source: NFR1
  - id: BR3.6
    statement: This heading shows one worked example of keys and projected columns for each plan lookup using that same locked train and date
    category: constraint
    applies_to: JourneyLookup CallingPointLookup ServiceDayLookup
    trigger: read of Worked example — plan lookups
    logic: IF the heading is locked THEN that example remains
    violation: example train or date changed
    source: FR3.1
  - id: BR3.7
    statement: This Bolt does not rename C2 grain identifiers, Glue names journey / calling_points / service_day, or STP order, and does not rename C3 overlay identity or overlay columns
    category: constraint
    applies_to: JourneyLookup CallingPointLookup ServiceDayLookup OverlayLookup
    trigger: this Bolt
    logic: IF this Bolt writes THEN those names stay
    violation: renamed grain or column
    source: FR3.1 FR3.3
  - id: BR3.8
    statement: This Bolt edits only the Cheap lookups heading
    category: constraint
    applies_to: document
    trigger: this Bolt
    logic: IF this Bolt writes THEN CFA, Environments, and Kafka archive/overlay headings are untouched
    violation: sibling heading rewrite
    source: FR1.2
```

## Summary

| ID | Category | One-line |
|----|----------|----------|
| BR3.1 | journey lookup | toc + run_date; CIF or service_guid |
| BR3.2 | stop sequence | (service_guid, seq); toc + run_date stored |
| BR3.3 | service day | (train_uid, calendar_date); STP C O P N |
| BR3.4 | list | exact four; overlay specified in Kafka |
| BR3.5 | fixture | no full journey or Kafka-object scan |
| BR3.6 | example | LM / C12345 / 261020 lock |
| BR3.7 | names | C2/C3 lock |
| BR3.8 | this Bolt | owned heading only |

## Sources

- `stories.md`, `unit-of-work-story-map.md` — US3.1 AC3.1.1–AC3.1.4
- `requirements.md` — FR3 NFR1
- `functional-design-questions.md` — Q1 A Q2 A Q3 A Q4 A Q5 A Q6 A
- `contract-summary.md` — C2 C3
