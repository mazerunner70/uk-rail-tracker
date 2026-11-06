# Kafka updates — implementation

Live Darwin updates land in `apps/local-timetable/db/timetable.sqlite`. The screen keeps polling `GET /network` and already renders forecast times, `deactivated`, and `service_day.cancelled`. The server work is a writer on that file. After the first snapshot, the browser sends the revision it last applied and merges the journeys that changed.

Reference behaviour lives in `server/poc/live-sched` (`consumer.py`, `apply.py`, `kafka_classify.py`). Port the ideas. Do not import `live-sched` or `schedule_poc`.

## Outcome

After a successful MCA load, the server consumes the Darwin JSON topic for as long as the process runs. Each applied message commits before the next poll. The following client tick merges the journeys touched since the revision it stored.

A message that does not bind to an LM journey still advances the stored offset. Booked `public_arr` and `public_dep` never change.

## What stays as it is

- SQLite shape in `server/src/sqliteSchema.ts`. `kafka_offsets`, `applied_updates`, `service_aliases`, forecast columns, and `cancelled` are already there. MCA load leaves the overlay tables empty.
- Problem and cancel rules in `server/src/trainViews.ts`. Forecast still wins over the public time. `journeys.status = 'deactivated'` is still a problem. A today-row with `service_day.cancelled = 1` still leaves the network list and still counts as a pair cancellation. The delta query is specified under Client cursor.
- `/network-map` stays on the existing stale flag. Darwin does not move the diagram.

## Process shape

One Node process. The HTTP server stays on the main thread and keeps opening short-lived read-only connections. A worker thread owns the only read-write connection and the Kafka consumer.

`better-sqlite3` is synchronous. A consumer loop on the main thread would stall `/network` during a Darwin burst. The native addon is created inside the worker, not passed across the thread boundary.

If Kafka settings are missing, the worker stays idle and the HTTP server still serves the booked timetable.

Run one consumer of `KAFKA_GROUP_ID` at a time. Stop `live-sched consume` while this server is consuming. RDM rejects any other group id.

## Modules

All new files under `apps/local-timetable/server/src/`. Tests under `server/test/kafka-overlay/`.

| File | Role |
|------|------|
| `kafkaSettings.ts` | Read Kafka env. Return settings or a typed "not configured". |
| `pushPort.ts` | Pure decode and classify. No I/O. |
| `overlayPlan.ts` | Pure: a bound update item becomes a write description. |
| `overlayWriter.ts` | Apply one write description and the next offset in one transaction. |
| `kafkaConsumer.ts` | Assign/seek, poll, call the writer, skip offsets already stored. Inject the poll function in tests. |
| `kafkaWorker.ts` | Worker entry. Opens SQLite, runs the consumer, honours pause and resume. |
| `kafkaOverlay.ts` | Main-thread handle: start the worker, `pause()`, `resume()`. |

`package.json` gains `kafkajs`. It is pure JavaScript, so the server image does not need librdkafka. Add `test:kafka-overlay` beside the existing test scripts, and include that glob in `test`.

## Settings

Read the same names as the workspace-root `.env.example`. Do not write secrets into the repo or the image.

- `KAFKA_BOOTSTRAP_SERVERS`
- `KAFKA_CONSUMER_KEY`
- `KAFKA_CONSUMER_SECRET`
- `KAFKA_GROUP_ID` (use this exact value)
- `KAFKA_TOPIC_JSON`
- `KAFKA_SSL_CA_LOCATION` (optional)

Broker config: `SASL_SSL`, mechanism `PLAIN`, auto-commit off. XML and Avro topics are out of scope.

`compose.yaml` passes those variables into the `server` service from the environment or an env file. The file is not copied into the image.

## Decode

`decodePushPort(raw: Buffer | string)` returns the payload object or `null`.

- JSON object, or a JSON string.
- If the object has a string `bytes` field, parse that string and use the inner object when it is an object.
- Invalid JSON returns `null`. The consumer still advances the offset.

