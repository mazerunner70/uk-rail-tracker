# Requirements Analysis

Upstream inputs: `intent-statement.md`; CodeKB `business-overview.md`, `architecture.md`, `code-structure.md`; confirmed answers in `requirements-analysis-questions.md`.

## Intent analysis

Demonstrate the moving parts of running a live schedule in a new proof of concept under `server/poc/live-sched`: fully parse the MCA timetable file, store only London Midland (LM) schedules in a local database, document schedule-code handling, then run a one-hour Kafka consumer that archives every message, applies matching updates to that database, and logs each database change. This proof is separate from `server/poc/schedule/` and is the step that enables a later production database for a full schedule — that later database is not part of this proof.

## Functional requirements

### FR1 — MCA parse and LM schedule load

- **FR1.1** The prototype shall fully parse the MCA file at `server/poc/timetable/RJTTF944MCA.txt`.
- **FR1.2** The prototype shall store only London Midland (LM) company schedules from that parse into a local SQLite database under `server/poc/live-sched`.
- **FR1.3** Implementation shall live as a fully separate package under `server/poc/live-sched` that does not import the existing `schedule_poc` library (copy or rewrite as needed).

### FR2 — Schedule-code summary and algorithm documentation

- **FR2.1** After (or as part of) the MCA load, the prototype shall produce a summary that counts the schedule codes processed (for example LI).
- **FR2.2** The prototype shall produce a markdown document that states the algorithm used for processing each schedule code handled in the load.
- **FR2.3** The schedule-code summary and algorithm markdown shall be written under `server/poc/live-sched/docs/`.

### FR3 — One-hour Kafka live update

- **FR3.1** The prototype shall run a Kafka consumer for a fixed one-hour period using credentials loaded from a `.env` file at the workspace (repo) root.
- **FR3.2** During that hour, the prototype shall store every message received in a CSV file under `server/poc/live-sched/output/` for review.
- **FR3.3** For matching LM journeys, the prototype shall apply the same update kinds that the Schedule POC harvest/replay path already handles; other messages shall be ignored or logged without failing the run.
- **FR3.4** For each database change caused by an applied message, the prototype shall append a short (couple of lines) summary to a markdown change log under `server/poc/live-sched/output/`.

## Non-functional requirements

### NFR1 — Credential handling

- **NFR1.1** Kafka and related live-feed credentials shall be read from a `.env` file at the workspace root and that file shall remain gitignored (repo already ignores `**/.env`).

### NFR2 — Locality and scope of proof

- **NFR2.1** The proof shall run locally as a POC under `server/poc/live-sched` without requiring a production or AWS database for pass/fail.

### NFR3 — Existing suite

- **NFR3.1** Changes for this proof shall leave the existing repository test suite green; no extra new-test floor is required beyond what this POC needs to demonstrate FR1–FR3.

## Constraints

- Product boundary is the proof of concept in `server/poc/live-sched` only.
- TOC / company filter for stored schedules is London Midland (LM) only.
- Local SQLite under `server/poc/live-sched` is the schedule store for this proof.
- Package must not import `schedule_poc` from `server/poc/schedule/`.
- Output split: docs under `server/poc/live-sched/docs/`; CSV and DB-change log under `server/poc/live-sched/output/`.
- Kafka capture duration is fixed at one hour.
- A later production full-schedule database is the reason for the proof, not a deliverable of this workflow.

## Assumptions

- “Same update kinds as Schedule POC harvest/replay” can be determined from the existing Schedule POC behaviour and CodeKB during design/build, without importing that package.
- Variable names in the root `.env` may mirror the Kafka POC’s names for practicality, even though the file path is the workspace root.
- “Fully parse” means stream/process the MCA end-to-end for the codes this load handles, while persisting only LM schedules.

## Out of scope

- Importing or extending `server/poc/schedule/` as the product package (a separate `live-sched` package is required).
- Implementing a production or AWS database for the full national schedule.
- Android app wiring or Darwin LDB HTTP API work.
- Applying Kafka update kinds beyond those already handled by Schedule POC harvest/replay for matching LM journeys.
- Changing the one-hour capture window or omitting the CSV / markdown change log.

## Open questions

- Exact filenames under `docs/` and `output/` (to be fixed during design/build if not specified further).
- Concrete list of Push Port / Kafka update kinds treated as “same as Schedule POC harvest/replay” — confirm against Schedule POC behaviour during design without creating an import dependency.
