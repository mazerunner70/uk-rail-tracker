# User Stories

Document-delivery stories only (Q2 A). Breakdown is by workflow (Q3 A). Persona is Later Implementer (Q1 A). Priority follows Q4 A. Acceptance for this intent is that `docs/athena-timetable-lake.md` specifies the slice so a later build can pass.

Product label in every story is `docs/athena-timetable-lake.md`. Given/When/Then is a document check, not a running lake.

Upstream: `requirements.md`, CodeKB `business-overview.md`, `component-inventory.md`, `team-practices.md`.

## Must Have

### US1.1 — Name the document and the three environments

As a Later Implementer, I want `docs/athena-timetable-lake.md` to exist, to use a stable heading map I can re-enter by task, and to name `dev`, `staging`, and `prod` as an `{env}` pattern for every logical S3, Glue, and Athena object, so that I can build the first slice against named tiers without inventing secrets or locking exact bucket strings.

- **Priority:** Must Have
- **Traces:** FR1.1, FR1.2, FR1.3, FR1.4, NFR2, NFR3
- **Depends on:** none
- **INVEST:** Independent document-hygiene slice; testable by reading the file

**AC1.1.1**
Given this piece of work is complete
When I open the repo
Then a new file exists at `docs/athena-timetable-lake.md`

**AC1.1.2**
Given `docs/athena-timetable-lake.md`
When I read every logical S3 prefix, Glue catalog or database, and Athena workgroup it introduces
Then each is qualified with an `{env}` pattern covering `dev`, `staging`, and `prod`
And the file does not claim those resources already exist
And exact bucket or prefix strings, and shared-versus-separate buckets, are left as architecture choices

**AC1.1.3**
Given `docs/athena-timetable-lake.md`
When I search it for credentials
Then it contains no Kafka keys, Darwin keys, or long-lived AWS access keys

**AC1.1.4**
Given `docs/athena-timetable-lake.md`
When I read the environments heading
Then merge to `main` may roll staging
And production needs a separate human approval
And production names a rollback path that must be tested before a prod cut (the file names the obligation; it does not prove a test already ran)

**AC1.1.5**
Given `docs/athena-timetable-lake.md`
When I scan top-level headings
Then they include: environments, CFA receipt → validate → apply, cheap lookups, Kafka archive/overlay, TOC extension, later package layering, and Deferred / not in this document
And any diagram has a text fallback
And refuse versus accept is not shown by colour alone

**AC1.1.6**
Given `docs/athena-timetable-lake.md`
When I read the Deferred / not in this document section
Then KMS, bucket policies, and workgroup encryption are named as later NFR / infrastructure work, not as resources this file creates
And the section lists reserved Kafka types, station + time-window cheapest scan, versioned journey history, runtime/CI, and Android / `docs/architecture.md` pointer as not in this document

### US2.1 — Specify CFA receipt, validate, and apply

As a Later Implementer, I want `docs/athena-timetable-lake.md` to specify CFA zip receipt, sequence validation, refuse, empty-lake identity bootstrap, incremental LM apply, and apply history, so that I can build the first ingest slice without reopening FR2.

- **Priority:** Must Have
- **Traces:** FR2.1, FR2.2, FR2.3, FR2.4, FR2.5, NFR4
- **Depends on:** US1.1 (named receipt location)
- **INVEST:** One workflow chapter for this document; later construction splits ingest / validate / apply

**AC2.1.1**
Given `docs/athena-timetable-lake.md`
When I read the CFA receipt heading
Then it names an env-qualified logical S3 receipt location that accepts timetable CFA zip objects
And it does not lock an exact bucket string

**AC2.1.2**
Given `docs/athena-timetable-lake.md`
When I read the CFA validate section
Then it requires exactly one CFA member
And the sequence is the three-digit run in HD Current File Reference columns 33–39
And the zip basename is never the sequence
And expected code is stored previous plus one, modulo 1000, zero-padded
And an empty store seeds `990`
And it states how first journey identities and the CRS/TIPLOC map enter the current plan (a supporting MCA baseline into the same grains, or another named path — not CFA-only silence)

