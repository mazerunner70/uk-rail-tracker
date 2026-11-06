# local-timetable server (MCA load)

Replaces `apps/local-timetable/db/timetable.sqlite` from that day's MCA. London Midland only. No CFA. This unit exposes in-process `start` and `progress` for the query; it does not bind a port.

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

The `screen` service is a keep-alive stub in this unit. The UI lands later.

## Open the SQLite file

After a successful load, open the host file:

`apps/local-timetable/db/timetable.sqlite`

A SQL client on this machine can open that path. The database container holds the same bind-mount so the file survives a server restart.

## Tests

```bash
cd /home/william/code/personal/2025/uk-rail-tracker/apps/local-timetable/server
npm install
node --import tsx --test test/mca-load/*.test.ts
```
