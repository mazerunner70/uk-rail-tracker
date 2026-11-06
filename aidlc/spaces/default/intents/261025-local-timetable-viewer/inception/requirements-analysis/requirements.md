# Requirements

## Intent analysis

One person, on this machine, wants to see London Midland trains: all that run today, a chosen ordered pair of stations, and which of those have a problem. A TypeScript server replaces the SQLite file from that day's MCA, then answers the views from that file. A TypeScript screen starts empty, starts that load, and shows the answers. The service runs in Docker on this machine. Live-sched is a proof of concept and is not part of this service.

The timetable page at `docs/future-intents/local-timetable.md` names the three views and the store. The `intent-statement` and `scope-document` name this local Docker service. `business-overview`, `architecture`, and `code-structure` record the seven-table store the views read. `team-practices` records the TypeScript layout, the test bar, and local Docker only.

Done for this service is the empty screen, the load, the pair page, and the network page.

## Functional requirements

### FR1. Station pair

The pair page is one of the two train views. The screen asks for it only after the day's load has succeeded, and only when both station codes are present.

- **FR1.1** The reader types two station codes. The screen asks the server only after both codes are present and the day's load has succeeded.
- **FR1.2** A train is on that route when one journey's `calling_points`, ordered by `seq`, include both codes with the first code on an earlier row than the second. Matching only `journeys.origin_crs` and `journeys.dest_crs` is not enough.
- **FR1.3** The journey is in scope for today when `service_day.calendar_date` is today's `YYMMDD` and `service_day.cancelled` is not 1. "Today" is defined in FR4.
- **FR1.4** For each in-scope journey the screen names two stops and does not invent a point between them. The reached stop is the latest `calling_points` row whose stop time is at or before now. The next stop is the following row in `seq` order. When no row is at or before now, there is no reached stop and the next stop is the first row. When the reached stop is the last row, there is no next stop.
- **FR1.5** A row's stop time is the first of these values that is present: `forecast_dep`, `forecast_arr`, `public_dep`, `public_arr`. A `HHMM` earlier than the origin row's stop time is the next calendar day, because a schedule can run for a couple of hours after midnight. Any other `HHMM` is on `service_day.calendar_date`.
- **FR1.6** The screen lists each matching journey's `service_guid`, the two station codes, the reached stop, the next stop, and whether it has an issue as defined in FR3.

### FR2. All trains now

- **FR2.1** The network view lists journeys with `toc = LM` that have a `service_day` row for today's `YYMMDD` with `cancelled` not 1.
- **FR2.2** Each row shows the `service_guid`, the reached stop, the next stop, and whether the journey has a problem as defined in FR3.1. A journey that is only a today cancellation does not appear on this view.
- **FR2.3** The network view asks on open and on each interval after a successful load, even when the station boxes are empty.

### FR3. Problems and cancellations

- **FR3.1** A journey has a problem when `journeys.status` is `deactivated`, or when any of its `calling_points` has a `forecast_arr` that differs from `public_arr`, or a `forecast_dep` that differs from `public_dep`. A null forecast does not differ from the booked time. The booked public times stay as stored.
- **FR3.2** On the station-pair view, an issue is a FR3.1 problem, or a `service_day` row for today's `YYMMDD` with `cancelled = 1` for a `train_uid` that has a journey whose ordered calling points include the pair as in FR1.2.
- **FR3.3** A cancelled today-row is shown on the pair view even when `service_day.service_guid` is null. It is not a running train. It has no reached stop and no next stop. It is not shown as a running train on the network view.

### FR4. Today and now

- **FR4.1** This machine is kept on UK time. Today and now are the Europe/London date and time of the machine running the server.
- **FR4.2** The server converts that local date to `YYMMDD` and that local time to `HHMM` before it compares them with `service_day.calendar_date` and with stop times.

### FR5. The screen asks again

- **FR5.1** While a view stays open, the screen asks the server again every 30 seconds. On the empty screen that ask is load progress. After a successful load that ask is the open train view.
- **FR5.2** The screen shows the interval in seconds and lets the reader change it. The next ask uses the new interval. A blank or non-positive value is refused and the interval already in use stays.
- **FR5.3** Reloading the page starts the interval at 30 seconds again.
- **FR5.4** An attempt that fails leaves any held answer unchanged, and the screen does not change. The failure is not shown as an empty board of trains. When the page has no held answer, that failed attempt is shown as "No data can be retrieved".
- **FR5.5** The page paints only from the last successful answer. Only the latest success may write that answer. An older success is discarded.
- **FR5.6** A hang, a closed connection, or a response the screen cannot read is a failed ask. Held trains stay. The status bar says `comms is lost`. There is no retry. The next interval is the next ask. A still-open call is not yet a failed ask. A later success clears that status line.
- **FR5.7** "Loading" is shown only before the first success. "No trains" is shown only for a successful empty list. The other empty sentences are "No problems", "Nothing in this filter", and "Nothing matches".

### FR6. What the server returns

- **FR6.1** The server answers the station-pair view, the network view, and the problem marking in FR3. It does not call `list_journeys()` to build those answers.
- **FR6.2** Store names in the answers stay `service_guid`, `train_uid`, `stp_indicator`, `origin_crs`, `dest_crs`, `run_date`, and `days_run`. Dates stay `YYMMDD` text. Times stay `HHMM` text.
- **FR6.3** When the SQLite file is missing, deleted, locked, or an older schema than the seven tables in FR7, the view request fails and the body says which of those four states was found. Older schema means a missing table from FR7, or a missing column the views read: `forecast_arr`, `forecast_dep`, `public_arr`, `public_dep`, `seq`, `crs`, `status`, `toc`, `calendar_date`, `cancelled`.
- **FR6.4** Two station codes that match no calling point are a successful empty list. The sentence is "No trains".