`updateItems(payload)` walks `uR` then `sR`. For each of `TS`, `schedule`, and `deactivated`, yield one item. A single object and a list both yield items. Any other key (`association`, `OW`, `trainAlert`, and the rest of the Darwin set) is ignored. Ignoring still advances the offset.

Identity fields on an item:

- `rid`: non-empty string, or absent.
- `uid` / `trainUid` / `train_uid`: first non-empty string.
- `ssd`: digits only. Eight digits become `YYMMDD` via `slice(2)`. Six digits stay. That matches `service_day.calendar_date`.

Forecast time on a location: from `arr` or `dep`, the first non-empty of `at`, `et`, `wet`. Keep the first four digits. `09:50` becomes `0950`.

`can` on a schedule item: `true`, `"true"`, and `"1"` cancel. `false` and `"false"` clear the cancel. Absent leaves `cancelled` unchanged.

## Bind

Look up an existing journey. Never insert one.

1. If `rid` is present, `SELECT service_guid FROM service_aliases WHERE alias_type = 'rid' AND alias_value = ?`.
2. Otherwise, if `uid` and `ssd` are present, `SELECT service_guid FROM service_day WHERE train_uid = ? AND calendar_date = ?`.
3. Otherwise, miss.

Do not scan `journeys` by UID. A miss is normal: the file only holds London Midland, and the topic carries every operator.

When bind hits and the item has a `rid`, upsert `service_aliases` in the same transaction as the overlay write.

## Writes

One SQLite transaction per Kafka message. It always upserts `kafka_offsets (topic, partition, next_offset, updated_at)` to `offset + 1`, including decode failures, ignored types, and bind misses.

When an item binds, the same transaction also does the following.

**TS.** For each location with a tiploc and at least one forecast time, `UPDATE calling_points SET forecast_arr` and `forecast_dep` where `service_guid` and `tiploc` match. A missing side keeps the current forecast value. Public times stay. Then insert `applied_updates` with `update_type = 'TS'`, the item as `payload_json`, `rid`, `source_offset`, and `applied_at` in UTC ISO form.

**schedule.** Insert `applied_updates` with `update_type = 'schedule'`. If `can` cancels, `UPDATE service_day SET cancelled = 1` for that `train_uid`, `calendar_date`, and `service_guid`. If `can` clears, set `cancelled = 0`. Do not rewrite calling points. The proof of concept only stores the audit row, so a Darwin cancel would never reach the pair view. This server must set `cancelled`.

**deactivated.** `UPDATE journeys SET status = 'deactivated'`. Insert `applied_updates` with `update_type = 'deactivated'`. Leave `service_day.cancelled` as it is. The network query already keeps the train on the diagram and marks `problem`.

Last write wins. Applying the same offset again is safe. `applied_updates` may gain another audit row. The screen never reads `payload_json`. It uses `id` as the cursor, then loads the current journey rows. `id` is a SQLite row number. `source_offset` on the same row is the Darwin message offset and is not sent to the browser. One message can insert several rows, a bind miss inserts none, and the same numeric offset exists on every partition.

Writer connection, opened inside the worker:

- `journal_mode = WAL`
- `synchronous = NORMAL`
- `foreign_keys = ON`
- `busy_timeout = 5000`

## Reader

In `openTimetableReader`, after a successful open, set `busy_timeout = 5000` on the read-only connection. A poll that arrives during the overlay commit then waits instead of returning `locked`. The existing 503 path and the held network and pair snapshots stay.

## Consumer loop

Use the configured group id so RDM accepts the connection. Resume position comes from SQLite, not from a Kafka commit. Do not commit offsets to the broker.

On join, for each assigned partition:

- Row in `kafka_offsets` for that topic: seek to `next_offset`.
- No row: seek to the end of the log.

Also drop any delivered message whose offset is below the stored next offset. That covers a seek that lands a moment late, and a redelivery after restart.

