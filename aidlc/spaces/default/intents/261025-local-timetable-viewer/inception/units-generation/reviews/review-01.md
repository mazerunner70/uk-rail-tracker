## Review

**Verdict:** READY
**Reviewer:** aidlc-architecture-reviewer-agent
**Date:** 2026-10-25T22:05:00Z
**Iteration:** 1

### Findings

| ID | Severity | Location | Finding | Required action | Status |
|---|---|---|---|---|---|
| R-01 | Major | aidlc/spaces/default/intents/261025-local-timetable-viewer/inception/units-generation/unit-of-work-dependency.md > yaml units timetable-query depends_on | The edge adds a caller the component catalogue does not have. `components.md` gives `McaInitialisation` `dependents: []` and `TimetableQuery` `depends_on: []`, and the diagram text fallback states that no other block calls `McaInitialisation`. The yaml lists `timetable-query` `depends_on: [mca-load]`, and the integration row is a synchronous start and progress call. `unit-of-work.md` section `screen` starts that load from the empty screen and withholds train asks until the load succeeds. `components.md` `NetworkPage` behaviour and ADR-003 require the network page to ask on open and on each interval even when the station boxes are empty. ADR-001's consequence states that starting the day is a separate step from opening a page. Construction would inherit two call graphs and two ask rules. | Publish one caller and one ask rule. Drop the `timetable-query` to `mca-load` edge and the pre-load ask suppression so the units match `components.md`, ADR-001, and ADR-003, or write the amended graph into the unit artifacts in the same terms those upstream contracts will be held to: the query starts the load, and train asks wait until the load succeeds. | New |
| R-02 | Major | aidlc/spaces/default/intents/261025-local-timetable-viewer/inception/units-generation/unit-of-work.md > opening paragraph | US3.1 and NFR2 require three containers and a SQLite file that remains after the database container is restarted or its image is rebuilt. The requirements constraint places that file in `apps/local-timetable/db`. The opening paragraph states the database container is not a unit. No unit responsibility covers that container, the bind mount, or the survival behavior. U1 and U2 are both kind `service` with deployment Shared in the one server container, and neither responsibility owns that container definition. `unit-of-work-story-map.md` still assigns US3.1 to U1, U2, and U3, and `traceability.json` marks US3.1 OK with target `mca-load`. The container deliverable has no constructing owner. | Name one unit whose responsibilities include the database container, the bind mount at `apps/local-timetable/db`, survival across restart and image rebuild, and the single server container shared by `mca-load` and `timetable-query`. Point US3.1's coverage target at that unit. | New |
| R-03 | Minor | aidlc/spaces/default/intents/261025-local-timetable-viewer/inception/units-generation/traceability.json > coverage targets | Coverage targets are the yaml names `screen` and `mca-load`. The units table identifies those units as `U3` / `u3-screen` and `U1` / `u1-mca-load`. Story-map assignment rows use the yaml names and do not carry the Unit ID or the directory. The stage requires each OK target to be a Unit ID or a construction directory, and that same identifier on the story row. A join from a coverage target to a construction directory cannot use the target as written. | Set each coverage target to the Unit ID (`U1`, `U2`, `U3`) or the directory (`u1-mca-load`, `u2-timetable-query`, `u3-screen`), and put that same identifier on the story's row in `unit-of-work-story-map.md`. | New |

### Validation Tool Results

| Tool | Result | Interpretation |
|---|---|---|
| (none) | No validation tools were declared for units-generation | The stage definition lists sensors only. No validation tool was run. |

### Summary

The three units form one acyclic chain, every `depends_on` name resolves, and every story ID is assigned to a declared unit. Weigh two gaps before approval: the load edge and the pre-load ask rule contradict the component catalogue and ADR-003, and the database container required by US3.1 has no owning unit.
