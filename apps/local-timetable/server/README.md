# local-timetable server

Loads today's MCA into `apps/local-timetable/db/timetable.sqlite` (London Midland only), serves timetable queries, and optionally consumes Darwin Kafka updates into the same file.

## Run the three containers

From the repository root:

```bash
docker compose -f apps/local-timetable/compose.yaml up --build
```

Put today's MCA in `apps/local-timetable/mca`. The filename must contain today's date as `YYYYMMDD` (Europe/London), and there must be exactly one such file.

If you only have yesterday's file while developing, either rename or copy it so the name contains today's date, or run the load with a date override (the filename must still contain that `YYYYMMDD`):

```bash
MCA_CALENDAR_DATE=20261101 apps/local-timetable/scripts/mca-load.sh
```

The server process uses `TZ=Europe/London`. In the container it reads `/mca` (host `./mca`, read-only) and writes `/db/timetable.sqlite` (host `./db`).

During a replace, SQLite uses WAL mode and a single transaction from the first LM insert through `service_day` and `feed_meta` (one commit at the end). That avoids per-row fsync, which dominated load time on large MCAs.

## Kafka overlay

After a successful MCA load, a worker thread consumes the Darwin JSON topic when `KAFKA_*` env vars are set (same names as the workspace-root `.env.example`). Compose passes those through from the host environment.

Resume position is stored in `kafka_offsets` inside the SQLite file, not in the Kafka consumer group. A fresh load clears that table, so the consumer starts at the live end of the log. Forecasts, cancellations, and deactivated status land in the same file the HTTP queries read. Stop `live-sched consume` while this server is running — RDM allows only one consumer of the subscribed group id.

If Kafka settings are missing, the HTTP server still serves the booked timetable.

## Open the SQLite file

After a successful load, open the host file:

`apps/local-timetable/db/timetable.sqlite`

A SQL client on this machine can open that path. The database container holds the same bind-mount so the file survives a server restart.

## Tests

Use Node 22 (`nvm use 22`).

```bash
cd /home/william/code/personal/2025/uk-rail-tracker/apps/local-timetable/server
pnpm install
pnpm test
```