Then, for each message: decode, plan, write the transaction, then poll again. A thrown broker error is logged and the loop continues. A thrown SQLite error aborts that message without advancing the offset, so the next start retries it.

Empty `kafka_offsets` means "live tail". That is the state MCA load leaves behind.

## Load hand-off

`openReplacement` deletes `timetable.sqlite`, including offsets, aliases, and forecasts. The worker must not hold the file across that delete.

`kafkaOverlay.pause()` tells the worker to finish the current transaction, close the writer, and reply `paused`. `runLoad` awaits that pause before `locateDayMca`. The consumer is down for the whole load. Messages published during the load are not replayed.

After the load:

- `succeeded`: `resume()`. The new file has no offset rows, so the consumer seeks to the live end.
- Failed before `openReplacement` (MCA missing, header refused): `resume()` on the existing file. Saved offsets are still valid.
- Failed after `openReplacement` (`abortMissing` has deleted the file): stay paused. Queries already report the file missing. The next successful load calls `resume()`.

`createLoadController` takes an optional `{ pause, resume }` and `main.ts` wires the real overlay. Tests pass a fake and never open a broker.

`finalize` writes `feed_meta.loaded_at` as UTC ISO as well as `load_day` and `hd_sequence`. The client generation is those three values joined. A second load of the same MCA gets a new `loaded_at`, so the generation changes when `hd_sequence` does not. Reloading deletes `applied_updates`, and the ids start again at 1.

## Client cursor

The browser keeps the network snapshot it has already drawn. Each poll asks for journeys written after the last `applied_updates.id` it applied.

`GET /network` takes optional `since` and `generation` query parameters. `httpServer.ts` currently drops the query string; it must pass both through to `buildNetworkAnswer`. The body adds `generation`, `revision`, `full`, and `removed`. `revision` is `MAX(applied_updates.id)`, or `0` when that table is empty. `removed` is `{ serviceGuid, trainUid }[]`.

- No `since`, or a `generation` that does not match `feed_meta`: `full` is true, `trains` is every London Midland service running today, `removed` is empty. This is the scratch load.
- `since` is present and `generation` matches: `full` is false. Distinct `service_guid` values in `applied_updates` with `id > since` are read as they stand now. A guid whose today-row is cancelled goes in `removed` and is left out of `trains`. Any other guid is one current train row, including its stops. When nothing has been applied, `trains` and `removed` are empty and `revision` stays at `since`.

A schedule audit row that does not change `cancelled` still names that guid, so that one train is sent again.

The view model stores `generation` and `revision` next to `networkHeld`.

`tick` calls `GET /network` with the stored `since` and `generation` once it has both, and with neither on the first successful load. Then:

- `full` is true, or the returned generation differs from the stored one: replace `networkHeld.trains`, clear held cancellations, store the new generation and revision.
- Otherwise: replace each returned train by `serviceGuid`, insert a guid that was not held, delete every guid in `removed` from the running list, and keep those `{ serviceGuid, trainUid }` rows as cancellations. Store the new revision.
- A comms failure leaves the held trains, the cancellations, and the cursor where they are. The next tick repeats the same `since`.

`tick` stops calling `POST /pair`. The pair panel filters the held running trains with the same earlier-then-later calling-point rule as `pairMatchesCallingPoints`, and lists the held cancellation rows that match that rule. A cleared cancel arrives later as a normal train and is dropped from the cancellation list.

`reachedCrs`, `nextCrs`, and `scheduledTime` follow the clock. Each render recomputes them from the held stops and London now, using the same rules as `reachedAndNextCrs` and `scheduledHhmm` in `server/src/stopTime.ts`. The fe package copies those pure functions into `fe/src/stopTime.ts`. A poll is not required to move a train along the diagram.

## Worker messages

Main to worker: `{ type: "pause" }`, `{ type: "resume" }`.

