---
id: KDD-001
title: Live-track chart stations from network overview selection
type: Architecture
status: historical-station-tap / current-train-tracking
app: local-timetable
superseded_for_station_tap_window_by: docs/kdd/003-journey-window-station-chart.md
source_of_truth:
  - apps/local-timetable/fe/src/main.ts
  - apps/local-timetable/fe/src/controller.ts
  - apps/local-timetable/fe/src/routeStrip.ts
  - apps/local-timetable/fe/src/routeLiveModel.ts
  - apps/local-timetable/server/src/networkSchematic.ts
---

# KDD-001 — How chart stations are chosen (station tap on Network Overview)

> **Station-tap window rule:** implemented by [KDD-003](003-journey-window-station-chart.md) (merge Journeys through S → ±5 + side branches). This doc remains the **historical Line/JunctionSegment** description and still covers **train-tracking** windows.

When a user selects a station on the **Network overview** map, the app opens **Route & Live Track** and draws a horizontal station chart (the route strip). This note records **which stations appear on that chart** and **how that set is computed**.

## Glossary

| Term | Meaning in this doc |
|------|---------------------|
| **CRS** | Three-letter station code (e.g. `MKC`). |
| **route strip / chart** | The horizontal live-track station diagram on Route & Live Track. |
| **spine** | Chart backbone sequence through the tapped station. When line-sourced, this is a **JunctionSegment** / **Line** — see [KDD-002](002-terminology-map.md). |
| **window** | Contiguous slice of the spine shown on the chart. |
| **WINDOW_RADIUS** | Max stations either side of the centre on the spine (**5** → up to 11 stations). |
| **schematic line** | Preferred spine source (`diagram.lines`); project name **Line** / **JunctionSegment** — see [KDD-002](002-terminology-map.md). |
| **corridor** | Layout paint edge in `diagram.corridors` — not topology (KDD-002). |
| **junction** | Station with open neighbours ≥ 3 (onward choice) — see [KDD-002](002-terminology-map.md). |
| **terminal** | Station with open neighbours = 1 (end of line). |
| **through** | Station with open neighbours = 2. |
| **clamp** | Cutting the ±`WINDOW_RADIUS` window short because the spine ends before that many stations. |

## Entry path

1. Pointer on the network map hits a station mark (`schedulePick` → `{ kind: "station", crs }`).
2. The controller filters the network list to that station (`setScheduleFocus`) and opens route tracking (`openRouteFromStation(crs)`).
3. Tracking mode is **station-centred**, not train-centred:

```text
routeTrack = { kind: "station", crs, lastWindowKey: "at:<CRS>" }
page       = "route"
```

4. `rebuildRouteLive` resolves a **window** of CRS codes and builds `routeLive` (strip layout + aggregates) from that window.

There is **no selected train** on this path (`selectedServiceGuid` is `null`). The chart centres on the tapped station, not on a service.

## What “the chart” shows

The live-track chart is the strip whose stations are exactly:

```text
routeLive.window  // ordered CRS list
```

`stripLayout(window, …)` places those codes left-to-right. Only trains whose live place falls on that spine are marked on the strip.

## Station set algorithm

For `routeTrack.kind === "station"`:

```text
window = windowAroundStation(diagram, crs)
       = slice of spine centred on crs, ± WINDOW_RADIUS
```

May **clamp** at either end of the spine (see **WINDOW_RADIUS**, **junction** / **terminal**, **clamp** in the glossary).

### Step A — Choose the spine (`corridorThroughStation`)

The spine is an ordered CRS path through the tapped station. Preference order:

| Priority | Source | Rule |
|----------|--------|------|
| 1 | `diagram.lines[]` | Longest schematic line whose `stations` list includes the CRS |
| 2 | `diagram.corridors[]` | Longest `from → via… → to` sequence that includes the CRS, if length **> 2** |
| 3 | Hop walk fallback | Build undirected adjacency from corridors; from the tapped CRS take up to two neighbours (sorted CRS ascending); walk each degree-2 chain away from the station for at most `WINDOW_RADIUS` hops; concatenate `before + crs + after` |

Schematic `lines` are produced on the server (`linePathsFromJourneys`). Older diagrams may omit `lines`; then corridors or the hop walk apply.

