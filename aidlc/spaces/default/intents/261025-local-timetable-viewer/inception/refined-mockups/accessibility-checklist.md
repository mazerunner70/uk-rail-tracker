# Accessibility checklist

Target is WCAG 2.1 AA. The only surface is the desktop browser on this machine, at the width of the two pictures. There is no phone layout and no tablet layout. The two station names sit at the top of the network page. The screen changes only when the held data changes. "No trains" is a successful empty list.

## Perceivable

| Check | Result |
|---|---|
| Page language is English, set with `lang="en"` | Required on both pages |
| The clock is text, labelled Europe/London | The picture's "UTC" is not used |
| "No trains", "Loading", "No data can be retrieved", "No problems", "Nothing in this filter", "Nothing matches", and "This panel has no data" are text | Required |
| Headcode, platform, scheduled time, map, berth, and the other no-data panels say "no data" in text | Required |
| Problem, issue, cancelled, and on time are words, not colour alone | Required |
| Normal text meets 4.5:1. Large text and UI marks meet 3:1 | Required on the dark panels |
| Decorative rail marks use an empty alt | Required |
| No content flashes | Required |

## Operable

| Check | Result |
|---|---|
| A skip link is the first focusable control on each page and moves focus to the train list | Required |
| Every control works from the keyboard | Interval, both station boxes, the swap between them, search, list filter, page switcher. Filter by headcode and Fleet take focus and say they have no data |
| Focus is a 2px outline at 3:1 against the panel | Required |
| Tab order follows the page from top to bottom | On the network page: header, earlier station, swap, later station, interval, search, then the list. On the pair page: header, earlier station, swap, later station, Filter by headcode, Fleet, the status filters, then the list |
| There is no keyboard trap | The failure notice does not trap focus |
| The interval can be changed and the wait extended | The reader sets the seconds. There is no separate timeout |
| The interval elapses without moving the screen | An attempt starts. Held data stays until a later success changes it |
| Target size of the page switcher and the filter choices is at least 24 by 24 CSS pixels | Required |

## Understandable

| Check | Result |
|---|---|
| Navigation is the same control in the header of both pages | PageSwitcher |
| Labels sit above the interval and the two station boxes | "Interval (seconds)", "Earlier station", "Later station" |
| Editing a station box does not move focus and does not clear the list | The list changes only when the data layer changes |
| A refused interval stays the number already in use and the refusal is text | "Value refused" |
| A failure with no held data says "No data can be retrieved" | FailureNotice |
| "No trains" is only a successful empty list | "No problems", "Nothing in this filter", and "Nothing matches" are the other empty sentences |

## Robust

| Check | Result |
|---|---|
| One heading level per page: the control-room title, then panel titles | Required |
| The train list is a table with a caption and column headers | TrainList |
| The loading line is a status region | Announced only before the page has held data. It does not appear beside held rows |
| The failure notice is an alert | Announced only when the page has no held data and the attempt failed. The text is "No data can be retrieved" |
| When the page is already holding data, a failed attempt does not move focus and does not announce a change | Required |
| When the page has no held data, focus moves to the failure notice | Required |
| A change to a held cell is announced from that cell | The rest of the table is not re-announced |
| Ids on the two pages do not clash if both layers are in one document | Prefix `network-` and `pair-` |

## Pages

| Page | Reachable | Empty | Failure | Loading |
|---|---|---|---|---|
| Network | Earlier station, swap, and Later station are at the top. Open station pair opens the other page | "No trains" after a successful empty answer. Search with no hit says "Nothing matches". Disruptions with no problem row say "No problems" | "No data can be retrieved" only when this page has no held data | "Loading" only before the first success |
| Station pair | Opens from the two names and paints at once. Network Overview returns | "No trains" for a successful empty list. A status filter with nothing visible says "Nothing in this filter" | "No data can be retrieved" only when this page has no held data, including invalid stations | "Loading" only before the first success |
