#!/usr/bin/env bash
# Dump everything we know about a service_guid from timetable.sqlite.
# Usage:
#   ./scripts/show-service.sh C17843_261115_O
#   ./scripts/show-service.sh                 # defaults to C17843_261115_O
#   DB=/path/to/timetable.sqlite ./scripts/show-service.sh C17843_261115_O

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DB="${DB:-$ROOT/db/timetable.sqlite}"
ID="${1:-C17843_261115_O}"

if [[ ! -f "$DB" ]]; then
  echo "DB not found: $DB" >&2
  exit 1
fi

run() {
  local title="$1"
  shift
  echo
  echo "=== $title ==="
  sqlite3 -header -column "$DB" "$@"
}

echo "DB: $DB"
echo "service_guid: $ID"

# Latest arr.at / dep.at / pass.at → current place; next public stop from calling_points.
run "where now" "
WITH locs AS (
  SELECT
    au.id AS update_id,
    au.applied_at,
    json_extract(j.value, '\$.tpl') AS tiploc,
    json_extract(j.value, '\$.arr.at') AS arr_at,
    json_extract(j.value, '\$.dep.at') AS dep_at,
    json_extract(j.value, '\$.pass.at') AS pass_at,
    COALESCE(
      json_extract(j.value, '\$.plat'),
      json_extract(j.value, '\$.plat.\"\"'),
      json_extract(j.value, '\$.plat.#text')
    ) AS platform
  FROM applied_updates au,
       json_each(
         CASE json_type(json_extract(au.payload_json, '\$.Location'))
           WHEN 'array' THEN json_extract(au.payload_json, '\$.Location')
           WHEN 'object' THEN json_array(json_extract(au.payload_json, '\$.Location'))
           ELSE json_array()
         END
       ) AS j
  WHERE au.service_guid = '$ID'
    AND au.update_type = 'TS'
),
events AS (
  SELECT update_id, applied_at, tiploc, 'arrived' AS event, arr_at AS actual, platform
  FROM locs WHERE arr_at IS NOT NULL
  UNION ALL
  SELECT update_id, applied_at, tiploc, 'departed', dep_at, platform
  FROM locs WHERE dep_at IS NOT NULL
  UNION ALL
  SELECT update_id, applied_at, tiploc, 'passed', pass_at, platform
  FROM locs WHERE pass_at IS NOT NULL
),
latest AS (
  SELECT *
  FROM events
  ORDER BY update_id DESC,
    CASE event WHEN 'departed' THEN 3 WHEN 'arrived' THEN 2 WHEN 'passed' THEN 1 END DESC
  LIMIT 1
),
at_cp AS (
  SELECT cp.seq AS last_seq
  FROM calling_points cp
  JOIN latest ON cp.tiploc = latest.tiploc
  WHERE cp.service_guid = '$ID'
  ORDER BY cp.seq
  LIMIT 1
),
next_stop AS (
  SELECT cp.seq, cp.tiploc, cp.crs, cp.public_arr, cp.public_dep, cp.forecast_arr, cp.forecast_dep
  FROM calling_points cp
  JOIN at_cp ON cp.seq > at_cp.last_seq
  WHERE cp.service_guid = '$ID'
    AND cp.crs IS NOT NULL
    AND cp.crs != ''
    AND (
      (cp.public_arr IS NOT NULL AND cp.public_arr != '' AND cp.public_arr != '0000')
      OR (cp.public_dep IS NOT NULL AND cp.public_dep != '' AND cp.public_dep != '0000')
      OR cp.activity = 'destination'
    )
  ORDER BY cp.seq
  LIMIT 1
)
SELECT
  CASE latest.event
    WHEN 'arrived' THEN 'at ' || latest.tiploc
    WHEN 'departed' THEN 'left ' || latest.tiploc
    WHEN 'passed' THEN 'passed ' || latest.tiploc
  END AS position,
  latest.event,
  latest.actual AS actual_time,
  latest.platform,
  latest.applied_at AS reported_at,
  latest.update_id,
  next_stop.tiploc AS next_tiploc,
  next_stop.crs AS next_crs,
  COALESCE(next_stop.forecast_arr, next_stop.public_arr) AS next_arr,
  COALESCE(next_stop.forecast_dep, next_stop.public_dep) AS next_dep
FROM latest
LEFT JOIN next_stop;
"

