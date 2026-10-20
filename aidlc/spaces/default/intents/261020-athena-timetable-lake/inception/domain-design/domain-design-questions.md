# Domain Design Questions

These settle the logical building blocks the later `docs/athena-timetable-lake.md` will specify. Components are code you write, not buckets or Glue. Already locked and not re-asked: LM first, HD sequence from columns 33–39, archive every Kafka message, overlay only `TS` / `schedule` / `deactivated`, sibling package under `server/`, no `schedule_poc`, fail-closed CFA / fail-open overlay, four cheapest lookups.

Upstream: `requirements.md`, `stories.md`, CodeKB `architecture.md`, `component-inventory.md`, `team-practices.md`, `mockups.md`.

### Question 1: How should the later lake package be split?

Live-sched is one process and one SQLite file (CodeKB). The lake has two intakes and one query surface. This choice is the component catalogue.

A. Three components: CfaIntake (receipt, validate, apply, history), KafkaOverlay (archive, classify, three-type project), PlanQuery (journey / calling-point / service-day / overlay grains)
B. Two components: TimetablePlan (CFA plus current-plan grains) and LiveOverlay (archive plus overlay project)
C. One TimetableLake component that owns intake, overlay, and grains
X. Other (please specify)

[Answer]:A

### Question 2: Do CFA receipt and Kafka archive share a bucket?

Requirements Open questions say the architecture file must pick this. Exact strings can wait; the sharing pick cannot.

A. One env-qualified bucket with prefixes for receipt, archive, and curated tables
B. Separate env-qualified buckets for receipt and archive, plus a curated location
X. Other (please specify)

[Answer]: B, as the bucket CFA bucket will be a location shared with the external cfa supp
ly system

### Question 3: How do first journey identities and the CRS/TIPLOC map enter an empty lake?

US2.1 / AC2.1.2 forbids CFA-only silence. Requirements assume a supporting MCA baseline into the same grains.

A. Supporting MCA baseline into the same current-plan grains (same identity and persist rules as CFA apply)
B. Another named path (please specify)
X. Other (please specify)

[Answer]: X yes, if the received zip contaions a MCA file do the full update, else expect the zip to contain a cfa and log an error if not

### Question 4: How does an overlay message bind to a current-plan journey?

Stories require at least one of Darwin `rid` alias or `(train_uid, scheduled start date)` via `service_day`, and forbid a UID-wide `list_journeys()` scan (the live-sched gap).

A. Try `rid` alias first; if missing, `(train_uid, scheduled start date)` via `service_day`; miss keeps the archive object and creates no journey
B. Require both keys to agree before projecting
C. Only `(train_uid, scheduled start date)` via `service_day` — `rid` is stored but not used to bind
X. Other (please specify)

[Answer]: A, log if this arises

### Question 5: How is `service_guid` minted?

Live-sched uses `SVC-{originCrs}{destCrs}{runDateYYMMDD}{originStdHHMM}` (CodeKB `api-documentation.md`). Stories review asked the architecture file to state the rule.

A. Keep the live-sched form
B. New opaque identifier per CIF identity `(train_uid, run_date, stp_indicator)`
X. Other (please specify)

[Answer]: B with an SVC- stable id per cif identity

### Question 6: What file layout do the cheap-lookup grains use?

NFR1 and the mockups review (accepted risk R-01 / R-02) need a layout pick so Athena can skip unrelated dates, TOCs, and overlay types. Glue table names can follow the grains.

A. Parquet objects on S3, one Glue table per grain (journey, calling_points, service_day, overlay, apply_history)
B. Name the grains only; leave columnar format to later infrastructure design
X. Other (please specify)

[Answer]: A
