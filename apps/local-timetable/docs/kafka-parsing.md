# Kafka / Darwin Push Port parsing

Working notes for how Darwin messages arrive, what the fields mean, and which
user-visible events they imply. Companion to the implementation plan in
[`kafka-updates.md`](./kafka-updates.md).

**Reference pack:** [`darwin-push-port/`](./darwin-push-port/) (specs + XSDs). Do not confuse the docs:

| Doc | Role |
|-----|------|
| [`darwin-push-port/RSPS5053 P-01-01 Darwin PubSub via RDM.pdf`](./darwin-push-port/RSPS5053%20P-01-01%20Darwin%20PubSub%20via%20RDM.pdf) | How to consume Kafka on RDM (topics, credentials, SSL). Not field semantics. |
| [`darwin-push-port/P75301004 Push Port Data Interface Specification Issue 27.pdf`](./darwin-push-port/P75301004%20Push%20Port%20Data%20Interface%20Specification%20Issue%2027.pdf) | Field meanings per message type — especially **§3.3**. |
| [`darwin-push-port/`](./darwin-push-port/) XSDs (prefer `rttiPPTSchema_v19.xsd`, `rttiPPTForecasts_v4.xsd`, `rttiPPTSchedules_v5.xsd`) | Machine-readable Push Port field model (RSPS5051 family). JSON topic keys match these names. |
| RDM product → Reference Material | JSON schemas for the JSON topic (not checked in here). |

Code: `apps/local-timetable/server/src/pushPort.ts` (decode/classify),
`overlayPlan.ts` (write plan), `overlayWriter.ts` (SQLite apply).

---

## 1. Current processing pipeline

```text
Kafka record (JSON topic)
  → decodePushPort          unwrap { bytes: "<json>" } if present
  → updateItems             walk uR then sR; yield TS | schedule | deactivated only
  → resolveServiceGuid      rid → service_aliases, else uid+ssd → service_day
  → planOverlayWrite        bound → write; unbound → miss
  → apply in one SQLite txn (+ always advance kafka_offsets)
  → applied_updates row     audit copy of the item JSON
```

Rules that matter when reading payloads later:

- Only **London Midland** journeys are in SQLite. Other TOCs bind-miss; offset still advances.
- Booked **`public_arr` / `public_dep` never change** from Kafka.
- **TS** only updates `calling_points.forecast_arr` / `forecast_dep` for matching **TIPLOC**.
- The UI “problem” flag is **not** read from Kafka directly: it is
  `forecast_* ≠ public_*` on any stop, or `journeys.status = 'deactivated'`.
- “Where is the train?” on the board is inferred from stop clocks vs London now
  (`reachedAndNextCrs`), not from the latest TD berth.

Ignored today (offset advances, no SQLite overlay): `association`,
`scheduleFormations`, `formationLoading`, `serviceLoading`, `trainOrder`, `OW`,
`trainAlert`, `trackingID`, `alarm`, `stationInformation`, and any other Push
Port child under `uR`/`sR`.

---

## 2. Envelope and identity

### Kafka → Push Port body

```text
record.value
└── { "bytes": "<UTF-8 JSON string>" }     ← common RDM envelope
     └── { ts, version, uR | sR, ... }
          └── update child(ren): TS, schedule, deactivated, …
```

- `uR` = live update; `sR` = snapshot. Same child semantics.
- Wrapper metadata (`updateOrigin`, `requestSource`, `requestID`) is not an update type.
- One Kafka record may contain **zero or many** update children; each child may
  be a single object or an array.

### Identity fields (on TS / schedule / deactivated items)

| Field | Meaning | How we normalise |
|-------|---------|------------------|
| `rid` | Darwin **run** id (unique for this day’s run of the service) | string as-is; stored in `service_aliases` |
| `uid` | Timetable **train UID** (slot across days) | also accept `trainUid` / `train_uid` |
| `ssd` | Schedule start date | digits only; `YYYYMMDD` → `YYMMDD` for `service_day.calendar_date` |

Bind order: **rid alias first**, else **uid + ssd** on `service_day`. Never insert a new journey from Kafka.

---

## 3. Field glossary (locations and times)

Seen mainly on **TS** `Location` nodes (Push Port §3.3.4).

### Location identity and schedule times

