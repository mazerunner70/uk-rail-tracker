# Decisions

Architecture decisions for the timetable lake components. Each record uses Context, Decision, Consequences, and Alternatives Rejected.

## ADR-001: Three components — CfaIntake, KafkaOverlay, PlanQuery

### Context
Live-sched is one process and one SQLite file. The lake has two intakes (supplier zip, Kafka) and one query surface. Fail-closed CFA and fail-open overlay must not share a transaction (NFR4). Q1 asked whether to keep one block, two, or three. CodeKB already warns that live-sched CIF parsers drift across copies; two owners for CIF/CFA-to-grain would split STP and identity.

### Decision
Split the later sibling package into **CfaIntake**, **KafkaOverlay**, and **PlanQuery**. PlanQuery is the only writer of curated Parquet. **CfaIntake shapes** CIF/CFA records into journey, calling-point, and service_day grains and applies the STP winner C then O then P then N. **PlanQuery persists** those already-shaped rows. KafkaOverlay shapes overlay columns and rid aliases; PlanQuery persists those too.

### Consequences
Positive: overlay cannot roll back a CFA apply; sequence refuse cannot drop an archived Kafka object; Glue names and partition keys live in one place; STP and CIF identity have one owner.
Negative: two sync calls into PlanQuery instead of one in-process SQLite batch.
Neutral: Units Generation still decides whether these ship as one process or several.

### Alternatives Rejected
- **TimetablePlan + LiveOverlay (Q1 B):** mixes grain ownership with CFA apply, so overlay layout and CIF transform would split across writers.
- **One TimetableLake (Q1 C):** hides the fail-closed / fail-open boundary that `team-practices.md` already affirmed.
- **PlanQuery transforms CIF records CfaIntake validated:** would move STP winner next to persist and leave a second parser copy in intake validation.

## ADR-002: Separate receipt and archive buckets

### Context
Requirements Open questions required a shared-versus-separate pick. The CFA file arrives from an external supply system.

### Decision
Use **separate** env-qualified buckets for CFA receipt and Kafka archive, plus a curated location for Parquet. The receipt bucket is the drop zone **shared with the external CFA supply system**. Exact strings are written later in `docs/athena-timetable-lake.md`.

### Consequences
Positive: supplier IAM can be limited to the receipt bucket; archive retention and overlay classify stay off that grant.
Negative: three logical locations to name per env instead of one bucket with prefixes.
Neutral: curated prefix may be a third bucket or a prefix on a lake bucket; only receipt-versus-archive sharing is locked.

### Alternatives Rejected
- **One bucket, three prefixes (Q2 A):** cheaper to name, but the supplier would see or need to be denied the Kafka archive and curated prefixes in the same bucket policy.

## ADR-003: Receipt zip is MCA full update or CFA incremental

### Context
US2.1 forbids CFA-only silence on first identities and the CRS/TIPLOC map. Requirements assumed a supporting MCA baseline. Q3 asked for the named path. Live-sched MCA load does not persist feed sequence; empty store seeds 990. Leaving a prior `currentCode` after MCA would make the next CFA fail-closed refuse.

### Decision
If the receipt zip contains an **MCA** member, CfaIntake does a **full update** of Journey booked identity columns, CallingPoint booked times, and ServiceDay, **writes LocationMap**, **appends ApplyHistory**, and **clears FeedSequence** so the next CFA uses the empty-store seed 990. Else it expects a **CFA** member and the existing sequence / incremental apply. CFA sequence rules run **only on the non-MCA path**. If there is no MCA and no CFA, **log an error** and leave the current plan and FeedSequence unchanged. If both are present, MCA wins.

### Consequences
Positive: empty-lake bootstrap and a later full refresh use the same door as daily CFA; identities and LocationMap enter with the MCA; the next CFA is not blocked by a leftover sequence.
Negative: a zip that was meant to be CFA-only but also contains an MCA would full-replace booked grains. Daily CFA remains fail-closed on sequence.
Neutral: this widens the mockups CFA chapter to name an MCA member path; it does not add a second receipt bucket.

### Alternatives Rejected
- **Supporting MCA as a separate one-time load (Q3 A):** a second mechanism beside the supplier zip.
- **CFA-only until identities exist:** rejected by AC2.1.2.
- **Set FeedSequence from MCA HD:** MCA is a full refresh, not the next incremental code; live-sched does not persist that value.
- **Leave FeedSequence unchanged after MCA:** the next CFA would refuse against a stale currentCode.

## ADR-004: Overlay bind is rid, then service_day, and it is logged

### Context
Stories require at least one of Darwin `rid` alias or `(train_uid, scheduled start date)` via `service_day`, and forbid live-sched’s UID-wide `list_journeys()` scan.

### Decision
**Try `rid` first.** If it is missing, bind via **service_day**. **Log** when the fallback is used and when both miss. A miss keeps the archive object, creates no journey, and leaves booked times unchanged. When a message carries Darwin `rid` and bind succeeds, **KafkaOverlay writes ServiceAlias** (Glue table `service_aliases`) so later messages can bind rid-first.

