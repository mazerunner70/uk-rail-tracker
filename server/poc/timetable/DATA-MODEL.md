# Timetable plan and Darwin live state

**Status:** data-model design for loading the published timetable and overlaying live running updates  
**Specs:** [RSPS5046 P-04-02](./RSPS5046%20P-04-02%20Timetable%20Information%20Data%20Feed%20Interface%20Specification.pdf) (*Timetable Information Data Feed Interface Specification*, RSP / DTD, 03-Jun-2025); Push Port message types as in [P75301004](https://www.raildeliverygroup.com/) §3.3, recorded in [`../kafka/FINDINGS.md`](../kafka/FINDINGS.md) and [`../../PLAN.md`](../../PLAN.md) §4.1  
**Identity:** Darwin `rid` lifetime in [`../../PLAN.md`](../../PLAN.md) §3.0

The published timetable and the live running feed are two layers. A timetable load replaces the plan. It does not replace Darwin state for a train that is already activated.

---

## 1. Layers

| Layer | What it stores | Written by | Key |
|-------|----------------|------------|-----|
| Plan | The published schedule, and the one schedule that applies on a calendar day | `MCA`, then each `CFA` | CIF identity |
| Live | How an activated run is actually going | Kafka, continuously | Darwin `rid` |

[`../../PLAN.md`](../../PLAN.md) §4.1 gives the timetable the published schedule and Push Port the live movement. [`FINDINGS.md`](./FINDINGS.md) states the same split: CIF is the base; Kafka adds live state and the `rid`.

---

## 2. Plan

A CIF schedule is one row, not one row per train per day. RSPS5046 §5.3.3.3 identifies it by **UID + start date + STP indicator**.

### `schedule`

| Column | Source |
|--------|--------|
| `train_uid` | BS columns 4–9 (RSPS5046 §5.5.2) |
| `date_runs_from` | BS columns 10–15 |
| `stp_indicator` | BS column 80: `P`, `O`, `C`, or `N` (§5.5.2) |
| `date_runs_to`, `days_run` | BS columns 16–21 and 22–28 |
| `toc`, headcode, calling points | `BX`, `LO`, `LI`, `LT` |

Primary key: `(train_uid, date_runs_from, stp_indicator)`.

`days_run` is seven flags, Monday through Sunday. `0000001` is Sunday only.

### `schedule_stop`

Booked public times for that schedule. Journey planners must use the public times (RSPS5046 §5.4.13). These columns are never updated by Kafka.

### `service_day`

The resolved plan for one UID on one calendar date. Primary key: `(train_uid, calendar_date)`. It points at the winning `schedule`, or at no schedule when that day is cancelled.

Resolution follows the STP definitions in RSPS5046 §5.5.2:

- A `C` whose dates and `days_run` include that day cancels the permanent schedule. `service_day` records the cancellation and no train runs.
- Otherwise an `O` that covers that day is the schedule that runs.
- Otherwise the `P` that covers that day is the schedule that runs.
- An `N` is a train with no permanent parent. It occupies that day on its own.

Permanent rows for one UID must not both apply on the same calendar day. Their date ranges may still overlap when `days_run` selects different weekdays. An overlay or cancellation sits inside a permanent window, on the days it names. It does not replace that whole window.

### Example

`G43966`, Sundays only (`0000001`):

| STP | Dates | What runs |
|-----|-------|-----------|
| `P` | 13 Dec 2026 – 9 May 2027 | Every Sunday in that window |
| `O` | 27 Dec 2026 | That Sunday only |
| `O` | 3 Jan 2027, 10 Jan, 17 Jan, 24 Jan | Each of those Sundays only |

On 27 Dec, 3 Jan, 10 Jan, 17 Jan, and 24 Jan the overlay replaces the permanent schedule. On every other Sunday in the window, only the permanent row matches. The five overlays do not overlap each other.

The same pattern caused the `live-sched` load failure for `G43975` on 13 Dec 2026: a permanent 12:22 Euston–Birmingham New Street (`SVC-EUSBHM2612131222`, STP `P`) and a one-day overlay at 12:23 (`SVC-EUSBHM2612131223`, STP `O`, `date_to` `261213`). Those are two schedules under §5.3.3.3. They are one `service_day`.

---

## 3. Live

### `activation`

| Column | Source |
|--------|--------|
| `rid` | Darwin running identity. [`../../PLAN.md`](../../PLAN.md) §3.0 records a lifetime of about 48 hours |
| `train_uid`, `ssd` | Push Port `schedule` / `TS` |
| `service_day` | Matched plan row, when the UID and service date resolve |
| `status` | `active`, or `deactivated` from Push Port `deactivated` (P75301004 §3.3.1) |

### `live_stop`

Forecast time, actual time, and platform from `TS` (P75301004 §3.3.4), keyed by `rid` plus location. It does not write `schedule_stop`.

A `schedule` message records the `rid` against the plan. A `deactivated` message sets `activation.status` and leaves the plan row in place.

Darwin only emits this layer after the service is activated. Dates that exist only in the `MCA` or `CFA`, outside that window, have no Kafka row. After `deactivated`, that `rid` receives no further updates. The national topic goes quiet in the small hours; it does not switch off. A consumer filtered to one operator can see a long gap. An empty `uR` is an acknowledgement with no change ([`../kafka/FINDINGS.md`](../kafka/FINDINGS.md), §3.3).

---

## 4. Loads

### `feed_receipt`

One row per file: sequence `nnn`, kind (`MCA` or `CFA`), current file reference, last-file reference, and update indicator `F` or `U` (header, RSPS5046 §5.5.1). Sequence `nnn` runs `001`–`999` and then rolls to `001` (§4.2).

1. **Full `MCA` (`F`), ad hoc.** First load for a new daily recipient (RSPS5046 §7.6.1), or any gap in the `CFA` sequence (§7.4.1). Replace `schedule`, `schedule_stop`, and `service_day`. Do not delete `activation` or `live_stop` for a `rid` that is still active. Re-link those rows to the new `service_day` on UID and service date.
2. **One `CFA` (`U`) per sequence, after the zip arrives.** Distribution is usually between about 22:30 and 01:00 (§7.3.1), so 02:00 is a sound time to apply the file when it is present. If it is not present, wait. The header’s last-file reference must be the file last applied. Transactions are `N` (insert), `R` (revise), and `D` (delete) on the CIF identity (§5.5.2). A delete is a `BS` record alone (§5.4.5). A day with nothing to apply is `HD` and `ZZ` only (§5.4.6). A missing sequence stops the chain. The next successful load is a new `MCA`, and its sequence need not follow the last one (§7.4.1).
3. **Kafka continuously**, including while either timetable load runs.

An empty DTD feed (§7.3.2) is the last good full files sent again, plus empty change files. That restores the last good day. It does not fill a sequence gap.

The full zip `RJTTFnnn.ZIP` does not contain a `CFA`. Its manifest (`RJTTFnnn.DAT`, §5.2) lists the `MCA` and the reference files. `RJTTCnnn.CFA` is in the update zip. Each `CFA` is that day’s transactions since the previous file, not the backlog since the last `MCA`. Applying only the newest `CFA` drops every earlier `N`, `R`, and `D`.

A full refresh header’s extract dates are a fixed quirk and are not the timetable window (§5.5.1.2). Use the `BS` date ranges. An update header’s start and end dates are the extract window for that `CFA`.

### Other files in the full zip

| File | Use |
|------|-----|
| `.DAT` | Manifest. Every name in it must be in the zip (§7.2.2). |
| `.MSN` | Station names, CRS, interchange. Always a full file for the live timetable. Passenger locations come from here, not from `TI` records (§5.3.5.2). Replace it when a new one arrives. |
| `.FLF`, `.ALF`, `.TSI` | Transfers and minimum interchange. Needed for connections, not for train schedules. Always full files. |
| `.ZTR` | Bus and ferry. Always a full file. Leave it out of the rail timetable. |
| `.REJ` | Schedules rejected before the `MCA` and `CFA` were built. Diagnostic only. |
| `.SET` | Effectively fixed. No timetable value. |

Joins and splits are `AA` records inside the `MCA` and `CFA`, not a separate file (RSPS5046 §5.3.4).

---

## 5. Which layer wins

| Question | Winner |
|----------|--------|
| Which train exists on a future day, and what are the booked times? | Plan. Kafka has no message for a run that is not activated. |
| Is today’s activated train late, cancelled, or on which platform? | Live. `TS` and `deactivated` override the display. Booked times stay on `schedule_stop`. |
| A `CFA` revises a schedule that already has a `rid` | Update the plan. Keep the live rows. The next `TS` refreshes the live times. |
| A full `MCA` is loaded while overnight trains are running | Rebuild the plan only. Active `rid`s keep their Darwin state. |

---

## 6. `live-sched` mapping

`server/poc/live-sched` maps this model onto POC tables:

| This note | `live-sched` |
|-----------|----------------|
| `schedule` | `journeys` keyed by `service_guid`, unique on `(train_uid, run_date, stp_indicator)` |
| `schedule_stop` | `calling_points.public_*` (booked; never written by Kafka) |
| `service_day` | `service_day` materialised after MCA load |
| Live forecast | `calling_points.forecast_*` from Push Port `TS` |

A second STP version of the same UID and start date (the `G43975` permanent 12:22 and overlay 12:23) loads as two journeys and one `service_day` row for that calendar date.