| Field | Meaning |
|-------|---------|
| `tpl` | TIPLOC of the location |
| `wta` / `wtd` | Working time arrival / departure (operational timetable; often with seconds) |
| `wtp` | Working time **pass** (non-stop through the location) |
| `pta` / `ptd` | Public time arrival / departure (passenger timetable) |
| `plat` | Platform object; element text often appears as `plat[""]` |
| `platsup` / `cisPlatsup` | Platform suppressed for public display |
| `length` | Reported train length (coaches); may disagree with formation data |
| `LateReason` | Late-running reason code (needs reference-data lookup) |

Public times in our DB are CIF-loaded `public_arr` / `public_dep` (`HHMM`), not
copied from every TS `pta`/`ptd`.

### Movement / prediction nodes: `arr`, `dep`, `pass`

Each may carry:

| Subfield | Meaning |
|----------|---------|
| `at` | **Actual** time (train has arrived / departed / passed) |
| `et` | **Estimated** time |
| `wet` | **Working** estimated time (operational; not for public boards when it differs from public forecast) |
| `src` | Source of the time (`TD`, `Darwin`, `CIS`, `TRUST`, …) |
| `atClass` | How the actual was obtained (`Automatic`, `Manual`, `GPS`, …) |
| `atRemoved` | Actual withdrawn; treat as removed once |

**Call vs pass shape:**

| Activity | Schedule keys | Movement node |
|----------|---------------|---------------|
| Passenger call | `wta`/`wtd`, often `pta`/`ptd` | `arr` and/or `dep` |
| Pass-through | `wtp` | `pass` |

Example pass (actual through Wembley):

```json
{
  "tpl": "WMBY",
  "wtp": "12:58:30",
  "pass": { "at": "13:07", "atClass": "Automatic", "src": "TD" }
}
```

### How this app collapses times today

`locationForecasts` reads only **`arr` and `dep`**. For each, it takes the first
of **`at` → `et` → `wet`**, keeps four digits (`09:50` → `0950`), and writes
`forecast_arr` / `forecast_dep`.

Consequences:

- Actual and estimate share one column; presence of `at` is not retained as a flag.
- **`pass` is ignored** — pass-only TS updates do not move forecast columns.
- Platform, length, LateReason, suppression are not stored.

---

## 4. Message types we apply

### TS — actual and forecast (`update_type = 'TS'`)

User intent in the feed: “times / platforms / length at one or more locations
have changed.”

Apply:

1. Upsert `rid` → `service_guid` alias when present.
2. For each location with TIPLOC + `arr`/`dep` time: update matching
   `calling_points` forecast columns (missing side leaves previous value).
3. Insert `applied_updates` with full item JSON.

Match key is **TIPLOC** (`tpl`), not CRS. No matching calling-point row → that
location is skipped silently.

### schedule — cancel / schedule change (`update_type = 'schedule'`)

Full schedule rewrite semantics are rich in the Push Port spec. **This server
only acts on `can`:**

| `can` value | Effect |
|-------------|--------|
| true / `"true"` / `"1"` | `service_day.cancelled = 1` for uid+ssd+guid |
| false / `"false"` / `"0"` | clear cancel (`cancelled = 0`) |
| absent | leave cancel flag alone; still audit the row |

Calling-point public times are not rewritten from schedule messages.

### deactivated (`update_type = 'deactivated'`)

Sets `journeys.status = 'deactivated'`. Train stays visible as a **problem** on
the network board; does not flip `service_day.cancelled`.

---

## 5. User-interesting events

Grouped by what an operator cares about. Each row: how you spot it in Kafka,
and what this codebase does today.

### Train delayed / running late

**Signals in feed**

- TS `Location.arr` / `dep` / `pass` with `et` or `at` later than booked
  `pta`/`ptd` or `wta`/`wtd`/`wtp`.
- Optional `LateReason` on the TS item.
- Spec also allows an unknown-delay / manual delay indication on TS.

**This app**

- Delay is inferred after apply: any stop with `forecast_* ≠ public_*` → UI
  **problem**.
- No separate “minutes late” field; no LateReason display.
- Pass delays never appear unless an `arr`/`dep` forecast is also present.

### Train arrived at / departed a calling station

**Signals**

- `Location.arr.at` — actual arrival at a call.
- `Location.dep.at` — actual departure from a call.
- Prefer `at` over `et` when both appear across successive messages.

**This app**

- `at` is written into `forecast_arr` / `forecast_dep` (same columns as estimates).
- Board “Reached / Next” still comes from clock comparison on stop times, not
  from “latest actual TIPLOC”.

