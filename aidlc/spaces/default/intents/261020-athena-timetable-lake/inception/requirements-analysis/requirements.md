# Requirements

This piece of work produces an architecture markdown for a later S3 / Glue / Athena timetable lake. It does not stand up buckets or write runtime code. The lake it describes follows the live-sched ideas in CodeKB `business-overview.md`, `architecture.md`, and `code-structure.md`, and the affirmed `team-practices.md`. Classic skipped ideation, so there is no `intent-statement.md` or `scope-document.md`; the initial request is the `project-description.json` line in `aidlc-state.md`.

## Intent analysis

Give a later implementer one architecture document that they can use to build the first ingest and query slice without reopening these decisions. The lake receives timetable CFA zips, validates them, and applies an accepted file into the S3 objects that Athena reads. Every Kafka message also lands in S3. Queryable tables keep the current LM plan plus a current live overlay for this train today, and they stay cheap for the lookups live-sched already runs plus that overlay. Remaining Kafka types stay in the archive for later uses. Implementation, Glue jobs, and AWS resources come in later work.

## Functional requirements

### FR1 — Architecture document as the deliverable

- **FR1.1** This piece of work shall produce a new markdown file under `docs/` (canonical path `docs/athena-timetable-lake.md`).
- **FR1.2** The document shall be specific enough that a later implementer can build the first CFA receipt → validate → apply slice and the first Athena lookup slice without reopening FR2–FR6 or NFR1–NFR4.
- **FR1.3** The document shall name `dev`, `staging`, and `prod` for every S3 prefix, Glue catalog or database, and Athena workgroup it introduces. It shall not create those resources.
- **FR1.4** The document shall not list credentials or long-lived AWS access keys.

### FR2 — CFA receipt, validate, and apply

- **FR2.1** The document shall specify an S3 receipt location that accepts timetable CFA zip objects.
- **FR2.2** On object arrival the described flow shall validate the zip before any curated-table write: exactly one CFA member; HD Current File Reference columns 33–39 supply the three-digit sequence; the zip basename is never the sequence; expected code is the stored previous code plus one, modulo 1000, zero-padded; an empty store seeds `990` unless a later design records a different seed.
- **FR2.3** A failed validation shall leave the queryable current plan unchanged and shall record a refuse (expected versus actual sequence, or the validation reason). The raw zip shall remain in the receipt location for replay.
- **FR2.4** An accepted CFA shall apply incremental `N` / `R` / `D` into the Athena-backing current-plan objects for TOC `LM` only in this first population, using the same CIF identity `(train_uid, run_date, stp_indicator)` live-sched uses.
- **FR2.5** After an accepted apply, Athena shall hold the current plan plus an apply-history table (or equivalent objects) that records at least: accepted sequence, total / new / override / duplicate / delete counts, and which CIF identities changed. The document shall not require a full versioned copy of every journey row after every accepted file. Raw zips in the receipt location remain the replay source.

### FR3 — Cheap Athena lookups

- **FR3.1** The document shall specify partition keys, file layout, and table grain so these four lookups stay the cheapest Athena scans:
  1. Journey by CIF identity `(train_uid, run_date, stp_indicator)` or `service_guid`
  2. Calling points by stop sequence `(service_guid, seq)`
  3. One row per train per calendar day after STP resolution (`service_day` grain: `(train_uid, calendar_date)`)
  4. Current live overlay for this train today (forecast times versus booked public times; deactivated status)
- **FR3.2** Station-plus-time-window lookups (what calls at this CRS today or this hour) are not a cheapest-scan target in this document.
- **FR3.3** The document shall state how timetable rows are transformed from CIF/CFA records into those grains (including STP winner rules `C` then `O` then `P` then `N`) so Athena does not rescan every schedule window to answer FR3.1 item 3.

### FR4 — Kafka archive and queryable overlay

- **FR4.1** Every Kafka message shall be written to S3, including types that are not queryable in this document and including payloads that fail later decode.
- **FR4.2** Athena queryable overlay columns in this document shall project only the three live-sched apply types: `TS`, `schedule`, and `deactivated`, and only when they resolve to a journey that already exists in the current plan.
- **FR4.3** `TS` shall update forecast arrival and departure only; booked public times stay as loaded. `schedule` shall be retained as overlay payload and shall not rewrite booked timetable rows. `deactivated` shall set journey status to deactivated.
- **FR4.4** The remaining classified types shall remain in the S3 archive and shall not be dropped at the door. The document shall name them as reserved for later uses, not as queryable overlay in this piece of work: `association`, `scheduleFormations`, `formationLoading`, `serviceLoading`, `trainOrder`, `OW`, `trainAlert`, `trackingID`, `alarm`, `stationInformation`.
- **FR4.5** Archive write happens before classify and apply. A classify or apply failure shall not erase the archived object.

