# Requirements Analysis Questions

The request is already clear on the output (a server architecture markdown) and the shape (CFA zip into a receipt bucket, validate, apply into the Athena-backing buckets, Kafka messages into S3, cheap query layout). These six questions fill the gaps that would change the requirements.

### Question 1: Who is this architecture document for, and what counts as done?

You asked for the overall architecture now, with implementation later in stages. I need to know who must be able to act from the document.

A. You, as the later implementer — done when the document is specific enough to build the first ingest and query slice without reopening these decisions
B. A future platform/ops reader as well — done when it also names `dev` / `staging` / `prod` buckets, Glue/Athena objects, and the validate/apply flow
C. A short briefing only — done when the lakes, transforms, and query keys are sketched, with details left to later design
X. Other (please specify)

[Answer]:A 

### Question 2: Which Athena lookups must stay cheapest?

live-sched already queries journeys by CIF identity, calling points by stop sequence, and one row per train per calendar day after STP resolution. Athena charges by data scanned, so the partition and table grain should follow the lookups you actually run.

A. Same three as live-sched: identity, stop sequence, and train + calendar date
B. Those three, plus station + time window (what calls at this CRS today / this hour)
C. Those three, plus “current live overlay for this train today” (forecast vs booked)
X. Other (please specify)

[Answer]:C

### Question 3: What should Athena hold for the timetable after a CFA apply?

live-sched keeps one current SQLite plan and records new / override / duplicate / delete counts. A lake can keep every version or only the latest plan.

A. Current plan only — each accepted CFA replaces or updates the queryable tables; raw zips stay in the receipt bucket for replay
B. Current plan plus an apply history table (sequence, counts, which identities changed)
C. Full versioned history of every journey row after every accepted file
X. Other (please specify)

[Answer]:B

### Question 4: How should Kafka land, and what is queryable?

live-sched writes every message to a CSV first, then applies only `TS`, `schedule`, and `deactivated` when a journey already exists.

A. Archive every message in S3; Athena queryable tables are timetable-only in this document
B. Archive every message, and also project the same three apply types onto queryable overlay columns
C. Archive only the three apply types; ignore other messages at the door
X. Other (please specify)

[Answer]: X. keep every message in s3 surface the three for now, but expect later uses for the remaining messages 

### Question 5: Which timetable population does this architecture cover?

live-sched persists TOC `LM` only. A full MCA is much larger.

A. London Midland (`LM`) only, matching live-sched
B. All TOCs in the CIF file, with the same identity and calendar rules
C. `LM` first in the document, with an explicit extension path for other TOCs
X. Other (please specify)

[Answer]:C

### Question 6: Where should the architecture markdown live, and what is out of this piece of work?

This intent produces the document, not the buckets. Classic already skips CI and deploy.

A. New file under `docs/` (for example `docs/athena-timetable-lake.md`); no runtime code, no AWS resources in this piece of work
B. Update the existing `docs/architecture.md`; no runtime code, no AWS resources
C. New file under `docs/` and a short pointer from `docs/architecture.md`; still no runtime code
X. Other (please specify)

[Answer]: A
