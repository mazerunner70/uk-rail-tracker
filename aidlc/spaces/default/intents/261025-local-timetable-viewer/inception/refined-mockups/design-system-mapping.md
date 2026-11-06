# Design system mapping

There is no design system to map. team-practices and the requirements put this screen in `apps/local-timetable/fe` as its own TypeScript page. It does not use the Android app's components, type, or colour.

The visual source is the two pictures the reader chose. The network page follows `docs/local-timetable/rail controller dashboard.jpeg`. The station-pair page follows `docs/local-timetable/station-pair.png`. stories decide which cells have values. requirements decide the failure words, the interval, and the Europe/London clock. The only layout is that desktop width. There is no phone layout and no tablet layout.

## Look

| Token | Value | Used for |
|---|---|---|
| background | near-black blue | page background, as in both pictures |
| panel | dark blue panel on that background | every pictured region |
| text | off-white | titles, codes, times, "No trains", "This panel has no data" |
| text-muted | light grey that still meets 4.5:1 on the panel | secondary labels |
| accent | the picture's amber for a problem or a cancellation | the words `problem`, `issue`, and `cancelled`, plus an icon |
| ok | the picture's green for on time | the words `on time`, plus an icon |
| focus | 2px off-white outline | keyboard focus |

Colour is never the only signal. A problem row has the word `problem` or `issue`. A cancellation row has the word `cancelled`. An on-time row has the words `on time`.

## Type

One sans-serif face. Codes and times use a monospace face so `YYMMDD` and `HHMM` stay aligned. Titles are the picture's uppercase labels. Body text is sentence case for "No trains", "Loading", "This panel has no data", and "Failed: ".

## What is not mapped

These stay out of the component set: accounts, a hosted theme, a map library, a berth or milepost drawing, a chart for the disruption trend, and any Android style. Panels the file cannot fill are the NoDataPanel in the interaction spec, not a third-party widget.