### Consequences
Positive: `rid` is the Darwin key; service_day is the CIF calendar key; no full journey scan; first-seen rid lands via service_day then becomes the alias.
Negative: a wrong `rid` alias can attach overlay without consulting service_day (Q4 B would have required both to agree).
Neutral: log volume on fallback is an operations concern for a later NFR stage.

### Alternatives Rejected
- **Require both keys to agree (Q4 B):** safer join, more missed overlays on partial Darwin payloads.
- **service_day only (Q4 C):** ignores `rid`, which Darwin already sends on TS.
- **CfaIntake writes rid aliases:** CFA/MCA have no Darwin rid.

## ADR-005: `service_guid` is `SVC-` plus a stable CIF identity

### Context
Live-sched mints `SVC-{originCrs}{destCrs}{runDateYYMMDD}{originStdHHMM}`. That form omits `train_uid` and `stp_indicator`, so two trains or two STP rows can collide, and a CFA `R` that changes origin time changes the guid.

### Decision
Mint **`SVC-` plus a stable function of `(train_uid, run_date, stp_indicator)`**. Same CIF triple always yields the same guid. Origin, dest, and booked times stay attributes on Journey / CallingPoint.

### Consequences
Positive: MCA full update and later CFA `R` keep the same key; calling points and aliases do not move; Athena can still project human columns.
Negative: a row is no longer readable as origin/dest/time from the guid alone; Functional Design must pick the exact suffix encoding.
Neutral: the `SVC-` prefix keeps continuity with live-sched naming without copying its collision.

### Alternatives Rejected
- **Keep the live-sched origin/dest/time form (Q5 A):** readable, unstable, can collide.
- **Random UUID per insert:** not replay-stable across MCA and CFA.

## ADR-006: Parquet and one Glue table per grain

### Context
NFR1 and the mockups review required a file layout so Athena can skip unrelated dates, TOCs, and overlay types. Overlay cheap lookup had no layout slot (accepted risk R-02). FR3.1 item 4 and AC4.1.5 require one current overlay per train per calendar day. Partitioning that same entity by overlayType would invent a dummy type or break uniqueness.

### Decision
Store curated grains as **Parquet** on the curated S3 location. **One Glue table** each for `journey`, `calling_points`, `service_day`, `overlay`, `apply_history`, `service_aliases`, and `location_map`. Overlay **inherits** this layout. **OverlayProjection** is a **single current row** keyed `(trainUid, calendarDate)` with `forecastArrival`, `forecastDeparture`, `deactivated`, and `schedulePayload` columns. `overlayType` is not an identity or partition key. Partition keys skip unrelated dates and TOCs. Overlay types are skipped by **column projection** on that one row, not by partitioning on type.

### Consequences
Positive: column projection is cheap; overlay does not invent a second format; fixture query for one train on one date can avoid a full scan; aliases and the CRS map have named tables.
Negative: Parquet schema evolution is a later construction concern; not Iceberg/Hudi time-travel; a lookup that wants only TS still reads the overlay file group and projects columns.
Neutral: exact Glue names follow the grain names unless the architecture file picks synonyms.

### Alternatives Rejected
- **Name grains only, defer format (Q6 B):** would reopen NFR1 in infrastructure design.
- **Iceberg or Hudi:** extra catalog cost; this intent asked for cheapest scans, not versioned table time-travel (US7.3 is Won’t Have).
- **One overlay row per type keyed (trainUid, calendarDate, overlayType):** matches type partitions but is not unique on (trainUid, calendarDate) and contradicts FR3.1 / AC4.1.5.

## ADR-007: CfaIntake publish set is named; overlay stays merge-preserved

### Context
CfaIntake’s one atomic publish was “current-plan grains and apply history”, but PlanQuery owns seven entities. A replace of every PlanQuery entity on daily CFA would drop the CRS/TIPLOC map, rid aliases, and live overlay — the same blast radius CodeKB records when live-sched `load` deletes the SQLite file.

### Decision
Name the persist set on each CfaIntake path. **Refuse:** no PlanQuery entity writes; FeedSequence unchanged. **CFA accept:** replace Journey booked identity, CallingPoint booked times, and ServiceDay; append ApplyHistory; advance FeedSequence; leave LocationMap intact; **merge-preserve** OverlayProjection, ServiceAlias, Journey.status, and CallingPoint forecast columns. **MCA full update:** the same booked-grain replace plus **write LocationMap**, append ApplyHistory, and **clear FeedSequence**; overlay, aliases, status, and forecast columns stay merge-preserved.

### Consequences
Positive: daily CFA cannot wipe live overlay or rid aliases; MCA cannot wipe overlay the way live-sched file-delete does; LocationMap is written on MCA and left intact on CFA.
Negative: an MCA that should reset live overlay must be a later explicit ops path; this catalogue does not add one.
Neutral: KafkaOverlay writes OverlayProjection and ServiceAlias outside the CfaIntake transaction.

### Alternatives Rejected
- **Replace all PlanQuery entities on accept:** recreates the live-sched SQLite-delete blast.
- **Omit LocationMap from MCA publish:** empty-lake CRS/TIPLOC would stay missing (US2.1).
