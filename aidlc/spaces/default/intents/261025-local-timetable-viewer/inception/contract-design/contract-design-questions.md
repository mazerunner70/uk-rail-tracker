# Contract Design Questions

The screen already calls only the query unit. The query starts the MCA load and reports progress. Those two units share the server container. The screen is its own container. The SQLite file lives in the database container. There is no hosted API and no other machine.

## Question 1: How does the screen call the query?

The screen container has to reach the query in the server container for four asks: start the load, poll progress, the network list, and the station-pair list.

A. HTTP JSON on the Docker network. The query unit owns those four asks.
B. HTTP JSON on the Docker network, with one path that the screen posts to for every ask. The query unit still owns it.
C. Not HTTP. The screen reads the SQLite file itself.
X. Other (please specify)

[Answer]: REST API with json body as needed, and transport compression as needed

## Question 2: How does the query start the MCA load?

They share the server container. The screen never calls the load unit.

A. A function call in the same process. There is no HTTP between the query and the load.
B. HTTP JSON even though they share the container.
C. The query writes a row into the SQLite file, and the load reads that row.
X. Other (please specify)

[Answer]: A

## Question 3: Who owns each spec?

A contract needs one owner so a later change has a place to land.

A. The query unit owns the screen-to-query contract. The MCA load owns the SQLite file shape the query reads. The in-process start and progress call is not a published spec.
B. The query unit owns every spec, including the SQLite file shape.
C. One shared spec for all three units, owned by the query.
X. Other (please specify)

[Answer]:A

## Question 4: What happens if an ask does not return?

The screen already keeps a held train list when an ask fails. This is about a call that never comes back.

A. The screen waits for the local call. A hang is a failed ask. The held answer stays. There is no retry. The next interval is the next ask.
B. The screen waits a fixed number of seconds, then treats that as a failed ask. There is no retry. The next interval is the next ask.
C. The screen retries the same ask until it succeeds.
X. Other (please specify)

[Answer]: A, but do mention on status bar that comms is lost

## Question 5: What does the progress poll return?

The empty screen polls on the same interval that starts at 30 seconds, until the load succeeds.

A. A state of idle, running, succeeded, or failed, how far the load has got, and a sentence the empty screen can show.
B. Only a percent number.
C. Only the word running or finished.
X. Other (please specify)

[Answer]:A

## Question 6: How do the contracts change later?

This is one person, one machine, three containers rebuilt together.

A. There is no version number. A change is a new build of the containers together.
B. The query contract carries a version. A screen that does not match fails the ask.
X. Other (please specify)

[Answer]: A

## Keep after upstream refresh (2026-11-01)

Baseline contracts kept; `contract-summary.md` updated for advisory review R-01–R-07.

[Answer]: Applied.
