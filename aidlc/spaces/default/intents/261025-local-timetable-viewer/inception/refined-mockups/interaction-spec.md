# Interaction spec

The screen is two pages over two data layers. Components below are what a developer builds. The stories name the fields. The requirements name the failure words and the 30 second interval. There is no shared component library with the Android app. The only layout is the desktop width of the two pictures. There is no phone layout and no tablet layout.

## Timetable data layer

| Field | Value |
|---|---|
| Component | TimetableDataLayer |
| Description | Holds one page's last successful answer and is the only source the UI paints |
| Category | display |

### States

| State | Description | Trigger |
|---|---|---|
| default | Empty. No successful answer yet | page load or reload |
| hover | Not interactive | — |
| focus | Not interactive | — |
| disabled | Not interactive | — |
| loading | An ask is in flight. Held values stay | ask sent |
| error | The last ask failed. Held values stay | failure returned |
| empty | The held successful list has no rows | success with an empty list |

### Props / Inputs

| Prop | Type | Required | Default | Description |
|---|---|---|---|---|
| page | string | yes | — | `network` or `pair`. Each page has its own layer |
| answer | object | no | — | Last successful body. Unset until the first success |
| failure | string | no | — | `missing`, `deleted`, `locked`, or `older schema` when the last ask failed |
| inFlight | boolean | yes | false | True from send until success or failure |

### Responsive Behaviour

| Breakpoint | Behaviour |
|---|---|
| desktop | The layer is unchanged. There is no phone layout and no tablet layout |

### Accessibility

| Requirement | Implementation |
|---|---|
| ARIA role | none. The layer is not a control |
| Keyboard interaction | none |
| Label / aria-label | none |
| Contrast ratio | WCAG AA (4.5:1 text, 3:1 UI components) |
| Screen reader | "No data can be retrieved" is announced only when the page has no held data and the attempt failed. A change to a held value is announced by the control that paints it |
| Focus management | The layer never moves focus |

### Usage Example

```
<TimetableDataLayer
  page="pair"
  answer={lastSuccess}
  failure={null}
  inFlight={false}
/>
```

Rules:

- A success writes the new answer only when it answers the latest attempt for that page. An older success is discarded.
- A value that is the same stays. A value that differs updates. A row that is gone from the successful answer leaves the layer. A row that is new is added.
- A failure leaves `answer` as it was. When `answer` is already set, the screen does not change.
- `inFlight` does not clear `answer` and does not change the screen while `answer` is set.
- "No trains" is painted only when `answer` is a successful empty list.
- "No data can be retrieved" is painted only when `answer` is unset and the latest attempt failed.
- "No problems", "Nothing in this filter", and "Nothing matches" are the other empty sentences.
- Counts are painted only when `answer` is set. Until then the count tiles say they have no data.

## Refresh interval

| Field | Value |
|---|---|
| Component | RefreshInterval |
| Description | Shows the seconds until the open page asks again, and accepts a new whole number |
| Category | input |

### States

| State | Description | Trigger |
|---|---|---|
| default | Shows 30 | page load or reload |
| hover | Cursor over the field | mouseover |
| focus | Keyboard focus | Tab |
| disabled | Not used | — |
| loading | Not used. The interval is not the loading line | — |
| error | The typed value was refused. The number already in use stays, and the refusal is text | blank, fraction, or non-positive |
| empty | Not used. Blank is a refused edit | — |

### Props / Inputs

| Prop | Type | Required | Default | Description |
|---|---|---|---|---|
| seconds | number | yes | 30 | Positive whole number in use |
| onChange | function | yes | — | Called with the new positive whole number |

### Responsive Behaviour

| Breakpoint | Behaviour |
|---|---|
| desktop | Header row, labelled "Interval (seconds)". There is no phone layout and no tablet layout |

### Accessibility

| Requirement | Implementation |
|---|---|
| ARIA role | spinbutton |
| Keyboard interaction | Tab to focus. Up and Down change by 1. Enter commits a typed number. Escape restores the number in use |
| Label / aria-label | Visible label "Interval (seconds)" |
| Contrast ratio | WCAG AA (4.5:1 text, 3:1 UI components) |
| Screen reader | Announces the label, the value, and "value refused" when an edit is refused |
| Focus management | Focus stays in the field when a value is refused |

