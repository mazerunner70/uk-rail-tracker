# KDD — design docs

This folder holds the **design document** for this app (Knowledge-Driven Development / design knowledge).

Put architecture, product/design decisions, and related KDD material here — not under other `docs/` trees.

### Conventions

- Each KDD starts with a **Glossary** of local terms used in that doc (constants, shorthand, project names). Include only terms that help quick readability.
- Project-wide place/path names are defined in **[KDD-002](002-terminology-map.md)** (diagrams are normative). Prefer those definitions in new prose and code; migrate older identifiers over time.

## Catalogue

| ID | Document |
|----|----------|
| [KDD-001](001-live-track-station-chart-window.md) | Live-track chart — historical Line spine + **train-tracking** windows |
| [KDD-002](002-terminology-map.md) | Project terminology map (Station → adjacent → Segment/Route → JunctionSegment → Journey; canon) |
| [KDD-003](003-journey-window-station-chart.md) | Live-track chart Stations — **implemented**: merge Journeys through S → ±5 + Junction side branches |
