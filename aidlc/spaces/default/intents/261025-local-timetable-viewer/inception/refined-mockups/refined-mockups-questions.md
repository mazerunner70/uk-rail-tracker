# Refined Mockups Questions

There is no earlier wireframe. These questions start from the approved stories. The stories already require two labelled station boxes, a loading line, the words "No trains", and a control on each view that opens the other. The four points below are the screen choices those stories leave open.

## Question 1: Which arrangement should the page use?

A. One page. A top bar shows the interval in seconds. The station-pair view puts the earlier station on the left and the later station on the right, with the list under the boxes. All trains now is the same page with the boxes hidden.
B. A different arrangement. Describe it under Other.
X. Other (please specify)

[Answer]: X use the mockup here: docs/local-timetable/rail controller dashboard.jpeg. tell me any problems with using this.

## Question 2: When does a station-code edit ask again?

Both boxes are already filled, and the list is showing. You change one code.

A. Ask at once with the codes now in the boxes, and show that answer.
B. Keep the previous list until the timer fires.
X. Other (please specify)

[Answer]: use the mockup here: docs/local-timetable/station-pair.png. tell me any problems with using this.

## Question 3: What do we take from the two pictures?

The pictures are the visual target. They also draw a map, a berth line, incident cards, and percentage tiles. The stories name `service_guid`, the reached station, the next station, and the problem or the word `cancelled`.

A. Keep the dark control-room look and the two pages. Draw the lists from the stories. Leave out the map, the berth line, the incident cards, and the percentage tiles.
B. Keep every panel in the pictures. A panel the timetable file cannot fill says that it has no data.
C. Draw the trains on the map and on the berth line as the pictures show.
X. Other (please specify)

[Answer]: B

## Question 4: When does a station change ask again?

The pair picture uses two station choosers. Both are already set, and the list is showing. You change one station.

A. Ask at once with the stations now chosen, and show that answer.
B. Keep the previous list until the timer fires.
X. Other (please specify)

[Answer]:A

## Question 5: What stays put when the page asks again, or when you open the other page?

Neither picture shows a loading line. The pair picture has a way back to Network Overview.

A. A later ask keeps the previous list and shows the loading line with it. Opening the other page keeps the interval, the two stations, and the last list for each page.
B. A later ask replaces the list with the loading line. Opening the other page starts that page fresh.
X. Other (please specify)

[Answer]: X The page is stable until the underlying data changes. There is a clear data layer under the UI. A failed retrieval does not cause the data to change.

## Learnings keep

Which notes should I keep for next time?

A. No wireframe exists for this intent. The mockups start from the approved stories. Four questions only: page arrangement, when a station-code edit asks, what stays visible during a later ask, and what is kept when switching views.
B. The human pointed at docs/local-timetable/rail controller dashboard.jpeg and docs/local-timetable/station-pair.png as the two mockups. The old loading and navigation questions were rewritten around those pictures.
C. Keep every panel from the two pictures. A panel the file cannot fill says it has no data. A station change asks at once. The UI reads a data layer and stays as it is until that layer changes. A failed retrieval does not change the layer.
D. Ideation wireframes and user-flow are absent because classic scope skips them. The pages were drawn from the stories, the requirements, and the two pictures.
E. Pictured panels the file cannot fill stay on the page and say they have no data. Counts the story rules can compute are filled from the data layer. Footer counts stay unfilled until the first success so a zero does not look like an empty board.
F. Keep none
X. Other (please specify)

[Answer]: B, C, E only (project only)

## Anything to add

Anything to add for next time?

A. Nothing to add
B. Add a note
X. Other (please specify)

[Answer]: A. Nothing to add

## Later decisions

These were decided after the questions above, and the pages were rewritten to match them.

The network page names the two stations at the top. The station-pair page opens from those names and paints at once. Invalid stations still open that page. Before it has held data, a failed attempt says "No data can be retrieved".

The timer does not move the screen. An interval and a station edit only attempt a load. The screen changes only when the held data changes. Only the latest success may change it. A failed attempt leaves the held data as it is.

The empty sentences are "No trains", "No data can be retrieved", "No problems", "Nothing in this filter", and "Nothing matches".

The pages are desktop only. A stop time earlier than the origin stop is the next calendar day, because a schedule can run for a couple of hours after midnight.

[Answer]: recorded in this chat

## Artifact reuse

Existing `mockups.md`, `interaction-spec.md`, `design-system-mapping.md`, and `accessibility-checklist.md` are on disk after jumping back through this stage. Keep them as they are, change them from that starting point, or rewrite from scratch?

A. Keep — accept the existing mockups as they are
B. Modify — use the existing mockups as starting context, then decide what should change
C. Redo from scratch — ignore the existing files and write this stage again
X. Other (please specify)

[Answer]: A

## Learnings keep (keep)

Which notes should I keep for next time?

A. Keep after the requirements rewrite leaves the two pictured train boards. The product lead's advisory review says they do not cover the empty screen, the load button, or that day's MCA progress.
B. Keep: accept the existing mockups, interaction spec, design-system mapping, and accessibility checklist as they are after the requirements rewrite. No regeneration.
C. No wireframe exists for this intent. The mockups start from the approved stories. Four questions only: page arrangement, when a station-code edit asks, what stays visible during a later ask, and what is kept when switching views.
D. The human pointed at docs/local-timetable/rail controller dashboard.jpeg and docs/local-timetable/station-pair.png as the two mockups. The old loading and navigation questions were rewritten around those pictures.
E. Keep every panel from the two pictures. A panel the file cannot fill says it has no data. A station change asks at once. The UI reads a data layer and stays as it is until that layer changes. A failed retrieval does not change the layer.
F. A stop time earlier than the origin stop is the next calendar day. The rule stays because a schedule can run for a couple of hours after midnight.
G. Ideation wireframes and user-flow are absent because classic scope skips them. The pages were drawn from the stories, the requirements, and the two pictures.
H. Pictured panels the file cannot fill stay on the page and say they have no data. Counts the story rules can compute are filled from the data layer. Footer counts stay unfilled until the first success so a zero does not look like an empty board.
I. Keep none
X. Other (please specify)

[Answer]: I

## Anything to add (keep)

Anything to add for next time?

A. Nothing to add
B. Add a note
X. Other (please specify)

[Answer]: A. Nothing to add
