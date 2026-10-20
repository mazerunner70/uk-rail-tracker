# Interaction specification

Scan and re-entry behaviour for `docs/athena-timetable-lake.md`. Not a UI component library. Ideation `wireframes` and `user-flow` are absent. Stories (`stories.md`) and requirements (`requirements.md`) are document checks. Affirmed `team-practices.md` keep this intent markdown-only.

## How the Later Implementer uses the file

Flow: First ingest then first cheap lookup
Persona: Later Implementer (P1)
Trigger: Start a later construction slice from this architecture file
Steps:

1. Open `docs/athena-timetable-lake.md` → read the H1 and the Environments heading → confirm `{env}` and that resources are named, not created.
2. Jump to the heading for the task (CFA, cheap lookups, or Kafka) → read that chapter only → take partition keys, refuse words, and bind keys into the later design.
3. If the task is TOC extension or layering → jump to those Should Have headings.
4. Before closing scope → read Deferred / not in this document → do not implement listed items in this intent.

Success outcome: the Later Implementer can specify one slice without reopening FR2–FR6.
Error paths:

- Missing required heading → treat the file as incomplete; add the heading rather than inventing a parallel outline.
- Colour-only refuse/accept → rewrite as the words refuse / accept.
- Guessing empty-lake, HD refuse, or overlay bind → those must already be in the CFA and Kafka chapters.

## Document section — Environments

| Field | Value |
|---|---|
| Component | Environments heading |
| Description | Names `dev` / `staging` / `prod` and every logical S3, Glue, and Athena object |
| Category | navigation / orientation |

### States

| State | Description | Trigger |
|---|---|---|
| complete | `{env}` on every logical object; shared-versus-separate picked; example strings present; cadence and rollback obligation named; no secrets | author finished the heading |
| missing heading | H2 Environments absent | file draft omitted it |
| secrets present | keys or access tokens appear | author pasted runtime config |
| claimed live | text says buckets already exist | author wrote as-if-deployed |

### Inputs the heading must settle

| Input | Required | Description |
|---|---|---|
| env pattern | yes | `dev`, `staging`, `prod` as `{env}` |
| bucket sharing | yes | shared versus separate receipt and archive |
| example strings | yes | env-qualified names; values are the author’s choice |
| cadence | yes | staging on merge; prod human approval; rollback obligation |

### Responsive behaviour

| Surface | Behaviour |
|---|---|
| rendered GitHub / IDE preview | H2 in the outline; table of env objects readable without horizontal-only colour |
| raw markdown | same content; no interactive controls |

### Accessibility (usual architecture markdown)

| Requirement | Implementation |
|---|---|
| Heading order | One H1, then H2 Environments |
| Status | Tiers named as words, not colour chips |
| Secrets | None |

## Document section — CFA receipt → validate → apply

| Field | Value |
|---|---|
| Component | CFA ingest chapter |
| Description | Receipt, validate, refuse, accept, history, publish |
| Category | workflow |

### States

| State | Description | Trigger |
|---|---|---|
| accept | Incremental `N` / `R` / `D` for `LM` on CIF identity; atomic current-plan plus history | validation passed |
| refuse | Current plan unchanged; reason recorded; raw zip stays | sequence mismatch, bad member count, HD without three digits |
| empty-lake | Store seeds `990`; first identities and CRS/TIPLOC map have a named path | no previous sequence |
| mid-apply failure | Previous plan remains queryable | apply dies after validate |

Do not specify loading, toast, or spinner states.

### Inputs the heading must settle

| Input | Required | Description |
|---|---|---|
| sequence source | yes | HD columns 33–39 only |
| empty-lake seed | yes | `990` unless a later design records a different seed |
| identity bootstrap | yes | supporting MCA into the same grains, or another named path |
| apply identity | yes | `(train_uid, run_date, stp_indicator)` |
| history grain | yes | counts and identities changed; not full row versions |

## Document section — Cheap lookups

| Field | Value |
|---|---|
| Component | Cheap lookups chapter |
| Description | Four cheapest Athena scans; layout and Glue names live here |
| Category | display |

### States

| State | Description | Trigger |
|---|---|---|
| four-item list | CIF identity, stop sequence, service day, live overlay | chapter complete |
| missing layout | keys named but no Glue name or file layout | author treated format as optional |
| leaked cheapest | station + time-window listed as cheapest | author expanded FR3.1 |

### Inputs the heading must settle

| Input | Required | Description |
|---|---|---|
| partition keys | yes | skip unrelated dates, TOCs, overlay types |
| Glue table names | yes | one name per grain; value is the author’s choice |
| file layout | yes | choose a columnar format |
| worked example | yes | one train, one calendar date |
| service_guid rule | yes | how the identifier is minted |

## Document section — Kafka archive/overlay

| Field | Value |
|---|---|
| Component | Kafka archive/overlay chapter |
| Description | Archive every message; project three types; name bind keys |
| Category | workflow |

### States

| State | Description | Trigger |
|---|---|---|
| archived | Object exists before classify | any Kafka message, including undecodable |
| overlay projected | `TS` / `schedule` / `deactivated` bound to an existing journey | classify hit |
| overlay miss | Archive kept; no new journey; booked times unchanged | bind keys do not resolve |
| reserved | Named type stays in archive only | reserved type list |

### Inputs the heading must settle

| Input | Required | Description |
|---|---|---|
| archive location | yes | env-qualified; sharing pick lives under Environments |
| bind keys | yes | `rid` alias and/or `(train_uid, scheduled start date)` via `service_day` |
| apply types | yes | `TS`, `schedule`, `deactivated` only |
| reserved list | yes | the ten FR4.4 types |

## Document section — mermaid plus text fallback

| Field | Value |
|---|---|
| Component | Diagram block |
| Description | Optional mermaid in a chapter, always followed by a text fallback |
| Category | display |

### States

| State | Description | Trigger |
|---|---|---|
| complete | mermaid fence plus text tree or numbered steps | author added a diagram |
| fallback missing | mermaid only | author omitted the text |
| no diagram | prose only | author chose not to draw |

A chapter with no diagram is valid. A diagram without a fallback is not.

## Progressive disclosure

The file is hierarchical, not a wizard.

- First read: H1 → Environments → the Must Have chapter for the current task.
- Re-entry: outline jump to one H2.
- Should Have (TOC extension, Later package layering) stays on the page map even if cut from the first demo.
- Deferred stays last so Won’t Have is not mixed into Must Have chapters.

## Sources

- `stories.md` — Given/When/Then as file checks
- `requirements.md` — FR2 refuse / apply, FR3 lookups, FR4 archive
- `team-practices.md` — fail-closed CFA, fail-open overlay
- `mockups.md` — heading map and document conditions