Worker to main: `{ type: "paused" }`, `{ type: "resumed" }`, `{ type: "idle", reason: "not-configured" | "missing-db" }`.

`resume()` against a missing file replies `idle` / `missing-db` and does not throw into the HTTP server.

Spawn with `new Worker(new URL("./kafkaWorker.js", import.meta.url))` from the compiled `dist` output. Pass `sqlitePath` in `workerData`.

## Tests

Pure tests, no broker and no worker:

- Decode the shapes in `server/poc/live-sched/fixtures/kafka_payloads.jsonl`: wrapped `bytes`, bare `uR`, `TS` location object and list, `ssd` of `YYYY-MM-DD` to `YYMMDD`, forecast `09:50` to `0950`, unknown keys ignored, bad JSON returns null.
- `overlayPlan`: TS write names forecast columns only. Schedule with `can` sets cancelled. Schedule without `can` is audit-only. Deactivated sets status. A null guid produces no row change.

SQLite tests on a temp file built with the existing schema helpers:

- TS updates `forecast_arr` / `forecast_dep` for the tiploc and leaves `public_*`.
- Second TS for the same stop replaces the forecast.
- `can` sets `service_day.cancelled` to 1. The pair query then returns the cancelled row. Clearing `can` sets it back to 0.
- Deactivated sets `journeys.status`. The network row comes back with `problem: true` and still lists the train.
- Bind by `(train_uid, calendar_date)`. A later message with only `rid` binds through `service_aliases`.
- Unknown UID writes no journey and still stores `next_offset`.
- A failed statement inside the transaction leaves both the forecast and the offset unchanged.
- Restart: stored `next_offset` is what the consumer seeks to. No row means seek to the end. The seek choice is a pure function of `(saved offset | none, high watermark)`.

Load hand-off with a fake worker:

- `start()` does not call `openReplacement` until `pause()` resolves.
- Success calls `resume()`.
- Header refusal calls `resume()`.
- Write failure after the file is opened does not call `resume()`.

`busy_timeout` is asserted by reading the pragma on a reader opened through `openTimetableReader`.

Delta query, on a temp file:

- `since` returns only guids with `applied_updates.id` above that value, as they stand now.
- A cancelled guid is in `removed` and absent from `trains`.
- A generation that does not match `feed_meta` returns `full: true` and the whole day.
- `since` equal to the current max id returns empty `trains` and empty `removed`.

Client merge, with a fake API:

- A delta replaces one held train and leaves the others.
- A guid in `removed` leaves the running list and stays on the pair panel as a cancellation.
- A new generation replaces the snapshot and clears the cursor's previous cancellations.
- A comms failure keeps the held trains and the stored revision.

## Files to edit

- `server/src/main.ts` — construct the overlay and pass pause/resume into the load controller. Start the worker after `listen`.
- `server/src/start.ts` — await pause before work; resume on the branches above.
- `server/src/sqliteReplacer.ts` — write `feed_meta.loaded_at` in `finalize`.
- `server/src/httpServer.ts` — read `since` and `generation` and pass them to `buildNetworkAnswer`.
- `server/src/trainViews.ts` — full snapshot or delta, as under Client cursor.
- `server/src/sqliteReader.ts` — `busy_timeout`.
- `fe/src/types.ts`, `fe/src/apiClient.ts`, `fe/src/controller.ts` — store the cursor, merge, derive the pair panel from the held snapshot.
- `fe/src/stopTime.ts` — copy of the reached/next and scheduled-time rules.
- `server/package.json` — `kafkajs` and the test script.
- `compose.yaml` — pass Kafka env into `server`.
- `server/README.md` — one short section: consumer starts after a successful load, starts at the live end, and a reload clears overlays.

## Out of scope

- Pushing updates to the browser. The browser keeps polling.
- CFA zips.
- Replaying `kafka_offsets` and forecasts across an MCA reload. A reload starts live.
- XML and Avro topics.
- A second SQLite file for the overlay.
