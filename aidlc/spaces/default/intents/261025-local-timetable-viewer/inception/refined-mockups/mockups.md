# Mockups

Classic scope has no ideation wireframes and no user-flow. These pages are drawn from stories, requirements, and team-practices, plus the two pictures the reader chose: `docs/local-timetable/rail controller dashboard.jpeg` for the network page and `docs/local-timetable/station-pair.png` for the station-pair page.

The pictures set the panels and the dark control-room look. The lists inside those panels come from the approved stories. A panel the SQLite file cannot fill stays on the page and says it has no data.

## Data layer

Each page has its own data layer under the UI. The screen draws trains, counts, and problem rows from that layer. The screen changes only when a value in that layer changes.

An interval and a station edit only start an attempt to load. The attempt itself does not move the screen. There is no loading line beside data the page is already holding. A failed attempt does not add, remove, or edit any held value, and the screen stays as it is.

A success writes the layer only when it answers the latest attempt for that page. An older success is discarded. A value that is the same stays. A value that differs updates. A row that the successful answer no longer includes leaves the layer. A new row is added.

Before a page has any successful answer, the layer is empty. The page paints at once. That first paint says "Loading". If that first attempt fails, for any reason, the page says "No data can be retrieved". It does not say "No trains", and it does not show counts of zero.

These sentences are separate:

| What the reader is looking at | Text |
|---|---|
| A successful list with no rows | No trains |
| A page with no held data whose attempt failed | No data can be retrieved |
| Disruptions or incident logs, and the held list has no problem row | No problems |
| On time, Delayed, or Cancelled, and that subset is empty | Nothing in this filter |
| Search text that matches nothing in the held list | Nothing matches |

Leaving a page does not clear its layer. Coming back shows the layer you left. Reloading the browser starts a new page. Both layers start empty, and the interval starts at 30 seconds. A reload is not a failed retrieval.

A stop time earlier than the origin row's stop time is the next calendar day, because a schedule can run for a couple of hours after midnight. Any other stop time is on the service day. The reached stop is then the latest calling point at or before now.

## Network page

This is the all-trains-now view. It keeps every panel from the dashboard picture.

```
London Midland          JOURNEY CONTROL CENTRE
Earlier station [    ]   [swap]   Later station [    ]
clock (Europe/London)   date (Europe/London)
search (filters the held list)    route filter: no data
interval (seconds)                 Open station pair

NETWORK OVERVIEW
This panel has no data.

LIVE SCHEDULES
"Loading" only before the first success
"No data can be retrieved" when that first attempt fails
table from the data layer, or "No trains"

DISRUPTIONS
problem rows from the data layer, or "No problems"

DISRUPTION TREND
This panel has no data.

INCIDENT LOGS
the same problem rows as Disruptions

SYSTEM ALERTS
"No data can be retrieved" when the page has no held data and the attempt failed
"This panel has no data" once the page is holding data

footer: Active | On time | Delayed | Cancelled (no data)
```

The two station boxes sit at the top of this page. They are labelled Earlier station and Later station. The reader types CRS codes. The swap control between them exchanges the two codes. Open station pair uses the codes now in the boxes and opens the station-pair page. That page opens and paints at once, including when the codes are invalid. Invalid codes do not produce trains.

The clock and the date are the server's Europe/London clock from requirements FR4, not the sample date printed in the picture.

The live-schedules table includes every London Midland journey in the layer. A row shows `service_guid`, `origin_crs`, `dest_crs`, the reached stop's `crs`, the next stop's `crs`, and whether the journey has a problem. The picture's headcode column and platform column stay, and each of those cells says it has no data. The picture's single scheduled-time column stays, and each of those cells says it has no data. Status is the word `problem` or `on time`, from the story rule. A today cancellation is absent from this list.

Search filters the held list by the text the reader types. A match is a `service_guid` or a `crs` in that row. Search does not ask the server and does not change the layer. When the held list has rows and none match, the list says "Nothing matches". The route filter has no stored route catalogue, so that control says it has no data.

Disruptions and incident logs list the journeys in the layer whose problem rule matches. They do not invent a signal failure, a track fault, or any other incident the file does not store. When the held list has no such journey, those panels say "No problems".

System alerts say "No data can be retrieved" when this page has no held data and the attempt failed. Once the page is holding data, a later failed attempt leaves the alerts as they are. After a success they say they have no data.

Footer counts are taken from the layer after a success. Active is the number of running journeys. Delayed is how many of those have a problem. On time is the number without a problem, and that count as a percent of Active. When Active is 0 the percent is not shown. Cancelled says it has no data, because a cancellation-only row is not on this list. Before the first success the footer says it has no data.

## Station-pair page

This is the first slice. It keeps every panel from the station-pair picture.

```
London Midland          WEST COAST SOUTH CONTROL
feed status: no data    berth status: no data
clock (Europe/London)   date (Europe/London)
Network Overview  /  Station pair
line code: no data
tactical strategy: no data

Earlier station [    ]   [swap]   Later station [    ]
shortcut chips: no data
interval (seconds)

KPI tiles: running | problems | cancelled | PPM no data | average delay no data | route miles no data | journey time no data

BERTH SCHEMATIC
This panel has no data.

Filter by headcode: no data    Fleet: no data
filters: All | On time | Delayed | Cancelled
"Loading" only before the first success
"No data can be retrieved" when that first attempt fails
list from the data layer, or "No trains"
```

The same two station boxes are on this page, with the same swap. They show the codes named on the network page. A change here, including a swap, starts an attempt to load with the codes now in the boxes. The attempt does not move the screen. The list changes only when that latest attempt succeeds and a held value differs. Codes are compared to `calling_points.crs` ignoring case. The page still opens and paints when the codes are invalid. With no held data, a failed attempt says "No data can be retrieved".

Filter by headcode is on the page and says it has no data. The timetable file has no headcode. Fleet is on the page and says it has no data. The timetable file has no fleet or unit. Neither control filters the list.

The shortcut chips, the line code, and the tactical-strategy banner say they have no data. The feed-status and berth-status lines say they have no data. The clock is labelled Europe/London.

Running, problems, and cancelled are counts from the pair layer after a success. PPM, average delay, route miles, and journey time say they have no data. Before the first success those count tiles say they have no data.

The berth schematic says it has no data. The screen does not draw a train between stops.

The list includes every journey the pair rules include, and each cancellation row the pair rules include. A running row shows `service_guid`, the two station codes, the reached stop's `crs`, the next stop's `crs`, and whether it has an issue. A cancellation row shows `train_uid` and the word `cancelled`, with reached and next absent. It is not shown as a running train.

The four filters show a subset of the held list. All shows every row. On time shows running rows with no issue. Delayed shows running rows with an issue. Cancelled shows the cancellation rows. When On time, Delayed, or Cancelled leaves nothing visible, the list says "Nothing in this filter". All, on a successful empty list, says "No trains". The filter does not ask the server and does not change the layer. A row still shows the word `issue` or `cancelled`, so the filter is not the only place that meaning appears.

## Shared behaviour

The interval is one control. It shows a whole number of seconds and starts at 30. A positive whole number replaces the wait already in progress, measured from the moment of the change. A blank, a fraction, or a non-positive value is refused and the interval already in use stays. Changing pages does not reset it. Reloading the page does. When the interval elapses, the open page attempts a load. That attempt does not move the screen.

Open station pair, at the top of the network page, opens the station-pair page with the two codes now named. The Network Overview crumb on the pair page opens the network page. Neither control clears a data layer.

The screen never writes the SQLite file. The pages are the desktop browser on this machine, at the width of the two pictures. There is no phone layout and no tablet layout.
