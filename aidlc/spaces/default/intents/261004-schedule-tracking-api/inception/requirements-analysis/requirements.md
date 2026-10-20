# Requirements Analysis

Upstream inputs: `intent-statement.md`; CodeKB `business-overview.md`, `architecture.md`, `code-structure.md`; confirmed answers in `requirements-analysis-questions.md`.

## Intent analysis

Prove a cheap **local** schedule-maintenance approach for London Midland (LM) trains: load an offline daily schedule, apply harvested Kafka updates by replay, assign **stable journey ids**, and demonstrate accuracy via a scripted demo and automated tests that read the local store (database). Success gates a later AWS port and eventual Android wiring; this prototype does **not** ship an HTTP API or change the Android app.

## Functional requirements

### FR1 — Offline LM schedule load

- **FR1.1** The prototype shall load a London Midland (LM) subset of the daily standard schedule from offline/local material (reload-based; not a live remote pull during demo).
- **FR1.2** The loaded schedule shall be held in a local store that demo and automated tests can query without an HTTP server.

### FR2 — Kafka update replay

- **FR2.1** The prototype shall apply schedule updates by replaying harvested Kafka-derived update data into a working copy of the loaded schedule (not requiring a live RDM Kafka connection for the pass/fail demo).
- **FR2.2** After replay, the local store shall reflect the combined baseline schedule plus applied updates for the covered LM journeys.

### FR3 — Stable journey identity

- **FR3.1** The prototype shall assign a stable journey identifier to each tracked LM journey such that the same running journey remains addressable without relying on ephemeral Darwin `serviceId`.
- **FR3.2** Demo and automated tests shall prove that lookup by the stable id returns consistent schedule/update state for a journey across update application steps.

### FR4 — Demonstration and verification without HTTP

- **FR4.1** The prototype shall expose schedule and journey state for demo and tests through the local database (or equivalent local query path), not through a running HTTP API.
- **FR4.2** The prototype shall include a specific demo procedure and automated tests that show accurate schedules, applied Kafka updates, and working stable ids.

## Non-functional requirements

### NFR1 — Cost and locality

- **NFR1.1** The prototype shall run locally without AWS cloud services or AWS cloud costs for the pass/fail demonstration.

### NFR2 — Testability

- **NFR2.1** Automated tests shall be runnable locally as part of proving FR2 and FR3 (schedules, updates, stable ids).

### NFR3 — Reuse of existing exploration

- **NFR3.1** Implementation shall start from and remain grounded in existing material under `server/poc` (Kafka and timetable exploration), rather than requiring new supporting POC packs outside that folder.

## Constraints

- TOC scope for this prototype is London Midland (LM) only.
- No HTTP API implementation in this workflow; planned `/v1` shapes from `server/PLAN.md` are a **non-binding note** for a later AWS port only.
- Android app wiring is out of scope for this prototype (eventual follow-on after AWS port).
- Daily schedule source is treated as offline/reload; live full-feed CIF download during demo is not required.
- Live Kafka during demo is not required; harvested replay is the required path.

## Assumptions

- Harvested Kafka update samples sufficient for the demo can be prepared from or alongside `server/poc` exploration.
- An LM-filtered subset of schedule data is available or derivable for local load without implementing a full national CIF parser in the first pass (may be a thin path if FINDINGS already describe the filter approach).
- “Accurate schedules” for the demo means the local store matches the loaded offline baseline plus the replayed updates for the chosen LM sample set.

## Out of scope

- Implementing AWS infrastructure or following `server/PLAN.md` as the delivery target for this workflow.
- Implementing a running `/v1` HTTP API or OpenAPI server in this prototype.
- Changing or wiring the Android app to consume the prototype store.
- Full national (non-LM) schedule coverage.
- Production hardening, auth, multi-tenant ops, or cloud cost optimization.

## Open questions

- Exact LM sample size and which harvested Kafka update fixtures constitute the demo corpus (to be fixed during design/build if not already present under `server/poc`).
- Concrete schema of the stable journey id (format/algorithm) — decide in design/code from timetable FINDINGS and Kafka classification learnings.