### FR7. The store

- **FR7.1** The file the database container holds is `apps/local-timetable/db/timetable.sqlite`.
- **FR7.2** The seven tables are `journeys`, `calling_points`, `service_day`, `service_aliases`, `feed_meta`, `kafka_offsets`, and `applied_updates`.
- **FR7.3** After an MCA load the overlay tables `kafka_offsets` and `applied_updates` exist and have no rows. Forecast times on calling points are empty after that load.

### FR8. That day's MCA replace

- **FR8.1** The only writer of the SQLite file is the MCA load. It looks only in `apps/local-timetable/mca`.
- **FR8.2** Today's file is the unique filename that contains today's `YYYYMMDD` on the Europe/London calendar. Zero matching files, or more than one, is a failed load. The SQLite file is left as it was.
- **FR8.3** An unreadable MCA, or a header whose CIF columns 33 to 39 are not a valid sequence, is a failed load. The sentence says the MCA cannot be read, or that the header was refused. The SQLite file is left as it was. The load locates the file, opens it, and accepts the header before any delete.
- **FR8.4** After an accepted header the load deletes the current SQLite file and writes a new one. Journeys are London Midland only. No CFA is applied.
- **FR8.5** A start while a load is already running returns that load's progress and does not start a second load. A later start after success does a new replace.
- **FR8.6** Progress is `idle`, `running`, `succeeded`, or `failed`, plus `howFar` as text from 0 to 100, plus a sentence.

### FR9. Empty screen

- **FR9.1** The screen opens empty, with a load button.
- **FR9.2** The button asks the query to start the load. The screen never calls the load directly. The query starts the load in the same process and reports progress.
- **FR9.3** Until the load succeeds, the same 30-second interval polls progress. The screen does not ask for trains.
- **FR9.4** When the load succeeds, the button goes away and the train views may ask.
- **FR9.5** A failed load stays on the empty screen with the button. The screen does not ask for trains.
- **FR9.6** A reload while a load is running shows that progress again. The load keeps running on the server.

## Non-functional requirements

- **NFR1** The service runs in Docker on this machine for one person. It has no accounts and no hosted or cloud deployment. There is no public or hosted API.
- **NFR2** Three containers run together: one holds the SQLite file, one runs the server, and one runs the screen. After the database container is restarted, and after its image is rebuilt, the same SQLite file `apps/local-timetable/db/timetable.sqlite` and the same journey rows are still there.
- **NFR3** The query never creates or rewrites the SQLite file. A test of the query leaves the file byte-for-byte unchanged. The MCA load is the only writer: it replaces the file from that day's MCA.
- **NFR4** The query has unit tests and a thin check of all three views. The MCA load has tests that use a synthetic MCA and a throwaway SQLite path; they must not unlink the host file in FR7.1. The screen has tests once the TypeScript UI exists. There is no line-coverage percentage gate. Tests do not open a production timetable file or a live broker.
- **NFR5** Local secret scanning, dependency checks, and a dynamic check of the local reader run for the TypeScript server and screen. This service does not add a continuous-integration pipeline.
- **NFR6** Kafka and Darwin secrets stay in the gitignored workspace-root `.env` and in Android `local.properties`. The server, the screen, and these requirements do not contain those values.

## Constraints

- The server lives in `apps/local-timetable/server` and the screen in `apps/local-timetable/fe`. Both are TypeScript. The server prefers a functional style where that is possible.
- `apps/local-timetable/db` is the database part. The SQLite file that survives a restart and a rebuild belongs to that part.
- The server does not import `schedule_poc` and does not live inside `server/poc/live-sched`. Live-sched is not the writer of this file and is not a data source for this service.
- The store is London Midland only (`toc = LM`).
- There is no CFA. This load is the day's full MCA.
- New work for this service is a short-lived branch squash-merged to `main`.
- No `dev`, `staging`, or `prod` environment is created for this service. No formatter or style linter is added in this pass.

## Assumptions

- Europe/London and the machine clock are the same clock, because this machine is kept on UK time.
- A forecast time is a better "where" signal than a booked time, and a departure is a better signal than an arrival, which is why FR1.5 tries them in that order.
- After an MCA-only load, forecast columns are empty, so FR3.1 problems appear only when a later write fills a forecast or sets `deactivated`. That later write is not this service.

## Out of scope

- Accounts, a hosted deployment, any cloud environment, and a public API.
- A GPS position between two calling points.
- Operators other than London Midland.
- Importing `schedule_poc`, using live-sched as the writer or as a data source, or moving live-sched into `apps/`.
- Creating or rewriting the SQLite file from the query.
- A CFA apply.
- A second future-intents page.
- The Android app.
- A continuous-integration pipeline, a formatter, or a style linter.
- Using `list_journeys()` as the reader.

## Open questions

None.

## Sources

- `intent-statement`
- `scope-document`
- `business-overview.md`
- `architecture.md`
- `code-structure.md`
- `team-practices.md`
- `docs/future-intents/local-timetable.md`
