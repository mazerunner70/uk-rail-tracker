# Requirements

The person wants one page they can build from later. The page is `docs/future-intents/local-timetable.md`. It names only this local timetable service as future work, and it describes that service. The intent statement and the scope document set that boundary. This pass does not build the service.

The description uses the live-sched proof of concept recorded in the code knowledge base (`business-overview`, `architecture`, `code-structure`). That proof of concept is a local Python command-line package and one SQLite file. It is not an HTTP server and it has no network view. The page says what the new server and UI add, and what they must keep from that store.

## Functional requirements

### FR1. The page

- **FR1.1** The page exists at `docs/future-intents/local-timetable.md`.
- **FR1.2** The future-intents part names only this local timetable service, as the future work the rest of the page describes. It does not list the Android milestones.
- **FR1.3** The service part describes a service the reader can run in Docker on their own machine, with a server, a database, and a UI.
- **FR1.4** The UI part describes three views: all trains in the store now; trains that deviate from the published timetable; a chosen pair of stations with the trains on that route, where they are, and any issues.
- **FR1.5** The page is detailed enough to build later, and it is not a step-by-step build guide.

### FR2. The database

- **FR2.1** The database component is the live-sched SQLite file. The seven tables are `journeys`, `calling_points`, `service_day`, `service_aliases`, `feed_meta`, `kafka_offsets`, and `applied_updates`.
- **FR2.2** A journey's stable id is `service_guid`, formed as `SVC-{originCrs}{destCrs}{runDateYYMMDD}{originStdHHMM}`.
- **FR2.3** The store holds London Midland (`toc = LM`) schedules. "All trains on the network" in this service means the LM journeys in that store, not every operator in Britain.
- **FR2.4** The server and any new package that reads this store do not import `schedule_poc`.

### FR3. How the store is filled

- **FR3.1** The published timetable comes from an MCA full load that persists LM rows, then from daily CFA zips that apply `N`, `R`, and `D`.
- **FR3.2** A CFA is accepted only when the HD Current File Reference (CIF columns 33–39) contains the next three-digit feed sequence. The zip basename is not the sequence.
- **FR3.3** Live updates come from the Darwin Push Port JSON overlay. `TS` writes forecast times and leaves booked public times in place. `deactivated` sets the journey status to `deactivated`. A `schedule` message is stored and does not rewrite booked times.

### FR4. The three views

- **FR4.1** The network view lists LM journeys that `service_day` says run today and are not cancelled.
- **FR4.2** A train deviates when a calling point's forecast time differs from its booked public time, or when the journey status is `deactivated`.
- **FR4.3** A station pair is two CRS codes. A train is on that route when its ordered calling points include both codes, the first before the second. Matching only the journey's overall origin and destination is not enough, because those two codes are the whole journey, not the chosen pair.
- **FR4.4** "Where" a train is means the latest calling point at or before now, and the next calling point. The store has no train GPS track. The page does not invent a position between those two stops.
- **FR4.5** Issues on a view are the deviations in FR4.2, plus a `service_day` row marked cancelled.

### FR5. What is new

- **FR5.1** The server is a new reader of the SQLite file. It answers the three views. The existing proof of concept writes that file through its command-line load, CFA update, and consume commands. It does not serve the views.
- **FR5.2** The UI is new. It draws the three views from what the server returns. The proofs of concept do not include this UI, and the page does not choose a UI toolkit.

## Non-functional requirements

- **NFR1** The service the page describes runs on the reader's own machine, for that one reader. The page does not describe accounts, a hosted service, or a cloud deployment.
- **NFR2** Kafka credentials stay in environment variables (`KAFKA_CONSUMER_KEY`, `KAFKA_CONSUMER_SECRET`). The page does not contain those values.

## Constraints

- This pass writes the page. It does not build or run the service.
- The page is not a step-by-step build guide.
- The live-sched package today has no Dockerfile. The page names the three Docker parts. It does not supply image recipes.
- `load` deletes the SQLite file when the path is not the in-memory database. A later build has to treat a full reload as a wipe of live overlays, offsets, and the feed sequence.

## Assumptions

None.

## Out of scope

- Building or running the service.
- A hosted or cloud deployment.
- A second file for the future-intents note.
- The Android milestone roadmap (compensation, notifications, release).
- A national timetable beyond the LM rows the proof of concept stores.
- A GPS track of a train between calling points.
- Importing `schedule_poc`.

## Open questions

None.