### Train passed a location (non-stop) — gap

**Signals**

- `Location` with `wtp` and `pass.et` (estimate) or `pass.at` (actual).
- Often sourced from TD (`src: "TD"`), with `atClass: "Automatic"`.

**Example progression**

1. Forecast pass: `"pass": { "et": "13:06", "src": "Darwin" }`
2. Actual pass: `"pass": { "at": "13:07", "atClass": "Automatic", "src": "TD" }`

**This app**

- **Not applied.** `locationForecasts` never reads `pass`, so SQLite and the
  board do not advance on pass-only updates. Inspect `applied_updates.payload_json`
  (or the live topic) to see these.

**Why it matters**

Between passenger stops, Darwin/TD progress is mostly pass reports. Without
them, “where is the train?” stalls until the next calling-point `arr`/`dep`.

### Train cancelled

**Signals**

- `schedule` with `can` true (whole-service cancel as we interpret it).
- Spec also covers per-location cancels inside richer schedule payloads (not
  interpreted here).

**This app**

- Sets `service_day.cancelled = 1` for that uid+date+guid.
- Network list removes it (delta `removed`); pair view can show a cancellation row.

### Train finished / deactivated

**Signals**

- `deactivated` item (typically rid-focused).

**This app**

- `journeys.status = 'deactivated'` → UI problem; remains on the diagram.

### Train rerouted / diverted / associated

**Signals**

- `schedule` changes to calling pattern / locations (Push Port schedule +
  diversion examples in §3.6).
- `association` for joins, divides, and related working.

**This app**

- **Not modelled.** Schedule body (beyond `can`) ignored; `association` ignored.
- Booked calling points stay as MCA-loaded.

### Platform change / platform suppressed

**Signals**

- TS `Location.plat` text and `platsrc` / `conf`.
- `platsup` or `cisPlatsup` true → do not show platform to the public (§3.3.4.1).
- Location or service suppression via `suppr`.

**This app**

- Not stored; UI platform column is still “no data”.

### Train length / formation different from expected

**Signals**

- TS `Location.length` (coach count from v11) — can conflict with formation data.
- `scheduleFormations` — coach list / ids / classes / toilet availability.
- `formationLoading` / `serviceLoading` — loading against a formation.

**This app**

- All ignored. No comparison to a booked expected length yet.

### Other alerts / station messages

**Signals**

- `trainAlert`, `OW` (station messages / suppression), `alarm`,
  `stationInformation`, `trainOrder`, `trackingID`.

**This app**

- Ignored (offset only).

---

## 6. Event tagging

Once signals in §5 are recognised on a bound update, tag the audit row with
stable event names. Tags are **additive**: one Kafka item may yield several
tags (e.g. `passed_location` + `delayed`).

**Implemented** in `server/src/eventTags.ts` (`detectEventTags`), wired through
`planOverlayWrite` → `applied_updates.event_tags`. The network board exposes
the distinct union of tags per train as `eventTags`; the FE Disruption summary
counts those tags over the filtered Live schedules set and can filter the table
by tag. Existing DBs gain the column via `ensureAppliedUpdatesEventTags` on
writer open.

### Pipeline

```text
Kafka item
  → bind rid / uid+ssd
  → detectEventTags (pure)
  → tags: EventTag[]
  → apply overlay write (as today)
  → store tags with applied_updates.event_tags
```

Misses (unbound TOC) still advance the offset and do not invent tags.

### Tag set

| Tag | Primary signals | Status |
|-----|-----------------|--------|
| `arrived` | TS `Location.arr.at` | Emitted |
| `departed` | TS `Location.dep.at` | Emitted |
| `pass_forecast` | TS `Location.pass.et` (no `at`) | Emitted (tag-only; pass not written to calling_points) |
| `passed_location` | TS `Location.pass.at` | Emitted (tag-only) |
| `call_forecast` | TS `arr.et` / `dep.et` without `at` | Emitted |
| `delayed` | movement later than booked `pta`/`ptd`/`wta`/`wtd`/`wtp` in the message; or TS `delayed` / `etUnknown` / `LateReason` | Emitted |
| `actual_removed` | `atRemoved: true` | Emitted |
| `cancelled` | schedule `can` true | Emitted |
| `cancel_cleared` | schedule `can` false | Emitted |
| `deactivated` | deactivated item | Emitted |
| `platform_set` | TS `Location.plat` present | Emitted (not stored on calling points) |
| `platform_suppressed` | `platsup` or `cisPlatsup` or location `suppr` | Emitted |
| `length_reported` | TS `Location.length` > 0 | Emitted |
| `formation_changed` | `scheduleFormations` | Deferred (update type ignored) |
| `rerouted` | schedule calling-pattern change / diversion | Deferred |
| `associated` | `association` join/divide | Deferred |
| `alert` | `trainAlert` / `OW` / `alarm` / `stationInformation` | Deferred |

