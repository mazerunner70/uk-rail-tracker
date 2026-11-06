# Local timetable

## Future intents

The future work in this note is one service: a local timetable viewer. The rest of this page describes it. Build that service later from this page. This page does not list other future work.

## The service

Run it in Docker on your own machine. It has three parts:

- A **database**, the SQLite file the live-sched proof of concept already uses.
- A **server**, a new reader of that file. It answers the three views below.
- A **UI**, a new local view of those answers.

The proof of concept at `server/poc/live-sched/` already fills the database. It is a Python command-line package (`live-sched`) and one SQLite file. It does not serve these views, and it has no UI. This page does not choose a UI toolkit and it does not give image recipes or install steps.

The server, and any new package that follows live-sched, does not import `schedule_poc`.

There is one reader: you, on this machine. The page does not describe accounts or a hosted deployment.

## The database

The file holds seven tables: `journeys`, `calling_points`, `service_day`, `service_aliases`, `feed_meta`, `kafka_offsets`, and `applied_updates`.

A journey's stable id is `service_guid`, shaped as `SVC-{originCrs}{destCrs}{runDateYYMMDD}{originStdHHMM}`. Dates are `YYMMDD` text. Times are `HHMM` text. Stops sit in `calling_points` in order, with booked public times and nullable forecast times.

The load keeps London Midland schedules (`toc = LM`). "All trains on the network" here means those LM journeys, not every operator in Britain.

## How the database is filled

The published timetable comes from two existing commands:

- **Load** reads a full MCA and persists LM rows. When the database path is a real file, that command deletes the file first, so a full reload also clears live overlays, Kafka offsets, and the feed sequence.
- **CFA update** applies a daily zip without deleting the file. Each schedule change is `N` (new), `R` (replace), or `D` (delete). A zip is accepted only when the HD Current File Reference, CIF columns 33–39, contains the next three-digit feed sequence. The zip file name is not the sequence.

Live running state comes from the **consume** command. It reads Darwin Push Port JSON for a bounded time and archives every message. Then:

- A `TS` message writes forecast times on the matching calling point. Booked public times stay as loaded.
- A `deactivated` message sets the journey status to `deactivated`.
- A `schedule` message is stored and does not rewrite booked times.

Kafka credentials stay in the environment (`KAFKA_CONSUMER_KEY`, `KAFKA_CONSUMER_SECRET`). They are not written in this page.

## All trains now

The network view lists LM journeys that `service_day` says run today and that are not cancelled.

A train **deviates** from the published timetable when either of these is true:

- A calling point's forecast time differs from its booked public time.
- The journey status is `deactivated`.

The view shows those trains as the ones with a problem. Booked times remain the published timetable. Forecast times are the live overlay.

## A pair of stations

You choose two station codes (CRS).

A train is on that route when its ordered calling points include both codes, the first before the second. The journey's overall origin and destination are not enough: those two codes are the ends of the whole journey, and the pair you chose may be stops in the middle.

**Where** the train is means the latest calling point at or before now, and the next calling point. The database has no GPS track of the train. The view names those two stops. It does not invent a point between them.

**Issues** on that route are the same deviations as on the network view, plus a today-row in `service_day` marked cancelled.

## What this page leaves out

This page does not build or run the service. It does not describe a hosted or cloud deployment. It does not add a second future-intents file. It does not extend the store beyond the LM timetable the proof of concept already keeps.
