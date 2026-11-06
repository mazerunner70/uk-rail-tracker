# Requirements Analysis Questions

These five points are not settled by the timetable page or the practices already affirmed. The three views, London Midland only, local Docker, TypeScript under `apps/local-timetable/`, a read-only SQLite file, and a thin first slice are already decided.

## Question 1: Which view is built first? 

The first build is the existing database, one view, and enough of a screen to see it. Which view is that one?

A. All trains now: London Midland journeys that run today, with the ones that have a problem marked.
B. A pair of stations: two station codes, the trains that call at both in that order, where those trains are, and the issues on that route.
X. Other (please specify)

[Answer]: B

## Question 2: Which database file does the reader open?

The reader must not create or rewrite the SQLite file. Live-sched already writes one. The new app also has `apps/local-timetable/db`.

A. Open the file live-sched already writes. `apps/local-timetable/db` only records where that file is. There is no second copy.
B. Open a file under `apps/local-timetable/db`. Something other than the reader places that file there. The reader still does not write it.
X. Other (please specify)

[Answer]: X the db should be in its own docker insatnce, and the data shoudl survive a restart / rebuild

## Question 3: How current is the screen?

The views are about trains now. How often does the screen ask again?

A. Once, when you open the view or change the two station codes. You see a new result by opening it again.
B. Every 60 seconds while the view stays open.
X. Other (please specify)

[Answer]: X the screen should ask every 30s , and this number shold be configurable on screen

## Question 4: Which clock is "now"?

A journey runs today when `service_day` says so. Where the train is uses the latest stop at or before now. Which clock decides today and now?

A. The clock of the machine running the server. Today is that machine's local calendar date.
B. UK time (Europe/London), even when the machine is set to another zone.
X. Other (please specify)

[Answer]: AB

## Question 4a: Which of those two clocks wins?

Question 4's answer names both the machine's clock and UK time. Those two clocks disagree when this machine is not set to UK time. Which one decides "today" and "now"?

A. The machine's own clock, even when that clock is not UK time.
B. UK time (Europe/London). The machine's time zone does not change today or now.
C. This machine is kept on UK time, so the machine's clock and UK time are the same clock.
X. Other (please specify)

[Answer]: C

## Question 5: How do the parts run?

The service has a database file, a server, and a screen, and it runs in Docker on this machine.

A. One Compose file starts the server and the screen. The database is the SQLite file mounted in. It is not its own container.
B. Three containers: one for the file, one for the server, and one for the screen.
X. Other (please specify)

[Answer]: B

## Learnings keep

Which notes should I keep for next time?

A. five questions only: first view, which SQLite file, refresh, clock, and how Docker runs the parts. The page and the affirmed practices already settle the three views, London Midland only, TypeScript layout, and a read-only database.
B. Question 4a answer C means this machine is kept on UK time, so Europe/London and the machine clock are the same clock for today and now.
C. The database is its own container and the SQLite file survives a restart and a rebuild. The reader still does not create or rewrite that file. Live-sched remains the writer.
D. The screen asks again every 30 seconds while a view is open. A control on the screen changes that interval. Reloading the page starts again at 30 seconds.
E. Where the train is uses forecast departure, then forecast arrival, then booked public departure, then booked public arrival. A stop time earlier than the origin stop's time is the next calendar day.
F. Stated the stop-time order in the requirements so a later build does not guess which clock column means "at or before now". Forecast departure wins, then forecast arrival, then the booked public times in that same order.
G. Keep none
X. Other (please specify)

[Answer]: Keep A, B, D, E, and F unchanged. Amend C: live-sched is not involved in any way with this, as live-sched is a proof of concept and this is an application that will run into the future. Project scope.

## Anything to add

Anything to add for next time?

A. Nothing to add
B. Add a note
X. Other (please specify)

[Answer]: A. Nothing to add

## Artifact reuse

Existing `requirements.md` and `requirements-analysis-questions.md` are on disk after jumping back to this stage. Keep them as they are, change them from that starting point, or rewrite from scratch?

A. Keep — accept the existing requirements as they are
B. Modify — use the existing requirements as starting context, then decide what should change
C. Redo from scratch — ignore the existing files and write this stage again
X. Other (please specify)

[Answer]: B

## Question 6: What should change in the existing requirements?

(select all that apply)

The current requirements still name live-sched as the writer and a database that already holds the timetable. Later work already decided the MCA load replaces the file and the screen starts empty. Which of those later decisions should be written into this requirements file now? FR1.2–FR6.2, the three views, the 30-second refresh, and three containers stay unless you say otherwise.

A. MCA load is the writer. Live-sched is not involved.
B. Empty screen, load button, progress on the same 30-second interval, train asks only after a successful load.
C. Split the old "reader never writes" rule: the query never writes; MCA load is the only writer.
D. Done is the empty screen, the load, the pair page, and the network page. Drop the assumption that the network view waits.
E. Add FR7 (the seven tables), FR8 (that day's MCA replace), and FR9 (empty screen). Keep the FR1–FR6 numbers.
F. The page paints only from the last successful answer. Hang, closed connection, or unreadable response is a failed ask; held trains stay; the status bar says comms is lost. Empty sentences stay: No trains, No data can be retrieved, No problems, Nothing in this filter, Nothing matches.
G. Today's MCA is the unique filename containing YYYYMMDD under apps/local-timetable/mca. Missing, duplicate, or unreadable fails and leaves SQLite as it was. No CFA. The header is accepted before any delete.
X. Other (please specify)

[Answer]: A, B, C, D, E, F, G
**Mode:** chat
**Timestamp:** 2026-10-27T22:25:55Z

## Learnings keep (rewrite)

Which notes should I keep for next time?

A. five questions only: first view, which SQLite file, refresh, clock, and how Docker runs the parts. The page and the affirmed practices already settle the three views, London Midland only, TypeScript layout, and a read-only database.
B. Question 4a answer C means this machine is kept on UK time, so Europe/London and the machine clock are the same clock for today and now.
C. The database is its own container and the SQLite file survives a restart and a rebuild. The reader still does not create or rewrite that file. Live-sched remains the writer.
D. The human amended that database note: live-sched is not involved in any way, because it is a proof of concept and this application will run into the future. The other five notes stay unchanged.
E. The screen asks again every 30 seconds while a view is open. A control on the screen changes that interval. Reloading the page starts again at 30 seconds.
F. Where the train is uses forecast departure, then forecast arrival, then booked public departure, then booked public arrival. A stop time earlier than the origin stop's time is the next calendar day.
G. Modify: keep the existing FR1–FR6 numbers and the three views
H. Chat: apply all seven change items (MCA writer, empty screen, split never-writes, done includes network, FR7–FR9, held-answer screen rules, unique day's MCA). Live-sched is not the writer.
I. Stated the stop-time order in the requirements so a later build does not guess which clock column means "at or before now". Forecast departure wins, then forecast arrival, then the booked public times in that same order.
J. Keep none
X. Other (please specify)

[Answer]: A, B, C, D, E, F, G, H, I

## Anything to add (rewrite)

Anything to add for next time?

A. Nothing to add
B. Add a note
X. Other (please specify)

[Answer]: A. Nothing to add