**Not used for station tracking:** global rank/BFS sort of the whole network, or a train origin–destination spine. Those apply only when tracking a **train**.

### Junctions and clamping

Schematic lines are **(Junction|Terminal) → Through… → (Junction|Terminal)** walks over the journey graph (`walksBetweenJunctions` in `networkSchematic.ts`) — project term **JunctionSegment** / **Line** ([KDD-002](002-terminology-map.md)). Code still uses `isJunction` for neighbours ≠ 2 (Junction ∪ Terminal). For a **line-sourced spine** (the usual case when `diagram.lines` exist):

1. **Endpoints are always Junction or Terminal.** The spine ends there — it does not continue through them onto another walk.
2. **Through Stations between the ends are on the spine.** Neighbours = 2; the walk includes them. So the spine describes stations beyond the tapped CRS *within* the same JunctionSegment.
3. **Stations past an endpoint Junction/Terminal are not on that spine.** They belong to a different schematic line. Lines are never stitched across those endpoints into one longer spine.

That is why Junction/Terminal endpoints **clamp** the chart:

1. Station tracking picks one schematic line (the longest that contains the tapped CRS) as the spine.
2. `windowAroundStation` asks for ±`WINDOW_RADIUS` on that spine only.
3. If the tapped station sits near a Junction or Terminal end of that line, one side has fewer than five neighbours — the window clamps there. Stations beyond are on another line and are not pulled in.

Example: `APS` often sits on a line that ends at `WFJ` (a **Junction**, neighbours ≥ 3). Selecting `APS` can show five stations toward Cheddington and only Kings Langley + Watford Junction toward London — not Bushey / Euston — because those lie on the separate `WFJ → … → EUS` walk.

Corridor / hop-walk fallbacks apply only when no usable schematic line contains the CRS (e.g. older diagrams without `lines`). Hop walk also stops at branch points, or earlier if `WINDOW_RADIUS` runs out mid-chain.

### Step B — Window the spine (`windowAroundStation`)

Find the CRS index on the spine and take inclusive indices `[index − WINDOW_RADIUS, index + WINDOW_RADIUS]`, clamped to spine bounds (the clamp above).

If the CRS is somehow missing from the spine, the window collapses to `[crs]` alone.

### Step C — Render

`buildRouteLiveModel` keeps that window, lays out the strip, and aggregates trains **at** / **approaching** each window station.

## End-to-end picture

```text
Network overview station tap
        │
        ▼
openRouteFromStation(crs)
  routeTrack.kind = "station"
        │
        ▼
resolveRouteWindow
  windowAroundStation(diagram, crs)
        │
        ├─ corridorThroughStation  → spine (longest line; ends at Junction/Terminal)
        └─ ±WINDOW_RADIUS slice    → window (clamps at spine ends)
        │
        ▼
buildRouteLiveModel → stripLayout(window)
  Route & Live Track chart stations = window
```

## Worked examples

**Symmetric window (hop-fallback spine).** Selecting `G` on a long degree-2 chain yields:

```text
B C D E F G H I J K L
```

(eleven codes: ±5 around `G`).

**Clamp at a Junction.** Spine `CED → TRI → BKM → XOE → HML → APS → KGL → WFJ` (ends at Junction `WFJ`). Selecting `APS` yields the full spine: five stations before `APS`, only two after — `WINDOW_RADIUS` cannot extend past `WFJ` onto the next line.

## Out of scope for this KDD

- **Train tracking** (`routeTrack.kind === "train"`): window follows the train’s place on an OD spine (or corridor-through-place fallback), not a fixed station centre.
- Which trains appear as markers (visibility / severity filters) — only the **station list** is defined here.
- Network-overview map layout itself (geo / octilinear painting).

## Invariants

- Station-tap tracking always uses `windowKey = at:<CRS>`.
- Chart station order is spine order, not alphabetical or map rank.
- The tapped CRS is always included when resolution succeeds; neighbours depend on diagram topology and the ±`WINDOW_RADIUS` window.
- A schematic-line spine **includes** its bounding Junction/Terminal ends and the Through Stations between them; it does **not** continue through those ends onto another walk. Asymmetric windows near Junctions/Terminals are expected clamps, not missing radius.
