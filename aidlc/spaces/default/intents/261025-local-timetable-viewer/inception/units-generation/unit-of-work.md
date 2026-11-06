# Units of Work

Three code units. The database container is not a unit. It holds the SQLite file, stays up when the server container stops, and bind-mounts that file to a directory on this machine under `apps/local-timetable/db`.

Deployment for that container lives under `apps/local-timetable/db` (packaging, not a fourth unit). Both server units mount the same SQLite path. The screen container does not mount the file.

This decomposition is checked against components, decisions, requirements, and stories.

## Component mapping

| Domain component | Unit |
| --- | --- |
| McaInitialisation | mca-load |
| TimetableQuery | timetable-query |
| NetworkPage, StationPairPage | screen |

## Units

| Unit ID | Name | Directory | Kind | Complexity | Deployment |
| --- | --- | --- | --- | --- | --- |
| U1 | mca-load | u1-mca-load | service | L | Shared. Runs in the server container, separate from the query. |
| U2 | timetable-query | u2-timetable-query | service | M | Shared. Runs in the same server container as the MCA load. |
| U3 | screen | u3-screen | ui | L | Standalone. Runs in the screen container. |

## mca-load

Replaces the SQLite file from that day's MCA. London Midland only. No CFA. Refuses a header whose CIF columns 33 to 39 are not a valid sequence.

The file it replaces is the one the database container holds. The server container can stop and the file remains. A SQL client on this machine opens that same host path.

A load deletes the current file and writes a new one. A client that already has the old file open keeps that old file until it reconnects.

## timetable-query

Starts the MCA load and reports its progress. Reads the SQLite file for the network list and the station-pair list. Does not replace the file. Does not call `list_journeys()`.

A missing, deleted, locked, or older-schema file fails and the failure names which of those it is. Two codes that match no calling point are a successful empty list.

## screen

Both pages, the station boxes, the refresh interval, and the empty view. The interval starts at 30 seconds. Changing it replaces the wait from that moment. Reloading the page sets it back to 30.

The screen opens empty, with a button. The button asks the query unit to start the load. Until the load succeeds, that same interval polls the query unit for progress, and the screen does not ask for trains. When the load succeeds, the button goes away. The network page then asks on open and on each interval, even when the station boxes are empty. The pair page asks only when both boxes are filled.

A reload while a load is running shows that progress again. The load keeps running on the server. A failed load stays on the empty screen with the button, and the screen does not ask for trains.

The screen calls the query unit and no other unit. The page changes only when the held answer or load progress changes. Only the latest successful ask may write that answer. A failed ask leaves a held answer unchanged. With no held answer, a failed train ask says "No data can be retrieved". A hang, closed connection, or unreadable response keeps held trains, shows `comms is lost` in the status bar, does not retry until the next interval tick, and clears the bar on the next successful ask. The interval control refuses blank, fraction, or non-positive values and keeps the interval already in use.