### Usage Example

```
<RefreshInterval
  seconds={30}
  onChange={setSeconds}
/>
```

A committed change replaces the wait already in progress, measured from the moment of the change. Changing pages does not reset `seconds`. Reload sets it to 30.

## Station pair chooser

| Field | Value |
|---|---|
| Component | StationPairChooser |
| Description | Two labelled CRS boxes. Both must be non-empty before an ask |
| Category | input |

### States

| State | Description | Trigger |
|---|---|---|
| default | Both boxes empty. No ask | page load |
| hover | Cursor over a box | mouseover |
| focus | Keyboard focus in a box | Tab |
| disabled | Not used | — |
| loading | An ask is in flight. The boxes stay editable | both codes non-empty and an ask was sent |
| error | Not a field error. A failed ask is FailureNotice | failure returned |
| empty | One box is empty. No ask | the reader clears a box |

### Props / Inputs

| Prop | Type | Required | Default | Description |
|---|---|---|---|---|
| earlier | string | yes | "" | CRS in the earlier-station box |
| later | string | yes | "" | CRS in the later-station box |
| onChange | function | yes | — | Called with the two codes after either box changes |

### Responsive Behaviour

| Breakpoint | Behaviour |
|---|---|
| desktop | Earlier on the left, swap in the middle, later on the right. There is no phone layout and no tablet layout |

### Accessibility

| Requirement | Implementation |
|---|---|
| ARIA role | textbox on each box |
| Keyboard interaction | Tab between the boxes. Typing does not move focus |
| Label / aria-label | Visible labels "Earlier station" and "Later station" |
| Contrast ratio | WCAG AA (4.5:1 text, 3:1 UI components) |
| Screen reader | Announces each label. Does not announce a list change until the data layer changes |
| Focus management | Focus stays in the box the reader is editing |

### Usage Example

```
<StationPairChooser
  earlier="EUS"
  later="BHM"
  onChange={askIfBothFilled}
/>
```

When either code changes, that change starts an attempt to load. The screen changes only when the latest attempt succeeds and a held value differs. Codes are compared ignoring case. The same two codes are on the network page and on this page.

## Train list

| Field | Value |
|---|---|
| Component | TrainList |
| Description | Paints the held rows for the open page |
| Category | display |

### States

| State | Description | Trigger |
|---|---|---|
| default | Rows from the held answer | success with one or more rows |
| hover | Cursor over a row | mouseover |
| focus | A row can take focus | Tab |
| disabled | Not used | — |
| loading | Hidden while the page is holding data. "Loading" only before the first success | the page has no held data and an attempt is in flight |
| error | FailureNotice is visible. Rows stay | last ask failed |
| empty | The words "No trains" | successful empty list |

### Props / Inputs

| Prop | Type | Required | Default | Description |
|---|---|---|---|---|
| page | string | yes | — | `network` or `pair` |
| rows | array | no | — | Held rows. Unset before the first success |
| filter | string | no | all | Pair page only. `all`, `on time`, `delayed`, or `cancelled` |
| query | string | no | "" | Client-side text filter. Does not change the layer |

### Responsive Behaviour

| Breakpoint | Behaviour |
|---|---|
| desktop | Full table, as in the picture. There is no phone layout and no tablet layout |

### Accessibility

| Requirement | Implementation |
|---|---|
| ARIA role | table |
| Keyboard interaction | Tab moves through rows. Arrow keys move across cells |
| Label / aria-label | Caption "Live schedules" on the network page and "Station pair" on the pair page |
| Contrast ratio | WCAG AA (4.5:1 text, 3:1 UI components) |
| Screen reader | Announces a row when its held values change. Does not announce a wipe on failure |
| Focus management | Focus stays on the same row when that row is still in the layer |

### Usage Example

```
<TrainList
  page="network"
  rows={layer.answer.rows}
  query=""
/>
```

Network columns that have values: `service_guid`, `origin_crs`, `dest_crs`, reached `crs`, next `crs`, and `problem` or `on time`. Headcode, platform, and the single scheduled-time column are present and each cell says "no data".

