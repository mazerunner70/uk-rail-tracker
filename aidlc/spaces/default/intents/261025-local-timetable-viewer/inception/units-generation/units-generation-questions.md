# Units Generation Questions

The three containers are already decided: one holds the SQLite file, one runs the server, and one runs the screen. The four code blocks are already decided: the MCA load replaces the file, the query only reads it, and the two pages call only the query.

## Question 1: How do the four blocks sit in the three containers?

The database container keeps the SQLite file across a restart and an image rebuild. The MCA load is the only block that replaces that file. The query is the server the pages call. The two pages share the station boxes and the refresh interval.

A. Three units. The MCA load is its own unit and replaces the file held by the database container. The query is the server unit. Both pages are one screen unit.
B. Four units, one block each. The two pages stay separate units even though they share the station boxes and the interval.
C. Two units. The MCA load and the query ship together in the server container. Both pages are the screen unit. The database container holds the file and none of our code.
D. Three units. The MCA load is its own unit but runs in the server container, separate from the query. The database container holds only the file. Both pages are one screen unit.
X. Other (please specify)

[Answer]: D

## Question 2: When does the MCA load replace the SQLite file?

Opening a page does not run the load. The query only reads the file after a replace. A restart of the database container must still show the same journey rows.

A. The load runs once for that day, before the server and the screen are used. A later restart of the database container does not load the MCA again.
B. The load runs when the database container starts, and only when that day's timetable is not already in the file.
C. The load runs only when you start it by hand. Starting the three containers does not load the MCA.
X. Other (please specify)

[Answer]: X have the ui start empty but start the MCA load by a button press from the ui in empty mode, and show the load progressing by polling the server periodically

## Question 3: Which unit does the load button call?

The MCA load is its own unit inside the server container, separate from the query. The screen is its own unit. Earlier, the pages called only the query. The button on the empty screen starts the load, so the screen has to call something on the server.

A. The screen calls the MCA unit for the button and for the progress poll. It calls the query unit only for the train lists, after the load has finished.
B. The screen calls only the query unit. The query unit starts the MCA unit and reports how the load is going.
C. The screen calls the MCA unit for the button, the progress poll, and the train lists. The query unit is used by the MCA unit.
X. Other (please specify)

[Answer]: B

## Question 4: What does the empty screen show while the load runs?

The screen starts empty, with a button that starts the MCA load. It polls the server for progress. The train pages already ask on an interval that starts at 30 seconds, and they change only when the held train list changes.

A. The empty screen shows the button, then the load progress. It does not ask for trains until the load succeeds. The progress poll is its own loop, tighter than the 30 second train interval. When the load succeeds, the button goes away and the train pages ask as already decided.
B. The empty screen shows the button, then the load progress, using the same interval control that starts at 30 seconds. It does not ask for trains until the load succeeds. When the load succeeds, the button goes away and the train pages ask as already decided.
C. The empty screen shows the button and the load progress, and it also asks for trains on the usual interval while the load is still running.
X. Other (please specify)

[Answer]: B

## Modify after domain design (2026-11-01)

The three-unit chain is unchanged. Updates align with the revised component catalogue: explicit component-to-unit mapping, `comms is lost` and interval refusal on the screen unit, and US2.1 traceability emphasising screen-owned interval behaviour.

[Answer]: Applied.

## Plan approval

Three units in one chain: `mca-load` → `timetable-query` ← `screen`. Kinds: service, service, ui. Database container is not a unit. Empty screen starts load via query only.

A. Approve Plan
B. Revise Plan
X. Other (please specify)

[Answer]: A. Approve Plan

## Learnings keep

Which notes should I keep for next time?

A. Three containers and four code blocks are already decided. The open points are how those blocks sit in the containers, and when the MCA replace runs.
B. Q1 D: the MCA load is its own unit in the server container, the query is the other server unit, both pages are one screen unit, and the database container holds only the file. Q2 Other: the screen starts empty, a button starts the MCA load, and the screen polls the server for progress.
C. Q3 B: the screen calls only the query unit, and that unit starts the MCA load and reports progress. Q4 B: the empty screen uses the same 30 second interval for load progress, and it asks for trains only after the load succeeds.
D. Plan approved. Three units: mca-load and timetable-query share the server container. screen is its own container. The database container is not a unit.
E. Modify after domain design: same three-unit chain; component mapping, comms is lost and interval refusal on screen, traceability U1–U3, db packaging under apps/local-timetable/db.
F. Keep none
X. Other (please specify)

[Answer]: A, B, C, D, E

## Anything to add

Anything to add for next time?

A. Nothing to add
B. Add a note
X. Other (please specify)

[Answer]: A. Nothing to add
