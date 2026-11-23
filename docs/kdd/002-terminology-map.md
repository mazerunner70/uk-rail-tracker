---
id: KDD-002
title: Project terminology map
type: Concept
status: current
app: local-timetable
canon: project-wide terminology
related:
  - docs/kdd/001-live-track-station-chart-window.md
  - docs/kdd/003-journey-window-station-chart.md
---

# KDD-002 — Project terminology map (graphical + canon)

Project-wide names for places and paths. **Diagrams are normative** for the build-up; code and older docs migrate toward these terms over time.

## Glossary

| Term | Quick read |
|------|------------|
| **Station** | Place; primary id is a **CRS**. |
| **Adjacent / neighbouring** | Two Stations on the **same physical track**, evidenced as consecutive stops in **at least one published Journey**. |
| **Through Station** | Open neighbours = **2** (no choice; walk continues). |
| **Terminal** | Open neighbours = **1** (end of the line / stub). |
| **Junction** | Open neighbours ≥ **3** (choice of onward direction). |
| **Segment** / **Route** | Synonyms: ordered chain of adjacent Station pairs (**not** required to end at Junction/Terminal). |
| **JunctionSegment** | Segment/Route whose ends are **Junction and/or Terminal** (aliases: **Line**, schematic line; chart **spine** when line-sourced). |
| **Journey** | Published origin → calling stops → destination. |
| **Live-track context** | UI tracking session for a selected Station or train (`routeTrack` / “Route & Live Track” page) — **not** the topology term Route. |

## 1 — Station

```mermaid
flowchart LR
  APS["Station APS<br/>CRS = APS<br/>primary place id"]
```

Text: A **Station** is a place identified primarily by a three-letter **CRS** (e.g. `APS`).

## 2 — Adjacent / neighbouring

```mermaid
flowchart LR
  HML["Station HML"]
  APS["Station APS"]
  HML -->|"adjacent / neighbouring<br/>same physical track<br/>consecutive in ≥1 published Journey<br/>synonyms: hop, edge, link, arc"| APS
  jny["Published Journey<br/>… → HML → APS → …"]
  HML -.-> jny
  APS -.-> jny
```

Text: **Adjacent / neighbouring** always means two Stations on the **same physical track**, reflected as consecutive stops in **at least one published Journey**. Graph synonyms (hop, edge, link, arc) are implementation wording only. Do not use **section** for CRS-pair topology. Code `StripSegment` draws one adjacent link on the strip — migrate naming over time.

## 3 — Segment (not endpoint-bounded)

```mermaid
flowchart LR
  subgraph seg [Segment — chain of adjacent pairs]
    direction LR
    A["A"]
    B["B"]
    C["C"]
    D["D"]
    A --- B --- C --- D
  end
  note["Ends need not be Junction or Terminal<br/>Route = synonym of Segment<br/>contrast: JunctionSegment ends at Junction and/or Terminal"]
  seg -.-> note
```

Text: A **Segment** (synonym: **Route**) is an ordered sequence of Stations joined by **adjacent** pairs, with **no** requirement that ends are Junction or Terminal. A **JunctionSegment** is the special case that **is** bounded by those endpoints. Prefer either name in prose; do not use **Route** for the live-track UI session (that is **live-track context**).

## 4 — Through, Terminal, Junction

```mermaid
flowchart LR
  subgraph roles [Station roles by open-neighbour count]
    direction TB
    THRU["Through<br/>neighbours = 2<br/>no onward choice"]
    TERM["Terminal<br/>neighbours = 1<br/>end of line / stub"]
    JUNC["Junction<br/>neighbours ≥ 3<br/>choice of onward Journey options"]
  end
  KGL["KGL Through"] --- WFJ["WFJ Junction"]
  SAA["SAA"] --- WFJ
  BUS["… toward EUS"] --- WFJ
  MID["…"] --- EUS["EUS Terminal or Junction"]
```

Text: **Junction** means onward **choice** (degree ≥ 3). **Terminal** means degree 1. **Through** means degree 2. Code today often folds Junction∪Terminal into one `isJunction` (neighbours ≠ 2) for walk cutting — migrate names toward this split.

## 5 — JunctionSegment

```mermaid
flowchart LR
  subgraph js [JunctionSegment — ends at Junction and/or Terminal]
    direction LR
    CED["CED<br/>Terminal or Junction"]
    TRI["TRI<br/>Through"]
    BKM["BKM"]
    HML["HML"]
    APS["APS"]
    KGL["KGL"]
    WFJ["WFJ<br/>Junction"]
    CED --- TRI --- BKM --- HML --- APS --- KGL --- WFJ
  end
  note["Also known as: Line<br/>deprecated alias: schematic line<br/>chart spine when this Line is the backbone<br/>does not continue past endpoint Junction/Terminal<br/>walk: Junction|Terminal → Through… → Junction|Terminal"]
  js -.-> note
```

Text: A **JunctionSegment** is a **Segment** whose ends are **Junction and/or Terminal**. Through Stations lie between. Stations beyond an endpoint belong to a different JunctionSegment.

## 6 — Journey