Pair running columns that have values: `service_guid`, the two station codes, reached `crs`, next `crs`, and `issue` or `on time`. A cancellation row shows `train_uid` and `cancelled`, and the reached and next cells are empty.

## No-data panel

| Field | Value |
|---|---|
| Component | NoDataPanel |
| Description | A pictured panel the timetable file cannot fill |
| Category | display |

### States

| State | Description | Trigger |
|---|---|---|
| default | Shows the panel title and the sentence "This panel has no data" | page open |
| hover | Not interactive | — |
| focus | The region can take focus | Tab |
| disabled | Not used | — |
| loading | Unchanged. This panel does not load | — |
| error | Unchanged. A retrieval failure is not this sentence | — |
| empty | The same sentence. This panel has no empty-list state | — |

### Props / Inputs

| Prop | Type | Required | Default | Description |
|---|---|---|---|---|
| title | string | yes | — | The title printed in the picture |

### Responsive Behaviour

| Breakpoint | Behaviour |
|---|---|
| desktop | Occupies the same region as in the picture. There is no phone layout and no tablet layout |

### Accessibility

| Requirement | Implementation |
|---|---|
| ARIA role | region |
| Keyboard interaction | Tab to the region. No further keys |
| Label / aria-label | The visible title |
| Contrast ratio | WCAG AA (4.5:1 text, 3:1 UI components) |
| Screen reader | Announces the title and "This panel has no data" |
| Focus management | Focus enters and leaves with Tab |

### Usage Example

```
<NoDataPanel title="Network overview" />
```

Network page uses this for Network overview, the route filter, Disruption trend, and the Cancelled footer tile. After a success, System alerts uses it too.

Pair page uses this for the feed-status line, the berth-status line, the line code, the tactical-strategy banner, the shortcut chips, the berth schematic, and the PPM, average delay, route miles, and journey-time tiles.

## Loading line

| Field | Value |
|---|---|
| Component | LoadingLine |
| Description | The first paint of a page that has no held data yet |
| Category | feedback |

### States

| State | Description | Trigger |
|---|---|---|
| default | Hidden once the page is holding data | a successful answer is held |
| hover | Not interactive | — |
| focus | Not interactive | — |
| disabled | Not used | — |
| loading | Visible text "Loading" | the page has no held data and an attempt is in flight |
| error | Hidden. The page says "No data can be retrieved" | the first attempt failed |
| empty | Hidden | — |

### Props / Inputs

| Prop | Type | Required | Default | Description |
|---|---|---|---|---|
| inFlight | boolean | yes | false | From the data layer |

### Responsive Behaviour

| Breakpoint | Behaviour |
|---|---|
| desktop | Above the list. There is no phone layout and no tablet layout |

### Accessibility

| Requirement | Implementation |
|---|---|
| ARIA role | status |
| Keyboard interaction | Not focusable |
| Label / aria-label | The visible word "Loading" |
| Contrast ratio | WCAG AA (4.5:1 text, 3:1 UI components) |
| Screen reader | Announces "Loading" when it appears. Does not clear the list announcement |
| Focus management | Does not move focus |

### Usage Example

```
<LoadingLine inFlight={true} />
```

## Failure notice

| Field | Value |
|---|---|
| Component | FailureNotice |
| Description | Says no data can be retrieved when the page has no held data |
| Category | feedback |

### States

| State | Description | Trigger |
|---|---|---|
| default | Hidden | the page is holding data, or no attempt has failed |
| hover | Not interactive | — |
| focus | The notice can take focus | Tab |
| disabled | Not used | — |
| loading | Hidden | the first attempt is in flight |
| error | Shows "No data can be retrieved" | the page has no held data and the attempt failed |
| empty | Hidden | — |

### Props / Inputs

| Prop | Type | Required | Default | Description |
|---|---|---|---|---|
| failure | string | no | — | Set when the latest attempt failed and the page has no held data |

### Responsive Behaviour

| Breakpoint | Behaviour |
|---|---|
| desktop | Above the list. On the network page the same words also fill System alerts. There is no phone layout and no tablet layout |

### Accessibility