### Storage

```text
event_tags  TEXT NOT NULL DEFAULT '[]'   -- JSON array of EventTag strings
```

Example row after a WMBY actual pass that is also late vs `wtp`:

```json
["passed_location", "delayed"]
```

Query sketch:

```sql
SELECT id, rid, service_guid, applied_at, event_tags, payload_json
FROM applied_updates
WHERE update_type = 'TS'
  AND event_tags LIKE '%"passed_location"%'
ORDER BY id DESC
LIMIT 50;
```

### Rules of thumb

- Tag **what this message asserts**, not the whole journey state. A later
  on-time forecast does not rewrite an earlier `delayed` audit row.
- Do not require UI “problem” to equal `delayed`; problem remains the board
  projection (`forecast ≠ public` or deactivated). Tags are the feed narrative.
- Pass is parsed for tagging only; `calling_points` still has no pass forecast
  columns, so board “Reached/Next” still ignores pass-only progress.
- Richer tags (`rerouted`, `formation_changed`, `associated`, `alert`) wait on
  applying those message types — still listed so the enum stays stable.

---

## 7. How the board derives status today

After overlays are applied:

| UI concept | Rule |
|------------|------|
| Status label | Priority: `cancelled` → `deactivated` → `delayed` (forecast ≠ public) → `on time` |
| Status colour | Amber (`.status.problem`) when the train has **changed from scheduled**; green (`.status.ok`) for `on time`. No delay-severity grading yet |
| Change from scheduled | `deactivated`, any calling point with both forecast and public set and unequal, or pair-row cancel |
| Reached / next CRS | Latest stop whose picked time ≤ London now; next row after that |
| Scheduled time | Picked time of the next stop (forecast dep → forecast arr → public dep → public arr) |
| Cancelled (pair) | `service_day.cancelled = 1` |

Picked stop time never includes pass forecasts, because those are never written.

---

## 8. Investigating a live train

Useful SQL patterns (replace UID / guid as needed):

```sql
-- Overlay audit for one UID
SELECT au.id, au.update_type, au.rid, au.applied_at, au.payload_json
FROM applied_updates au
JOIN journeys j ON j.service_guid = au.service_guid
WHERE j.train_uid = 'W70310'
ORDER BY au.id;

-- Pass actuals only visible in payload today
SELECT id, applied_at, payload_json
FROM applied_updates
WHERE update_type = 'TS'
  AND payload_json LIKE '%"uid":"W70310"%'
  AND payload_json LIKE '%"pass"%'
ORDER BY id DESC;

-- What the UI can see
SELECT seq, crs, tiploc, activity, public_arr, public_dep, forecast_arr, forecast_dep
FROM calling_points
WHERE service_guid = (
  SELECT service_guid FROM service_day
  WHERE train_uid = 'W70310' AND calendar_date = '261107'
)
ORDER BY seq;
```

---

## 9. Parsing complexity (what to expect next)

Darwin parsing gets hard when you move from “store forecasts” to “operator
events”. Likely growth areas, in rough dependency order:

1. **Pass handling** — read `pass.at`/`et` so mid-route progress and pass delays work.
2. **Event tagging** — detect §5 signals and store §6 tags on `applied_updates`.
3. **Actual vs estimate** — keep `at` distinct from `et`/`wet` (and honour `atRemoved`).
4. **Platforms and suppression** — `plat` + `platsup`.
5. **Cancel granularity** — per-location cancels inside `schedule`, not only `can`.
6. **Reroute / association** — schedule body + `association` joins/divides.
7. **Length / formation / loading** — reconcile `length` with `scheduleFormations`.
8. **Reason codes and alerts** — `LateReason`, `trainAlert`, `OW` with reference data.

Until those land, treat `applied_updates.payload_json` as the full fidelity log,
and SQLite forecast columns as a lossy projection for the board.
