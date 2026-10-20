# Requirements Analysis Questions

## Q1. How should `server/poc/live-sched` relate to the existing Schedule POC?

Reverse engineering found CIF→SQLite LM load and Kafka harvest/replay already implemented under `server/poc/schedule/`, while `server/poc/live-sched/` does not exist yet. This answer decides whether the new proof reuses that code or starts a separate stack.

A. Put the new proof in `server/poc/live-sched`, and reuse / import the existing `schedule_poc` library for MCA load and Kafka apply where it fits
B. Put a fully separate package under `server/poc/live-sched` that does not import `schedule_poc` (copy or rewrite as needed)
C. Extend `server/poc/schedule/` in place, and use `live-sched` only as a thin runner/docs folder that calls into schedule
D. Not yet defined
E. Not applicable
X. Other (please specify)

[Answer]: B

## Q2. What database should hold the London Midland schedules for this proof?

The intent requires LM schedules in a database. The Schedule POC already uses local SQLite. This answer locks the storage target for the proof (not a later production database).

A. Local SQLite under `server/poc/live-sched` (same general approach as the Schedule POC)
B. Another local database engine I will name
C. Not yet defined
D. Not applicable
E. None
X. Other (please specify)

[Answer]: A

## Q3. Where should the run outputs be written?

Success needs a schedule-code count summary, a markdown algorithm note per schedule code, a one-hour Kafka CSV archive, and a markdown log of database changes. This answer sets their locations under the proof.

A. All under `server/poc/live-sched/output/` (or similar): code summary, algorithm markdown, Kafka CSV, and DB-change log markdown
B. Code summary and algorithm markdown under `server/poc/live-sched/docs/`; CSV and DB-change log under `server/poc/live-sched/output/`
C. Not yet defined — choose sensible defaults during build
D. Not applicable
E. None
X. Other (please specify)

[Answer]: B

## Q4. How should Kafka credentials and the one-hour capture work?

Credentials must live in a `.env` file. The existing Kafka POC already uses env-based RDM PubSub settings. This answer decides credential reuse and the capture rule.

A. Use a `.env` under `server/poc/live-sched` (can mirror the Kafka POC variable names), consume for a fixed 1 hour, write every message to CSV, and apply each applicable update to the database with a short markdown log line per DB change
B. Reuse the Kafka POC `.env` in place (path I will name), with the same 1-hour CSV + apply + markdown log behaviour
C. Not yet defined
D. Not applicable
E. None
X. Other (please specify)

[Answer]:B put the .env at the root for easy access and ensure .gitignored


## Q5. Which live messages must change the database in this proof?

“Apply those messages correctly” needs a pass/fail rule. The Schedule POC already classifies Push Port types and applies overlays. This answer sets how deep that apply must go for the proof.
sd
A. Apply the same update kinds the Schedule POC harvest/replay already handles for matching LM journeys; ignore or log the rest without failing the run
B. Apply only a smaller set of message types I will name
C. Persist every received message to CSV, but only apply types I will name to the database
D. Not yet defined
E. Not applicable
X. Other (please specify)

[Answer]:A

## Q6. Where exactly should the Kafka `.env` live?

For the one-hour capture you chose option B and also said put the `.env` at the root and keep it gitignored. Option B meant reuse the Kafka POC `.env` where it already is (`server/poc/kafka/.env`). This answer picks the path that the live-sched consumer must load.

A. Workspace root: `/.env` (repo root), gitignored
B. Live-sched root: `server/poc/live-sched/.env`, gitignored
C. Keep using `server/poc/kafka/.env` in place (true reuse of the Kafka POC file)
D. Not yet defined
E. Not applicable
X. Other (please specify)

[Answer]: A

## Consolidated Summary Confirmation

- Q1: Fully separate package under `server/poc/live-sched` that does not import `schedule_poc`
- Q2: Local SQLite under `server/poc/live-sched`
- Q3: Code summary and algorithm markdown under `server/poc/live-sched/docs/`; CSV and DB-change log under `server/poc/live-sched/output/`
- Q4: One-hour Kafka capture: every message to CSV; applicable updates applied to the DB with a short markdown log line per DB change; credentials via `.env` (path clarified in Q6)
- Q5: Apply the same update kinds the Schedule POC harvest/replay already handles for matching LM journeys; ignore or log the rest without failing the run
- Q6: Kafka `.env` at the workspace (repo) root, gitignored

Does this all look correct before I generate the requirements artifact?

- Looks correct
- Request changes

[Answer]: Looks correct