| Requirement | Implementation |
|---|---|
| ARIA role | alert |
| Keyboard interaction | Tab to the notice. Escape does not dismiss it |
| Label / aria-label | Visible text "No data can be retrieved" |
| Contrast ratio | WCAG AA (4.5:1 text, 3:1 UI components) |
| Screen reader | Announces "No data can be retrieved" when the page has no held data and the attempt failed. A later failure is not announced |
| Focus management | Focus moves to the notice when the first attempt fails. When the page is already holding data, focus stays where it was |

### Usage Example

```
<FailureNotice failure="locked" />
```

The notice stays until a later ask succeeds. It is not an empty board.

## Page switcher

| Field | Value |
|---|---|
| Component | PageSwitcher |
| Description | Opens the other page without clearing either data layer |
| Category | navigation |

### States

| State | Description | Trigger |
|---|---|---|
| default | Shows the control for the other page | page open |
| hover | Cursor over the control | mouseover |
| focus | Keyboard focus | Tab |
| disabled | Not used | — |
| loading | Stays usable during an ask | ask in flight |
| error | Stays usable during a failure | failure showing |
| empty | Stays usable | — |

### Props / Inputs

| Prop | Type | Required | Default | Description |
|---|---|---|---|---|
| current | string | yes | — | `network` or `pair` |
| onOpen | function | yes | — | Opens the other page. The pair page paints at once. Neither page's layer is cleared |

### Responsive Behaviour

| Breakpoint | Behaviour |
|---|---|
| desktop | Header control. On the pair page it is the Network Overview crumb. There is no phone layout and no tablet layout |

### Accessibility

| Requirement | Implementation |
|---|---|
| ARIA role | link |
| Keyboard interaction | Tab to focus. Enter follows |
| Label / aria-label | "Open station pair" or "Network Overview" |
| Contrast ratio | WCAG AA (4.5:1 text, 3:1 UI components) |
| Screen reader | Announces the destination |
| Focus management | After the page opens, focus moves to the skip link of that page |

### Usage Example

```
<PageSwitcher
  current="network"
  onOpen={openPair}
/>
```

## List filter

| Field | Value |
|---|---|
| Component | ListFilter |
| Description | Shows a subset of the held pair list |
| Category | input |

### States

| State | Description | Trigger |
|---|---|---|
| default | All is selected | page open |
| hover | Cursor over a choice | mouseover |
| focus | Keyboard focus | Tab |
| disabled | Not used | — |
| loading | Stays on the current choice | ask in flight |
| error | Stays on the current choice | failure |
| empty | "Nothing in this filter" when On time, Delayed, or Cancelled shows nothing. "No trains" when All is selected and the successful list is empty | the visible subset is empty |

### Props / Inputs

| Prop | Type | Required | Default | Description |
|---|---|---|---|---|
| value | string | yes | all | `all`, `on time`, `delayed`, or `cancelled` |
| onChange | function | yes | — | Sets the subset. Does not ask and does not change the layer |

### Responsive Behaviour

| Breakpoint | Behaviour |
|---|---|
| desktop | One row under the berth panel. There is no phone layout and no tablet layout |

### Accessibility

| Requirement | Implementation |
|---|---|
| ARIA role | radiogroup |
| Keyboard interaction | Arrow keys move the choice. Tab enters and leaves the group |
| Label / aria-label | Visible labels All, On time, Delayed, Cancelled |
| Contrast ratio | WCAG AA (4.5:1 text, 3:1 UI components) |
| Screen reader | Announces the choice. The row text still says issue or cancelled |
| Focus management | Focus stays on the chosen radio |

### Usage Example

```
<ListFilter
  value="all"
  onChange={setFilter}
/>
```

Delayed means a running row with an issue, as stories define an issue. On time means a running row with no issue. Cancelled means a cancellation row.

## Station swap

| Field | Value |
|---|---|
| Component | StationSwap |
| Description | Exchanges the earlier station and the later station |
| Category | input |

### States

| State | Description | Trigger |
|---|---|---|
| default | Ready to swap the two codes | pair page open |
| hover | Cursor over the control | mouseover |
| focus | Keyboard focus | Tab |
| disabled | Not used | — |
| loading | Stays usable during an ask | ask in flight |
| error | Stays usable during a failure | failure showing |
| empty | Stays usable when either box is empty | one or both boxes empty |

