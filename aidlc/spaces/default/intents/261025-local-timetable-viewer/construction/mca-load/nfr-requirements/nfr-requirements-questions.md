# NFR Requirements Questions — mca-load

This pass sets measurable targets for the MCA load that replaces the SQLite file. It does not set targets for the two train lists or the screen.

Already locked and not re-asked: Docker on this machine for one person; no accounts and no hosted deploy; three containers; the SQLite file survives a database-container restart and rebuild; this unit is the writer of that file; tests use a synthetic MCA and do not open a production timetable file; local secret scanning, dependency checks, and a dynamic check, with no CI pipeline; no Kafka or Darwin secret values in this unit; TypeScript in the server container; SQLite at `apps/local-timetable/db/timetable.sqlite` (query reads the same file); start returns at once; a missing or unreadable MCA fails and leaves the file; only one load at a time; howFar is a 0–100 percent as text while running.

Upstream: MCA load functional spec and rules, requirements NFR1–NFR6, in-process start/progress, scanned technology stack.

## Question 1: How long may a successful replace take?

Start returns at once. The screen polls progress on the same interval that starts at 30 seconds. A production MCA can be multi-GB. The requirements do not name a time bound.

A. No wall-clock cap. Progress stays `running` until the replace succeeds or fails.
B. Fail the load if it is still running after a number of minutes (say how many).
X. Other (please specify)

[Answer]: A

## Question 2: How should the load use memory while reading that day's MCA?

Tests must not open a production MCA. The real load still reads that day's file, which can be large. A whole-file read can exhaust this machine.

A. Stream the file. Do not require the whole MCA in memory.
B. Reading the whole file into memory is fine on this machine.
X. Other (please specify)

[Answer]: A