run "journey" "
SELECT *
FROM journeys
WHERE service_guid = '$ID';
"

run "aliases (Darwin RID etc.)" "
SELECT *
FROM service_aliases
WHERE service_guid = '$ID';
"

run "service_day for this UID (highlights which calendar days use this guid)" "
SELECT sd.*
FROM service_day sd
WHERE sd.train_uid = (SELECT train_uid FROM journeys WHERE service_guid = '$ID')
ORDER BY sd.calendar_date;
"

run "sibling journeys for same UID (permanent vs overlays)" "
SELECT service_guid, run_date, date_runs_to, days_run, origin_crs, dest_crs,
       origin_std, toc, stp_indicator, status
FROM journeys
WHERE train_uid = (SELECT train_uid FROM journeys WHERE service_guid = '$ID')
ORDER BY run_date, stp_indicator;
"

run "calling points (booked vs forecast)" "
SELECT seq, tiploc, crs, public_arr, public_dep, forecast_arr, forecast_dep, activity
FROM calling_points
WHERE service_guid = '$ID'
ORDER BY seq;
"

run "forecast deltas vs public (HHmm; null when public is 0000/blank)" "
SELECT
  seq,
  tiploc,
  crs,
  public_arr,
  forecast_arr,
  CASE
    WHEN public_arr IS NULL OR public_arr = '' OR public_arr = '0000'
      OR forecast_arr IS NULL OR forecast_arr = '' THEN NULL
    ELSE
      ((CAST(substr(forecast_arr,1,2) AS INT)*60 + CAST(substr(forecast_arr,3,2) AS INT))
       - (CAST(substr(public_arr,1,2) AS INT)*60 + CAST(substr(public_arr,3,2) AS INT)))
  END AS arr_delta_min,
  public_dep,
  forecast_dep,
  CASE
    WHEN public_dep IS NULL OR public_dep = '' OR public_dep = '0000'
      OR forecast_dep IS NULL OR forecast_dep = '' THEN NULL
    ELSE
      ((CAST(substr(forecast_dep,1,2) AS INT)*60 + CAST(substr(forecast_dep,3,2) AS INT))
       - (CAST(substr(public_dep,1,2) AS INT)*60 + CAST(substr(public_dep,3,2) AS INT)))
  END AS dep_delta_min
FROM calling_points
WHERE service_guid = '$ID'
ORDER BY seq;
"

run "update summary" "
SELECT
  update_type,
  COUNT(*) AS n,
  MIN(applied_at) AS first_at,
  MAX(applied_at) AS last_at
FROM applied_updates
WHERE service_guid = '$ID'
GROUP BY update_type;
"

run "event tag counts" "
SELECT json_each.value AS tag, COUNT(*) AS n
FROM applied_updates, json_each(applied_updates.event_tags)
WHERE service_guid = '$ID'
GROUP BY tag
ORDER BY n DESC;
"

run "latest schedule payload" "
SELECT payload_json
FROM applied_updates
WHERE service_guid = '$ID' AND update_type = 'schedule'
ORDER BY id DESC
LIMIT 1;
"

# Station movement actuals come from Darwin Push Port TS updates:
#   Location[].arr.at  -> event_tag "arrived"          (called at a stop)
#   Location[].dep.at  -> event_tag "departed"         (left a stop)
#   Location[].pass.at -> event_tag "passed_location"  (non-stop pass)
# Forecasts use et/wet (call_forecast / pass_forecast) and do NOT mean arrived/left.
# Schedule updates carry the planned path; they do not report movements.

