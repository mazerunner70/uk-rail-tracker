# NFR Design Questions — mca-load

This pass designs how the MCA load meets its targets: stream the file, no wall-clock cap, fail before any replace, one load at a time. It does not design the two train lists or the screen.

Already locked and not re-asked: TypeScript in the server container; SQLite at `apps/local-timetable/db/timetable.sqlite`; MCA directory `apps/local-timetable/mca`; start returns at once; stream the day's MCA; no time cap; delete then write after an accepted header; in-process start/progress; no accounts; no hosted API; no Kafka or Darwin secrets; synthetic tests; no CI; no AWS, no replicas, no cache tier.

## Question 1: What is the SQLite file name under `apps/local-timetable/db`?

The load writes that file and the query later opens it. The directory is locked. The file name is not. A writer that invents a name can disagree with the reader.

A. `timetable.sqlite` in `apps/local-timetable/db`
B. A name you specify (write the file name)
X. Other (please specify)

[Answer]: A

## Question 2: How is `howFar` measured while the load is running?

Progress is a percent from 0 to 100 as text. The file is streamed, so the total number of journeys is not known until the end.

A. Percent of the MCA file's bytes that have been read so far.
B. Percent of CIF records seen, counted against a first full pass (two passes).
X. Other (please specify)

[Answer]: A
