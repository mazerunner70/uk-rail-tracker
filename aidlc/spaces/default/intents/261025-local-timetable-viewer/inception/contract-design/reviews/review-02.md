## Review

**Verdict:** NOT-READY
**Reviewer:** aidlc-architecture-reviewer-agent
**Date:** 2026-11-01T09:33:00Z
**Iteration:** 1

### Findings

| ID | Severity | Location | Finding | Required action | Status |
|---|---|---|---|---|---|
| R-01 | Major | aidlc/spaces/default/intents/261025-local-timetable-viewer/inception/contract-design/contract-summary.md > OpenAPI paths./load.post responses 409 and components.schemas.Failure | POST `/load` defines `409` with `Failure.reason` `load-refused`, while `202` is described as load started or already running and `LoadProgress.state` already covers `failed`. Upstream FR8.3 and `unit-of-work.md` describe header or MCA refusal as a failed load with progress and a sentence, not a separate HTTP refusal moment. Nothing states when the query returns `409` versus `202` with `state: failed` versus a later `GET /load` with `state: failed`, so the screen and query can diverge on start failure. | Pin one mapping: either drop `409` and surface all start refusal through `LoadProgress`, or document the single condition that yields `409` and how that relates to FR8.3 header refusal. | New |
| R-02 | Major | aidlc/spaces/default/intents/261025-local-timetable-viewer/inception/contract-design/contract-summary.md > OpenAPI components.schemas.StationPairAnswer.trains.items | Pair train objects require only `status` and `issue`. `serviceGuid` is nullable and `trainUid` is not required. Upstream FR3.3 and `components.md` require a cancelled today-row with null `service_guid` to still be shown keyed by `train_uid`; running rows need `service_guid` per FR1.6. The wire shape allows a row with neither identifier, so the screen cannot paint FR3.3 rows reliably. | Require `serviceGuid` on running rows; for cancellation-only rows allow null `serviceGuid` and require `trainUid`. | New |
| R-03 | Major | aidlc/spaces/default/intents/261025-local-timetable-viewer/inception/contract-design/contract-summary.md > 3. SQLite file shared-schema `older_schema` | The contract defines `older_schema` as any of the seven tables missing. Upstream FR6.3 also treats missing columns the views read (`forecast_arr`, `forecast_dep`, `public_arr`, `public_dep`, `seq`, `crs`, `status`, `toc`, `calendar_date`, `cancelled`) as older schema, with `503` bodies naming `older-schema`. A developer following only this contract can implement table checks and miss column-level failures required by requirements and `TimetableQuery` behaviour in `components.md`. | Align `older_schema` with FR6.3 (tables and named columns), or point explicitly to FR6.3 as part of the published contract rule for `older-schema` failures. | New |
| R-04 | Minor | aidlc/spaces/default/intents/261025-local-timetable-viewer/inception/contract-design/contract-summary.md > 1. Screen to query (comms failure prose) | FR5.6 and approved Q4 require that a still-open call is not yet a failed ask. The REST section states hang, closed connection, and unreadable response as failed asks and names `comms is lost`, but omits the still-open rule, so implementers may add a client timeout Q4 rejected. | Add the still-open-call rule beside the comms-lost behaviour so it matches FR5.6 and Q4 answer A. | New |
| R-05 | Minor | aidlc/spaces/default/intents/261025-local-timetable-viewer/inception/contract-design/contract-summary.md > 2. Query to load in-process spec | `start` and `progress` both return `LoadProgress`, and REST uses `202` on POST `/load` with polling on `GET /load`. The in-process spec does not state that `start` returns while the MCA replace continues, so a blocking `start` would make polling unable to show `running`, contradicting FR9.3 and `unit-of-work.md`. | State that `start` returns promptly with `running`, `idle`, or `failed` while replace work continues, and that `progress` reports completion. | New |
| R-06 | Minor | aidlc/spaces/default/intents/261025-local-timetable-viewer/inception/contract-design/contract-summary.md > OpenAPI components.schemas.LoadProgress | Q5 answer A and FR8.6 require how far the load has got plus a sentence. `LoadProgress` requires only `state` and `sentence`; `howFar` is optional with no rule for when it must be present (for example while `state` is `running`). | Require `howFar` when `state` is `running`, or state that the sentence always carries progress and `howFar` may be omitted. | New |
| R-07 | Minor | aidlc/spaces/default/intents/261025-local-timetable-viewer/inception/contract-design/contract-summary.md > 3. SQLite file `path` | FR7.1 and `components.md` name the file `apps/local-timetable/db/timetable.sqlite`. The shared-schema block lists `path: apps/local-timetable/db` without the filename, which is ambiguous for bind mounts and tests. | Set the contract path to the full file path in FR7.1 or name `timetable.sqlite` explicitly under that directory. | New |

### Validation Tool Results

| Tool | Result | Interpretation |
|---|---|---|
| (none listed) | Not run | The stage definition lists no validation tools. None were run. |

### Summary

The three integration edges match `unit-of-work-dependency.md`, the four REST asks align with FR9, and FR5.6 comms-lost behaviour is captured on the screen-to-query contract. Three major wire-shape gaps remain: POST `/load` `409` versus load progress failure, pair rows that can omit both identifiers, and an `older_schema` rule narrower than FR6.3.