run "most recent station events (arr.at / dep.at / pass.at)" "
WITH locs AS (
  SELECT
    au.id AS update_id,
    au.applied_at,
    json_extract(j.value, '\$.tpl') AS tiploc,
    json_extract(j.value, '\$.arr.at') AS arr_at,
    json_extract(j.value, '\$.arr.atClass') AS arr_class,
    json_extract(j.value, '\$.arr.src') AS arr_src,
    json_extract(j.value, '\$.dep.at') AS dep_at,
    json_extract(j.value, '\$.dep.atClass') AS dep_class,
    json_extract(j.value, '\$.dep.src') AS dep_src,
    json_extract(j.value, '\$.pass.at') AS pass_at,
    json_extract(j.value, '\$.pass.atClass') AS pass_class,
    json_extract(j.value, '\$.pass.src') AS pass_src,
    COALESCE(
      json_extract(j.value, '\$.plat'),
      json_extract(j.value, '\$.plat.\"\"'),
      json_extract(j.value, '\$.plat.#text')
    ) AS platform
  FROM applied_updates au,
       json_each(
         CASE json_type(json_extract(au.payload_json, '\$.Location'))
           WHEN 'array' THEN json_extract(au.payload_json, '\$.Location')
           WHEN 'object' THEN json_array(json_extract(au.payload_json, '\$.Location'))
           ELSE json_array()
         END
       ) AS j
  WHERE au.service_guid = '$ID'
    AND au.update_type = 'TS'
),
events AS (
  SELECT update_id, applied_at, tiploc, 'arrived' AS event, arr_at AS actual,
         arr_class AS at_class, arr_src AS src, platform
  FROM locs WHERE arr_at IS NOT NULL
  UNION ALL
  SELECT update_id, applied_at, tiploc, 'departed', dep_at, dep_class, dep_src, platform
  FROM locs WHERE dep_at IS NOT NULL
  UNION ALL
  SELECT update_id, applied_at, tiploc, 'passed', pass_at, pass_class, pass_src, platform
  FROM locs WHERE pass_at IS NOT NULL
)
SELECT update_id, applied_at, tiploc, event, actual, at_class, src, platform
FROM events
ORDER BY update_id DESC, CASE event
  WHEN 'arrived' THEN 1
  WHEN 'departed' THEN 2
  WHEN 'passed' THEN 3
END
LIMIT 30;
"

run "latest actual per tiploc (most recent arr/dep/pass reported)" "
WITH locs AS (
  SELECT
    au.id AS update_id,
    au.applied_at,
    json_extract(j.value, '\$.tpl') AS tiploc,
    json_extract(j.value, '\$.arr.at') AS arr_at,
    json_extract(j.value, '\$.dep.at') AS dep_at,
    json_extract(j.value, '\$.pass.at') AS pass_at,
    COALESCE(
      json_extract(j.value, '\$.plat'),
      json_extract(j.value, '\$.plat.\"\"'),
      json_extract(j.value, '\$.plat.#text')
    ) AS platform
  FROM applied_updates au,
       json_each(
         CASE json_type(json_extract(au.payload_json, '\$.Location'))
           WHEN 'array' THEN json_extract(au.payload_json, '\$.Location')
           WHEN 'object' THEN json_array(json_extract(au.payload_json, '\$.Location'))
           ELSE json_array()
         END
       ) AS j
  WHERE au.service_guid = '$ID'
    AND au.update_type = 'TS'
),
events AS (
  SELECT update_id, applied_at, tiploc, 'arrived' AS event, arr_at AS actual, platform FROM locs WHERE arr_at IS NOT NULL
  UNION ALL
  SELECT update_id, applied_at, tiploc, 'departed', dep_at, platform FROM locs WHERE dep_at IS NOT NULL
  UNION ALL
  SELECT update_id, applied_at, tiploc, 'passed', pass_at, platform FROM locs WHERE pass_at IS NOT NULL
),
ranked AS (
  SELECT *,
         ROW_NUMBER() OVER (
           PARTITION BY tiploc, event
           ORDER BY update_id DESC
         ) AS rn
  FROM events
)
SELECT tiploc, event, actual, platform, applied_at, update_id
FROM ranked
WHERE rn = 1
ORDER BY update_id DESC, CASE event
  WHEN 'arrived' THEN 1
  WHEN 'departed' THEN 2
  WHEN 'passed' THEN 3
END;
"

run "movement / delay timeline (raw TS with movement tags)" "
SELECT id, applied_at, event_tags, payload_json
FROM applied_updates
WHERE service_guid = '$ID'
  AND (
    event_tags LIKE '%departed%'
    OR event_tags LIKE '%arrived%'
    OR event_tags LIKE '%passed_location%'
    OR event_tags LIKE '%delayed%'
    OR event_tags LIKE '%cancelled%'
  )
ORDER BY id;
"

run "last 10 updates (any type)" "
SELECT id, update_type, applied_at, event_tags, payload_json
FROM applied_updates
WHERE service_guid = '$ID'
ORDER BY id DESC
LIMIT 10;
"

echo
echo "Done."