**AC2.1.3**
Given `docs/athena-timetable-lake.md`
When I read the sequence-mismatch refuse path
Then a wrong sequence leaves the queryable current plan unchanged
And the refuse records expected and actual codes
And the raw zip stays in the receipt location
And receipt zips are named as the replay source

**AC2.1.4**
Given `docs/athena-timetable-lake.md`
When I read other CFA refuse paths
Then zero CFA members, more than one CFA member, and an HD field with no three-digit run each leave the current plan unchanged
And each records the validation reason
And the raw zip stays in the receipt location
And member-name and zip-basename sequence fallbacks are forbidden

**AC2.1.5**
Given `docs/athena-timetable-lake.md`
When I read the accepted-apply section
Then it applies incremental `N` / `R` / `D` for TOC `LM` only, on CIF identity `(train_uid, run_date, stp_indicator)`

**AC2.1.6**
Given `docs/athena-timetable-lake.md`
When I read apply history
Then Athena holds the current plan plus history of sequence, total / new / override / duplicate / delete counts, and identities changed
And it does not keep a full versioned copy of every journey row after every file

**AC2.1.7**
Given `docs/athena-timetable-lake.md`
When I read publish and failure
Then an accepted apply is one atomic published current-plan plus history update
And a mid-apply failure leaves the previous plan queryable

### US3.1 — Specify cheap Athena lookups for the current plan

As a Later Implementer, I want `docs/athena-timetable-lake.md` to specify partition keys, prefix/grain, and projected columns for the current-plan lookups, so that I can build the first query slice without reopening FR3.

- **Priority:** Must Have
- **Traces:** FR3.1, FR3.2, FR3.3, NFR1
- **Depends on:** US2.1 (current-plan grains)
- **INVEST:** Plan-query chapter; overlay lookup lives on US4.1; columnar format stays an architecture choice

**AC3.1.1**
Given `docs/athena-timetable-lake.md`
When I read the cheap-lookups heading
Then it names partition keys, prefix/grain, and projected columns for journey by CIF identity `(train_uid, run_date, stp_indicator)` or `service_guid`
And it does not treat a specific columnar file format as story done

**AC3.1.2**
Given `docs/athena-timetable-lake.md`
When I read the same heading
Then calling points are keyed `(service_guid, seq)`
And one row per train per calendar day exists after STP resolution `(train_uid, calendar_date)`
And STP winner order is `C` then `O` then `P` then `N`
And the file shows one worked example (one train, one calendar date) of keys and projected columns for each of those plan lookups

**AC3.1.3**
Given `docs/athena-timetable-lake.md`
When I read the cheap-lookups list
Then station-plus-time-window is not a cheapest-scan target
And the list is an exact four-item set: CIF identity, stop sequence, service day, live overlay (overlay columns are specified under US4.1)

**AC3.1.4**
Given `docs/athena-timetable-lake.md`
When I read the later query-contract
Then it names a fixture query for one train on one calendar date that must not require a full-table scan of every journey or every Kafka object

### US4.1 — Specify Kafka archive and the three overlay types

As a Later Implementer, I want `docs/athena-timetable-lake.md` to archive every Kafka message, name overlay bind keys, and project only `TS`, `schedule`, and `deactivated`, so that I can build the live overlay without dropping later types or rewriting booked times.

- **Priority:** Must Have
- **Traces:** FR4.1, FR4.2, FR4.3, FR4.4, FR4.5
- **Depends on:** US2.1 (journey must already exist)
- **INVEST:** Overlay workflow; reserved types listed in AC

**AC4.1.1**
Given `docs/athena-timetable-lake.md`
When I read archive-before-classify
Then every Kafka message, including undecodable payload, writes an env-qualified logical S3 archive object before classify or apply
And a later classify or apply failure does not erase that object
And exact bucket strings stay an architecture choice

**AC4.1.2**
Given `docs/athena-timetable-lake.md`
When I read overlay apply for `TS` / `schedule` / `deactivated`
Then those types project only when they resolve to a journey already in the current plan
And `TS` updates forecast arrival and departure only
And booked public times stay as loaded
And `schedule` is retained as overlay payload and does not rewrite booked rows
And `deactivated` sets journey status to deactivated
And Kafka overlay mutations are not required to share the CFA apply transaction

