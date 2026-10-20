# Requirements Analysis Questions

Upstream context used for these questions: intent statement; CodeKB business overview, architecture, and code structure (brownfield).

## Q1. Should this prototype follow the cheap local path, or the AWS-oriented plan in `server/PLAN.md`?

Intent success is an inexpensive non-AWS proof. Reverse Engineering found `server/PLAN.md` describes an AWS-first harvester and `/v1` API. Requirements cannot keep both as the primary delivery target.

A. Local-only prototype for this workflow — do not implement the AWS plan here; AWS is a later project
B. Follow `server/PLAN.md` AWS shape even for this prototype
C. Local prototype now, but keep the planned `/v1` API shapes from `server/PLAN.md` so a later AWS port is easier
D. Not yet defined
E. None
X. Other (please specify)

[Answer]: C

## Q2. What does “prove the schedule-maintenance approach” mean for a pass/fail demo?

The intent names inefficient per-route queries and missing stable journey ids that make the Android app lose train details mid-run. Success metrics so far emphasize cost and LM subset, not that outcome.

A. A locally runnable service assigns a stable journey id for an LM train and returns schedule/update state through an API without the caller needing ephemeral Darwin `serviceId`
B. Same as A, and a demo path shows the Android app (or a thin stand-in client) can keep following that train after Darwin `serviceId` would have been insufficient
C. Ingest and store LM schedule/update data locally is enough; API and Android continuity can wait
D. Not yet defined
E. None
X. Other (please specify)

[Answer]: X the AWS solution will solve the function issues described above, however this prototype will run locally to a demo will show the approach works with accurate schedules and kafka updates, and proof that the stable ids approach work. THere will be specific dmeo and automated tests. Success here will trigger an AWS port o fthe solution and eventual wiring to the android app

## Q3. What API surface is in scope for this prototype?

There is no implemented schedule HTTP API today. Planned `/v1` exists only in design docs.

A. Minimal read API: look up a journey by stable id and get current schedule/status for that journey
B. Read API plus a way to list or search LM journeys relevant to a station or time window
C. No HTTP API in this prototype — prove storage/identity only (for example CLI or in-process check)
D. Not yet defined
E. None
X. Other (please specify)

[Answer]: C schedules can be accessed through the db for demo and tests

## Q4. How should the prototype get CIF download and Kafka update data?

Existing exploration lives under `server/poc` (Kafka classify POC; timetable CIF design/sample). Live RDM credentials and full feeds may be costly or awkward for a cheap local proof.

A. Prefer recorded/sample data and local fixtures derived from `server/poc` (live feeds optional)
B. Must consume a live Kafka feed and a real CIF download for LM during the demo
C. Hybrid: CIF/sample for baseline schedule, and either recorded or live Kafka updates — I will clarify which
D. Not yet defined
E. None
X. Other (please specify)

[Answer]: C expecting the daily schedule download will only changes on reload so will always be 'offline'. the kafka feeds I can image will be harvested and can be played into a copy of the downloaded data as updates. this is critical as the source data feeds are complex and this prototype myay have a future use as a diagnostic tool

## Q5. Q1 kept the planned `/v1` API shapes for a later AWS port, but Q3 said this prototype has no HTTP API and uses the DB for demo/tests. Which is correct for this prototype?

We cannot treat both as primary scope without choosing how they relate.

A. No HTTP API in this prototype (Q3 wins). Document `/v1` shapes only as a non-binding note for the later AWS port — do not implement or require them here
B. Implement a minimal `/v1`-shaped HTTP API in this prototype as well as DB access (Q1 wins for the API)
C. No HTTP API now, but produce an OpenAPI (or similar) contract file matching planned `/v1` shapes without a running server
D. Not yet defined
E. None
X. Other (please specify)

[Answer]: A

## Consolidated Summary Confirmation

- Q1: Local prototype now, but keep the planned `/v1` API shapes from `server/PLAN.md` in mind so a later AWS port is easier.
- Q2: This prototype runs locally; demo and automated tests prove accurate schedules, Kafka updates, and that stable ids work. AWS (and eventual Android wiring) comes after success here.
- Q3: No HTTP API in this prototype — schedules are accessed through the DB for demo and tests.
- Q4: Daily CIF/schedule download is offline (reload-based). Kafka updates are harvested and replayed into a copy of that downloaded data; this may also support future diagnostic use.
- Q5: No HTTP API here (Q3). Document `/v1` shapes only as a non-binding note for the later AWS port — do not implement or require them in this prototype.

Does this all look correct before I generate the requirements artifact?

- Looks correct
- Request changes

[Answer]: Looks correct
