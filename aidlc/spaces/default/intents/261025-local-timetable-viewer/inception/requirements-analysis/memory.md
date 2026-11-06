<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
2026-10-25T16:00:00Z — five questions only: first view, which SQLite file, refresh, clock, and how Docker runs the parts. The page and the affirmed practices already settle the three views, London Midland only, TypeScript layout, and a read-only database.

- 2026-10-25T16:16:00Z — Question 4a answer C means this machine is kept on UK time, so Europe/London and the machine clock are the same clock for today and now.
- 2026-10-25T16:16:00Z — The database is its own container and the SQLite file survives a restart and a rebuild. The reader still does not create or rewrite that file. Live-sched remains the writer.
- 2026-10-25T16:36:00Z — The human amended that database note: live-sched is not involved in any way, because it is a proof of concept and this application will run into the future. The other five notes stay unchanged.
- 2026-10-25T16:16:00Z — The screen asks again every 30 seconds while a view is open. A control on the screen changes that interval. Reloading the page starts again at 30 seconds.
- 2026-10-25T16:16:00Z — Where the train is uses forecast departure, then forecast arrival, then booked public departure, then booked public arrival. A stop time earlier than the origin stop's time is the next calendar day.
- 2026-10-27T22:23:07Z — Modify: keep the existing FR1–FR6 numbers and the three views; rewrite the writer and the empty-screen start from later work rather than starting the file again.
- 2026-10-27T22:25:55Z — Chat: apply all seven change items (MCA writer, empty screen, split never-writes, done includes network, FR7–FR9, held-answer screen rules, unique day's MCA). Live-sched is not the writer.
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->

## Tradeoffs
- 2026-10-25T16:16:00Z — Stated the stop-time order in the requirements so a later build does not guess which clock column means "at or before now". Forecast departure wins, then forecast arrival, then the booked public times in that same order.

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
