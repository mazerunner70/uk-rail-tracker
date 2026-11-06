<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
- 2026-11-01T09:13:00Z — Keep after the requirements rewrite leaves the two pictured train boards. The product lead's advisory review says they do not cover the empty screen, the load button, or that day's MCA progress.
- 2026-11-01T09:11:58Z — Keep: accept the existing mockups, interaction spec, design-system mapping, and accessibility checklist as they are after the requirements rewrite. No regeneration.
- 2026-10-25T17:45:00Z — No wireframe exists for this intent. The mockups start from the approved stories. Four questions only: page arrangement, when a station-code edit asks, what stays visible during a later ask, and what is kept when switching views.
- 2026-10-25T18:38:00Z — The human pointed at docs/local-timetable/rail controller dashboard.jpeg and docs/local-timetable/station-pair.png as the two mockups. The old loading and navigation questions were rewritten around those pictures.
- 2026-10-25T18:45:00Z — Keep every panel from the two pictures. A panel the file cannot fill says it has no data. A station change asks at once. The UI reads a data layer and stays as it is until that layer changes. A failed retrieval does not change the layer.
- 2026-10-25T19:37:00Z — A stop time earlier than the origin stop is the next calendar day. The rule stays because a schedule can run for a couple of hours after midnight.

## Deviations
- 2026-10-25T18:50:00Z — Ideation wireframes and user-flow are absent because classic scope skips them. The pages were drawn from the stories, the requirements, and the two pictures.

## Tradeoffs
- 2026-10-25T18:50:00Z — Pictured panels the file cannot fill stay on the page and say they have no data. Counts the story rules can compute are filled from the data layer. Footer counts stay unfilled until the first success so a zero does not look like an empty board.

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