**AC4.1.3**
Given `docs/athena-timetable-lake.md`
When I read reserved types
Then `association`, `scheduleFormations`, `formationLoading`, `serviceLoading`, `trainOrder`, `OW`, `trainAlert`, `trackingID`, `alarm`, and `stationInformation` remain in the S3 archive
And they are named as reserved for later uses
And they are not queryable overlay in this document
And they are not dropped at the door

**AC4.1.4**
Given `docs/athena-timetable-lake.md`
When I read overlay bind
Then it names the join keys a message uses to attach to a current-plan journey (at least one of: Darwin `rid` alias; `(train_uid, scheduled start date)` via `service_day`)
And it forbids a UID-wide `list_journeys()` scan
And a miss keeps the archive object, creates no new journey, and leaves booked times unchanged

**AC4.1.5**
Given `docs/athena-timetable-lake.md`
When I read the live-overlay cheap lookup
Then it names partition keys and projected columns for forecast versus booked times and deactivated status on `(train_uid, calendar_date)`
And it shows one worked example for one train on one calendar date

## Should Have

### US5.1 — Specify LM first and a TOC extension path

As a Later Implementer, I want `docs/athena-timetable-lake.md` to lock LM as the first queryable population and to describe how other TOCs extend without a new receipt or sequence mechanism, so that I do not rebuild intake when we add a TOC.

- **Priority:** Should Have
- **Traces:** FR5.1, FR5.2
- **Depends on:** US2.1
- **INVEST:** Extension path; not required for the first ingest demo

**AC5.1.1**
Given `docs/athena-timetable-lake.md`
When I read persist rules
Then non-delete drafts whose TOC is not `LM` are skipped
And deletes follow identity, not TOC

**AC5.1.2**
Given `docs/athena-timetable-lake.md`
When I read the named TOC-extension heading
Then identity and calendar rules stay FR2.4 / FR3.1 / FR3.3
And no new receipt or sequence mechanism is required

### US6.1 — Record later package layering

As a Later Implementer, I want `docs/athena-timetable-lake.md` to record sibling-package layering and fail-closed / fail-open policy, so that later code does not land inside `live_sched` or import `schedule_poc`.

- **Priority:** Should Have
- **Traces:** FR6.1, FR6.2, FR6.3
- **Depends on:** US1.1
- **INVEST:** Layering heading stays on the page map even if cut from the first demo

**AC6.1.1**
Given `docs/athena-timetable-lake.md`
When I read later package layering
Then lake / transform / ingest code is a sibling package under `server/` with its own `pyproject.toml` and tests
And Athena or S3 clients are not added inside `live_sched`
And `schedule_poc` is not imported

**AC6.1.2**
Given `docs/athena-timetable-lake.md`
When I read client placement
Then object-store and query-engine clients do not live inside CIF parsers or the repository layer

**AC6.1.3**
Given `docs/athena-timetable-lake.md`
When I read error policy
Then CFA / sequence intake is fail-closed
And Kafka overlay is fail-open after the raw archive object exists

## Could Have / Won't Have (this time)

These follow requirements out of scope. They are not stories to implement in this document. They appear under Deferred / not in this document (AC1.1.6).

| ID | Priority | Statement |
|----|----------|-----------|
| US7.1 | Won't Have | Queryable overlay for reserved Kafka types |
| US7.2 | Won't Have | Station + time-window as a cheapest Athena target |
| US7.3 | Won't Have | Full versioned journey history after every CFA |
| US7.4 | Won't Have | Runtime ingest, Glue jobs, buckets, or CI in this piece of work |
| US7.5 | Won't Have | Android / OpenLDBWS changes or a pointer from `docs/architecture.md` |

## Story map

| Workflow | Must Have | Should Have | Won't Have |
|----------|-----------|-------------|------------|
| Document / env hygiene | US1.1 | US6.1 | US7.4, US7.5 |
| CFA intake | US2.1 | US5.1 | US7.3 |
| Cheap query | US3.1 | | US7.2 |
| Kafka overlay | US4.1 | | US7.1 |
