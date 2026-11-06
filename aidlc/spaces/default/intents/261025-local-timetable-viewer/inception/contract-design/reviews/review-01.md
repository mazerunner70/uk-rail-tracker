## Review

**Verdict:** READY
**Reviewer:** aidlc-architecture-reviewer-agent
**Date:** 2026-10-26T18:39:00Z
**Iteration:** 1

### Findings

| ID | Severity | Location | Finding | Required action | Status |
|---|---|---|---|---|---|
| R-01 | Major | aidlc/spaces/default/intents/261025-local-timetable-viewer/inception/contract-design/contract-summary.md > OpenAPI paths./load.post responses 409 | POST /load defines 409 with Failure reason load-refused, while LoadProgress.state already includes failed, and unit-of-work.md says mca-load refuses an invalid CIF header during the load, not at a documented HTTP moment. 202 is also "Load started, or already running". Nothing states when the query returns 409 versus 202 with state failed versus a later GET /load with state failed, so the screen and query can map start-failure differently. | Name the single condition for 409, or drop 409, and say whether CIF header refusal is LoadProgress state failed or that 409. | New |
| R-02 | Major | aidlc/spaces/default/intents/261025-local-timetable-viewer/inception/contract-design/contract-summary.md > OpenAPI components.schemas.StationPairAnswer.trains.items | Pair train objects require only status and issue. serviceGuid is nullable and trainUid is optional. components.md StationPairAnswer includes serviceGuid; requirements.md FR3.3 still shows a cancelled today-row when service_guid is null, keyed by train_uid; FR1.6 still names the journey. The spec allows a pair row with neither identifier, so the screen cannot name that row. | Require serviceGuid on running rows. On cancelled rows with no journey, allow null serviceGuid and require trainUid. | New |
| R-03 | Minor | aidlc/spaces/default/intents/261025-local-timetable-viewer/inception/contract-design/contract-summary.md > 2. Query to load in-process spec | start and progress both return LoadProgress, and REST POST /load is 202 so the screen can poll GET /load. The in-process spec does not say start returns before the MCA replace finishes. If the query awaits a blocking start, 202 is sent only after the load ends and polling cannot show running, which contradicts unit-of-work.md screen behaviour. The two-call shape implies async, but it is not pinned. | State that start returns at once with state running, idle, or failed, that the replace continues in-process, and that progress is what reports succeeded or failed completion. | New |
| R-04 | Minor | aidlc/spaces/default/intents/261025-local-timetable-viewer/inception/contract-design/contract-summary.md > OpenAPI components.schemas.LoadProgress | contract-design-questions.md Q5 answer A requires a state, how far the load has got, and a sentence. LoadProgress requires only state and sentence; howFar is optional with no rule that it is present when state is running. | Require howFar when state is running, or state that the sentence always carries how far and howFar may be omitted. | New |
| R-05 | Minor | aidlc/spaces/default/intents/261025-local-timetable-viewer/inception/contract-design/contract-summary.md > OpenAPI components.schemas.StationPairAnswer.trains.items.status | status is an unconstrained string. components.md says a cancelled service_day row carries the word cancelled; requirements.md FR3.1 uses journeys.status deactivated. Query and screen can disagree on cancelled versus deactivated versus other journey status values. | Enum or otherwise pin status values, including cancelled for a today cancellation row and the journeys.status values for running rows. | New |
| R-06 | Minor | aidlc/spaces/default/intents/261025-local-timetable-viewer/inception/contract-design/contract-summary.md > 1. Screen to query hang rule | A hang is a failed ask and the status bar says comms is lost, matching Q4 (wait for the local call, no retry). Q4 rejected a fixed timeout (option B), but the spec does not say how a hang is told apart from an ask still in flight, so an implementer may add a timeout the answers refused. | State that only a closed connection or an unreadable response is a hang, with no timeout, and that a still-open call is not yet a failed ask. | New |

### Validation Tool Results

| Tool | Result | Interpretation |
|---|---|---|
| (none listed) | Not run | The stage definition lists no validation tools. None were run. |

### Summary

Every DAG edge has a contract, the four REST asks match the units, and ownership is named (query owns REST; mca-load owns the SQLite shape and the in-process calls). Two major holes remain: when POST /load is 409 versus progress failed, and pair rows that can omit both serviceGuid and trainUid.