```mermaid
flowchart LR
  subgraph jny [Journey — published stop list]
    direction LR
    EUS["origin EUS"]
    VIA1["via / calling stop"]
    WFJ2["WFJ"]
    APS2["APS"]
    MKC["destination MKC"]
    EUS --> VIA1 --> WFJ2 --> APS2 --> MKC
  end
  subgraph topo [Topology underneath]
    JS1["JunctionSegment<br/>EUS…WFJ"]
    JS2["JunctionSegment<br/>WFJ…MKC"]
    JS1 --- JS2
  end
  train["Service / train<br/>= running instance<br/>not the Journey list"]
  jny -.-> train
  jny -.-> topo
```

Text: A **Journey** may cross several JunctionSegments. **Calling stop / calling point** = board/alight; pass-through is schedule data, not a separate topology type. **Via** = Station between origin and destination on the Journey.

## 7 — Composite: chart, window, live-track context

```mermaid
flowchart TB
  subgraph trackCtx [Live-track context — UI session, not topology Route]
    direction TB
    pick["Selected Station or train<br/>routeTrack code / Route and Live Track page label"]
  end

  subgraph spineBox [Spine — chart backbone sequence]
    direction LR
    J1["CED Junction"]
    T1["TRI"]
    B1["BKM"]
    X1["XOE"]
    H1["HML"]
    A1["APS selected"]
    K1["KGL"]
    W1["WFJ Junction"]
    J1 --- T1 --- B1 --- X1 --- H1 --- A1 --- K1 --- W1
  end

  subgraph windowBox [Window — slice of spine ± WINDOW_RADIUS]
    direction LR
    wT["TRI"]
    wB["BKM"]
    wX["XOE"]
    wH["HML"]
    wA["APS"]
    wK["KGL"]
    wW["WFJ"]
    wT --- wB --- wX --- wH --- wA --- wK --- wW
  end

  subgraph stripBox [Strip / chart — draws Stations + adjacent links]
    strip["Horizontal live-track diagram"]
  end

  subgraph legend [Legend]
    L1["Line = JunctionSegment"]
    L2["Adjacent = same physical track<br/>in ≥1 published Journey"]
    L3["Segment = Route<br/>adjacent Station sequence"]
    L3b["Junction ≥3 · Terminal =1 · Through =2"]
    L4["Corridor = layout paint only<br/>diagram.corridors — not topology"]
    L5["TIPLOC / NLC / STANOX<br/>other UK ids → map to Station"]
    L6["Path / walk = ordered Stations<br/>only when not Journey/Segment/JunctionSegment"]
  end

  trackCtx --> spineBox
  spineBox -->|"usually a JunctionSegment / Line"| windowBox
  windowBox --> stripBox
```

Text: Selecting a Station opens a **live-track context** (code `routeTrack`; page title may still say “Route & Live Track”). The chart **Window** is cut from a **Spine** (usually a **JunctionSegment** / **Line**, which is a kind of Segment/Route). The **strip** renders Stations and adjacent links inside that Window. See [KDD-001](001-live-track-station-chart-window.md) for window/clamp algorithm detail.

## Leftover dictionary

Terms that did not need a separate figure:

| Term | Project meaning |
|------|-----------------|
| **TOC line** | Marketing/operator “line” name — only when explicitly labelled; not **Line** / JunctionSegment. |
| **Pass** | Schedule run-through at a Station without a public call (contrast calling stop). |
| **isJunction (code)** | Today: neighbours ≠ 2 (Junction ∪ Terminal ∪ isolated). Prefer the split names in new prose; rename when migrating. |
| **“Route & Live Track”** | Product/page label for the live-track context — not the topology synonym **Route** (= Segment). |

## Migration

New prose and new code use the **New** column. Existing identifiers stay until deliberately renamed. [KDD-001](001-live-track-station-chart-window.md) keeps the window algorithm; names resolve here.

### Prose / concept

| Old | Migrates to |
|-----|-------------|
| Two-station “segment” / strip edge (topology sense) | **Adjacent / neighbouring** |
| Sequence of stations (informal “route” / “path” on track) | **Segment** / **Route** (synonyms) |
| Schematic line / `diagram.lines` entry (concept) | **Line** = **JunctionSegment** |
| Junction meaning “neighbours ≠ 2” (branch **or** end) | Split: **Junction** (≥3), **Terminal** (=1), **Through** (=2) |
| “Route” as the live-track page / tracking session | **Live-track context** (not topology **Route**) |
| Corridor as a topology path | Do not — **corridor** stays **layout paint only** |
| Path / walk when Journey or Segment/Route applies | Prefer **Journey** or **Segment** / **Route** / **JunctionSegment** |
| Section (CRS-pair) | **Adjacent / neighbouring** |

### Code / identifiers (when renamed)

| Old identifier | Migrates toward |
|----------------|-----------------|
| `StripSegment` | Type/name for one **adjacent** link on the strip |
| `diagram.lines` / schematic line fields | **JunctionSegment** / **Line** naming |
| `isJunction` (neighbours ≠ 2) | Helpers or names for **Junction** / **Terminal** / **Through** (or “walk endpoint” = Junction∪Terminal) |
| `spine*` / spine path vars | **Spine** (chart backbone) or **JunctionSegment** when line-sourced |
| `routeTrack` / route live page symbols | **Live-track context** naming (keep UI string “Route & Live Track” until product copy changes) |
| `corridor` / `diagram.corridors` | Unchanged meaning: layout only — do not repurpose as Segment/Route |
