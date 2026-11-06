# Code summary — screen

**Unit:** screen  
**Package:** `apps/local-timetable/fe`

## What shipped

- TypeScript UI bundled to static `dist/` (esbuild) and served by nginx in the screen container.
- REST client for `POST/GET /load`, `GET /network`, `POST /pair` with gzip accept and comms/train failure handling.
- Empty-screen load flow (FR9), 30s default polling (FR5), interval validation, held answers and `comms is lost` status (FR5.4–FR5.7).
- Network and station-pair views with `data-testid` hooks for tests.
- Compose: `screen` builds `./fe`, publishes `8081:80`, proxies API paths to `server:8080`.

## Tests

```bash
cd /home/william/code/personal/2025/uk-rail-tracker/apps/local-timetable/fe && PATH=/home/william/.nvm/versions/node/v22.18.0/bin:$PATH node --import ./test/register-hooks.ts --import tsx --test test/*.test.ts
```

**Last run:** 9 pass, 0 fail (Node 22.18.0).

Server suites unchanged green: 69 pass (mca-load + timetable-query).

## Notes

- Browser uses same-origin API via nginx reverse proxy; host dev can open `http://localhost:8081` with server on `8080`.
- Pair page asks only when both CRS fields are three letters and load has succeeded.
