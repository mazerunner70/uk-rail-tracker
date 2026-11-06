# Code summary — timetable-query

**Unit:** timetable-query  
**Package:** `apps/local-timetable/server`

## What shipped

- Read-only SQLite access with FR6.3 failure reasons (`sqliteReader.ts`).
- Network and station-pair answers from requirements FR1–FR3 (`trainViews.ts`, `stopTime.ts`, `londonClock.ts`).
- REST on port 8080: `POST/GET /load`, `GET /network`, `POST /pair` (`httpServer.ts`, `main.ts`).
- In-process load delegation via existing `start` / `progress` from mca-load.
- Optional gzip for large JSON bodies when `Accept-Encoding: gzip`.
- Compose publishes `8080:8080` on the server service.

## Tests

Command:

```bash
cd /home/william/code/personal/2025/uk-rail-tracker/apps/local-timetable/server && PATH=/home/william/.nvm/versions/node/v22.18.0/bin:$PATH node --import tsx --test test/timetable-query/*.test.ts
```

Combined with mca-load:

```bash
PATH=/home/william/.nvm/versions/node/v22.18.0/bin:$PATH node --import tsx --test test/mca-load/*.test.ts test/timetable-query/*.test.ts
```

**Last run:** 69 tests, 0 fail (Node 22.18.0).

## Notes

- Train asks do not write the SQLite file (covered by a byte-compare test).
- Pair cancellation rows use `service_day.cancelled = 1` with matching calling-point order.
- Screen unit (`apps/local-timetable/fe`) is not part of this pass.
