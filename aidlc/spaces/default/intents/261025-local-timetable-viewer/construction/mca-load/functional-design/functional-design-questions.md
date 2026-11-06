# Functional Design Questions — mca-load

This pass designs how the MCA load replaces the SQLite file. It does not design the two train lists or the screen.

Already locked and not re-asked: the load replaces the file from that day's MCA; London Midland only; no CFA; a header whose CIF columns 33 to 39 are not a valid sequence is refused; the file lives in the database container and is bind-mounted at `apps/local-timetable/db`; a load deletes the current file and writes a new one; a SQL client that already has the old file open keeps that old file until it reconnects; the query starts the load by a function call in the same process and polls progress; progress is idle, running, succeeded, or failed, plus a sentence, and how far when running; start returns at once; tests use a synthetic MCA; the seven-table file shape is owned by this unit.

Upstream: unit-of-work U1 mca-load, story map US3.1, requirements, McaInitialisation in components, contract-summary in-process start/progress and the SQLite file.

## Question 1: Where does the loader find that day's MCA?

The day's MCA is on this machine. The load runs inside the server container. A path the container cannot see cannot be loaded.

A. A path you set when the server container starts (an environment variable). That path is a file bind-mounted into the container.
B. A fixed file next to the SQLite file, under `apps/local-timetable/db`, with a name this unit always uses.
C. A directory of MCA files bind-mounted into the container. The loader picks the file whose date matches today on this machine (Europe/London).
X. Other (please specify)

[Answer]:C

## Question 2: What happens if start is asked while a load is already running?

The contract allows both "already running" as current progress, and a refused start.

A. Return the current running progress. Do not start a second load.
B. Refuse the second start (`load-refused`). The running load continues.
X. Other (please specify)

[Answer]: A

## Question 3: What happens if start is asked after a load has already succeeded?

The empty screen's button goes away after a success. The start function still exists. A later empty screen (a new open of the page) could press the button again.

A. Start a new replace from that day's MCA.
B. Keep the succeeded progress. Do not replace the file again until the server process restarts.
C. Refuse (`load-refused`).
X. Other (please specify)

[Answer]: A

## Question 4: What is how far while the load is running?

Progress always has a sentence. While it is running, how far is also present.

A. A percent from 0 to 100, as text (for example `40`).
B. A count of CIF records processed, as text (for example `12000`).
C. A short phrase naming the current step (for example `reading header` or `writing journeys`).
X. Other (please specify)

[Answer]: A

## Question 5: What if that day's MCA is missing or unreadable when start is asked?

The load must not leave a half-replaced file if it has not yet begun the replace.

A. Progress becomes failed. The sentence says the MCA cannot be read. The SQLite file is left as it was.
B. Refuse immediately (`load-refused`). Progress stays idle if no load has run yet, or stays at its last success or failure.
X. Other (please specify)

[Answer]: A

## Question 6: How does a file in that directory match today?

Question 1 chose a directory of MCA files. The loader picks the file whose date matches today on this machine (Europe/London). The design needs how that date is recognised.

A. Today's date is in the filename as `YYMMDD`.
B. Today's date is in the filename as `YYYYMMDD`.
C. Today's date is the HD header date inside the file, not the filename.
X. Other (please specify)

[Answer]: B

## Question 7: Where is that MCA directory on this machine?

The directory must be visible inside the server container.

A. A path you set when the server container starts (an environment variable), bind-mounted into the container.
B. A fixed host path under `apps/local-timetable` (for example `apps/local-timetable/mca`).
X. Other (please specify)

[Answer]: B