### Props / Inputs

| Prop | Type | Required | Default | Description |
|---|---|---|---|---|
| earlier | string | yes | "" | Code now in the earlier box |
| later | string | yes | "" | Code now in the later box |
| onSwap | function | yes | — | Called with the two codes exchanged |

### Responsive Behaviour

| Breakpoint | Behaviour |
|---|---|
| desktop | Sits between the two station boxes. There is no phone layout and no tablet layout |

### Accessibility

| Requirement | Implementation |
|---|---|
| ARIA role | button |
| Keyboard interaction | Tab to focus. Enter or Space swaps |
| Label / aria-label | Visible name "Swap stations" |
| Contrast ratio | WCAG AA (4.5:1 text, 3:1 UI components) |
| Screen reader | Announces "Swap stations" and the two codes after the swap |
| Focus management | Focus stays on the button |

### Usage Example

```
<StationSwap
  earlier="EUS"
  later="BHM"
  onSwap={exchangeAndAskIfBothFilled}
/>
```

A swap starts an attempt to load with the codes exchanged. The screen changes only when that latest attempt succeeds and a held value differs.

## Headcode filter

| Field | Value |
|---|---|
| Component | HeadcodeFilter |
| Description | The picture's headcode search. The timetable file has no headcode |
| Category | input |

### States

| State | Description | Trigger |
|---|---|---|
| default | Shows the label and "This control has no data" | pair page open |
| hover | Cursor over the control | mouseover |
| focus | Keyboard focus | Tab |
| disabled | Does not accept a query | always, because there is no headcode to match |
| loading | Unchanged | — |
| error | Unchanged | — |
| empty | The same sentence | — |

### Props / Inputs

| Prop | Type | Required | Default | Description |
|---|---|---|---|---|
| title | string | yes | Filter by headcode | The label from the picture |

### Responsive Behaviour

| Breakpoint | Behaviour |
|---|---|
| desktop | Left of Fleet, above All, On time, Delayed, and Cancelled. There is no phone layout and no tablet layout |

### Accessibility

| Requirement | Implementation |
|---|---|
| ARIA role | searchbox |
| Keyboard interaction | Tab to focus. Typing does nothing |
| Label / aria-label | Visible label "Filter by headcode" |
| Contrast ratio | WCAG AA (4.5:1 text, 3:1 UI components) |
| Screen reader | Announces the label and "This control has no data" |
| Focus management | Focus enters and leaves with Tab |

### Usage Example

```
<HeadcodeFilter title="Filter by headcode" />
```

This control does not filter the list and does not ask the server.

## Fleet

| Field | Value |
|---|---|
| Component | FleetControl |
| Description | The picture's fleet dropdown. The timetable file has no fleet or unit |
| Category | input |

### States

| State | Description | Trigger |
|---|---|---|
| default | Shows the label and "This control has no data" | pair page open |
| hover | Cursor over the control | mouseover |
| focus | Keyboard focus | Tab |
| disabled | Does not open a list of units | always, because there is no fleet to choose |
| loading | Unchanged | — |
| error | Unchanged | — |
| empty | The same sentence | — |

### Props / Inputs

| Prop | Type | Required | Default | Description |
|---|---|---|---|---|
| title | string | yes | Fleet | The label from the picture |

### Responsive Behaviour

| Breakpoint | Behaviour |
|---|---|
| desktop | Right of Filter by headcode, above the status filters. There is no phone layout and no tablet layout |

### Accessibility

| Requirement | Implementation |
|---|---|
| ARIA role | combobox |
| Keyboard interaction | Tab to focus. Arrow keys do not open a menu |
| Label / aria-label | Visible label "Fleet" |
| Contrast ratio | WCAG AA (4.5:1 text, 3:1 UI components) |
| Screen reader | Announces the label and "This control has no data" |
| Focus management | Focus enters and leaves with Tab |

### Usage Example

```
<FleetControl title="Fleet" />
```

This control does not filter the list and does not ask the server.