### FR5 — Timetable population and extension

- **FR5.1** The first queryable population in the document is London Midland (`LM`) only, matching live-sched persist rules (non-delete drafts whose TOC is not `LM` are skipped; deletes follow identity, not TOC).
- **FR5.2** The document shall include an explicit extension path for other TOCs in the CIF file that keeps the same identity and calendar rules (FR2.4, FR3.1, FR3.3) and does not require a new receipt or sequence mechanism.

### FR6 — Layering the later implementation must follow

- **FR6.1** When implementation is later written, lake / transform / ingest code shall be a sibling package under `server/` with its own `pyproject.toml` and tests. It shall not add Athena or S3 clients inside `live_sched`, and it shall not import `schedule_poc`.
- **FR6.2** Object-store and query-engine clients shall not live inside CIF parsers or the repository layer.
- **FR6.3** CFA / sequence intake is fail-closed (refuse leaves the current plan unchanged). Kafka overlay is fail-open after the raw archive object exists (FR4.5).

## Non-functional requirements

- **NFR1** Cost: for each FR3.1 lookup the document shall name the partition keys and projected columns that let Athena skip unrelated dates, TOCs, and overlay types. A later query-contract test passes when a fixture query for one train on one calendar date does not require a full-table scan of every journey or every Kafka object.
- **NFR2** Operability: the document shall name `dev` / `staging` / `prod` and the implied promote cadence (merge to `main` may roll staging; production needs a separate human approval and a tested rollback path). It shall not deploy those tiers.
- **NFR3** Security: the architecture markdown shall contain no secrets, Kafka keys, Darwin keys, or long-lived AWS access keys. Cloud controls (KMS, bucket policies, workgroup encryption) are named as later NFR / infrastructure work, not implemented here.
- **NFR4** Reliability of the described apply: an accepted CFA apply is one atomic published current-plan plus apply-history update; a refuse or mid-apply failure leaves the previous current plan queryable. Kafka overlay mutations are not required to share that same transaction as the CFA apply.

## Constraints

- This intent writes `docs/athena-timetable-lake.md` only. No runtime code, no S3 / Glue / Athena resources, no CI, no Operation stages.
- Classic scope; ideation (`intent-statement`, `scope-document`) was skipped.
- Follow live-sched ideas and CodeKB; do not copy `schedule_poc`, the untested XML/Avro-via-JSON path, the multi-member zip gap, or the UID-wide `list_journeys()` scan as a read pattern.
- Sequence always comes from HD columns 33–39, never the zip basename (`project.md` mandated rule).
- Affirmed `team-practices.md` apply: trunk-based squash-merge, architecture-first with staged implementation, test-after with suite-green until CI, named env tiers, no new formatter in this piece of work.
- Android client and Darwin LDB HTTP are out of this document’s change set.

## Assumptions

- “Receipt of the timetable CFA file” means a daily update zip containing one CFA member, as live-sched `cfa-update` already accepts, not a full MCA replace as the first ingest the document specifies.
- A full MCA baseline may be described as a one-time or rare load into the same current-plan grains so CFA apply has identities to update; it is supporting context, not a second product.
- “Cheapest” means Athena data scanned for the four FR3.1 lookups, not wall-clock latency SLOs (NFR stages own latency targets).
- Kafka transport remains Darwin Push Port JSON under `uR` / `sR`, classified as live-sched already classifies.
- `docs/architecture.md` stays as the existing product/server picture; this intent does not edit it.

## Out of scope

- Writing or deploying runtime ingest, Glue jobs, Athena workgroups, or buckets in this piece of work
- Updating `docs/architecture.md` (pointer from that file is not required)
- Full versioned history of every journey row after every accepted CFA
- Making Kafka types other than `TS`, `schedule`, and `deactivated` queryable in this document
- Dropping non-apply Kafka types at the door
- Station + time-window as a cheapest Athena target
- Persisting all TOCs in the first population (extension path only)
- Importing `schedule_poc` or adding lake modules inside `live_sched`
- Android, OpenLDBWS, or Darwin LDB changes
- CI pipeline, coverage floor, and Operation stages (classic skip)
- Credentials, KMS, GuardDuty, or other cloud controls as implemented resources

## Open questions

- Exact S3 bucket and prefix strings per environment (the document must name them; the strings themselves are a design choice).
- Columnar file format and Glue table names (the document must choose a layout that satisfies NFR1; Parquet-versus-alternatives is a design choice).
- Whether CFA receipt and Kafka archive share one bucket with different prefixes or use separate buckets (the document must pick one and keep FR2 and FR4).
- When a later intent projects the reserved Kafka types in FR4.4, which type is first — not decided here.
