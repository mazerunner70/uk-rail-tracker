# Practices Discovery Questions

Six points the draft and the reviews could not settle. Trunk-based squash-merge, test-after, the ban on importing `schedule_poc`, secrets in `.env`, and the project-local virtualenv already stand.

## Question 1: Where does new work land?

New work has been squash-merged onto `main` through a short-lived branch. This checkout is on `athena-timetable-lake`. Older milestone branches exist as history. Does that rule still hold for the local timetable service?

A. Yes. New work squash-merges to `main`. The current branch and the old milestone branches stay as history.
B. Keep this service on the `athena-timetable-lake` branch.
X. Other (please specify)

[Answer]: X create a new branch for this work, and squash merge at the end

## Question 2: Build a thin end-to-end slice first?

A walking skeleton is a minimal version that runs the whole way through, built first to prove the pieces connect before the real features go in. The earlier practice skipped that and started from the overall architecture, for the lake document. This service is the existing database, a new reader, and a new screen.

A. Yes. Build a thin running slice first: the existing database, one view, and enough of a screen to see it.
B. No. Design the whole local service first, then implement it in stages. Do not require a running slice before that design.
X. Other (please specify)

[Answer]:A

## Question 3: What tests does the new reader need?

The tables already exist. The reader and the screen do not. An older note said this kind of work produces no runtime tests, and that an 80% line-coverage gate waits until continuous integration exists. There is still no continuous integration.

A. The new reader gets tests now: unit tests plus a thin check of the three views (all trains today, trains that deviate, and a chosen pair of stations). The bar is that its own suite stays green. No 80% gate until continuous integration exists. Screen tests wait until a UI toolkit is chosen. Live-sched tests run only when that package changes. Reader tests must not change the SQLite file.
B. No new runtime tests for this service.
C. Require an 80% line-coverage gate on the new reader now, even though continuous integration does not exist.
X. Other (please specify)

[Answer]: A

## Question 4: Where does this service run?

The page says Docker on this machine, one reader, no accounts, and no hosted deployment. An older practice names `dev`, `staging`, and `prod`, with a separate approval before production.

A. This service stays local Docker only. Do not create those three environments for it, and do not add cloud controls.
B. Keep the three environment names in the practice text for later work, and do not build them for this service.
C. Plan a hosted deployment as part of this service.
X. Other (please specify)

[Answer]: A

## Question 5: What checks run before continuous integration exists?

Secret scanning, dependency checks, and security analysis were written as "when continuous integration exists." This workflow does not add that pipeline. The page also gives no image recipe. A container on this machine is still a running process.

A. Leave secret scanning, dependency checks, image pinning, and dynamic tests until continuous integration exists. Do not add a formatter or a security scanner in this pass.
B. Pin the image base and lock dependencies for this container. Leave secret scanning and dynamic tests until continuous integration exists.
C. Add local secret scanning, dependency checks, and a dynamic check of the local reader now, without standing up continuous integration.
X. Other (please specify)

[Answer]: C

## Question 6: Where does the new code live?

The page does not choose a server language or a UI toolkit. The reader must sit outside the live-sched package and must not import `schedule_poc`. It should not create or rewrite the SQLite file. If the file is missing, deleted, locked, or an older schema, the request fails.

A. New package at `server/<name>/`, screen in its own tree, not the Android app. No new formatter until the language and toolkit are chosen. Keep `service_guid` and the other store names. The three views are answered by this package, not by `list_journeys()`.
B. Same boundaries, but the package lives at `server/poc/<name>/`.
C. Use the existing Android app as the screen, and put the server at `server/<name>/`.
X. Other (please specify)

[Answer]: X typescript / FP (where possible) fro backend, FE is also typescript. This will be used fro the long term, but there will be other apps within this mono repo. place this code in ./apps/local-timetable/<db/server/fe>

## Learnings keep

Which notes should I keep for next time? (select all that apply)

A. team.md already has affirmed practices from earlier classic work, so this pass treats those sections as the baseline and only reopens what the local Docker timetable service does not settle.
B. Read the two Other answers as a short-lived branch squash-merged to main, and as TypeScript under apps/local-timetable/db, server, and fe
C. Left the quality review's tighter view cases and a local Docker smoke out of the hard rules
D. None
X. Other (please specify)

[Answer]: A, B, C (project only) — human said "keep all notes"

## Anything to add

Anything to add for next time?

A. Nothing to add
B. Add a note
X. Other (please specify)

[Answer]: A. Nothing to add A, B, C keep
