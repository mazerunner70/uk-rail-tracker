# Design system mapping

This intent ships no UI kit. The design system is the existing architecture-markdown style in `docs/`, especially `docs/architecture.md`, plus tokens already used in live-sched and `team-practices.md`.

There are no Ideation `wireframes`. Stories (`stories.md`) and requirements (`requirements.md`) are document-delivery.

## Mapping to existing docs

| Need in `docs/athena-timetable-lake.md` | Existing repo pattern | Do not invent |
|-----------------------------------------|------------------------|---------------|
| Layer / flow picture | mermaid `flowchart` in `docs/architecture.md`, then a text tree | Colour-coded status chips |
| Package / prefix layout | fenced `text` tree (`com.ukrailtracker.app/…`) | ASCII art that cannot be copied |
| Named objects | markdown tables (library / purpose in `docs/architecture.md`) | Locked “already created” AWS console screenshots |
| Cross-links | relative markdown links to other `docs/` files | A required pointer from `docs/architecture.md` (US7.5 / out of scope) |
| Status words | live-sched CFA folder statuses `accepted` / `refused` / `failed` / `nothing` | Colour-only accept/refuse |
| Identity tokens | `train_uid`, `service_guid`, `stp_indicator`, `origin_crs`, `dest_crs`, `run_date`, `days_run` | Renames in this document |
| Dates and times | `YYMMDD` text, `HHMM` text | ISO timestamps as the stored grain unless Domain Design changes it |
| Sequence | zero-padded three-digit string from HD columns 33–39 | Zip basename as sequence |
| Environments | `{env}` over `dev` / `staging` / `prod` (`team-practices.md` Deployment) | A live `s3://…` claim that the bucket exists |
| Layering | sibling package under `server/` with its own `pyproject.toml` | Modules inside `live_sched` or an import of `schedule_poc` |

## Tokens to keep

Copy these literals into the later architecture file. They are already the team’s language.

- CIF / CFA: `N`, `R`, `D`; STP `C`, `O`, `P`, `N`; TOC `LM`
- Overlay types: `TS`, `schedule`, `deactivated`
- Reserved types: `association`, `scheduleFormations`, `formationLoading`, `serviceLoading`, `trainOrder`, `OW`, `trainAlert`, `trackingID`, `alarm`, `stationInformation`
- Kafka envelope: Darwin Push Port JSON under `uR` / `sR` (assumption in `requirements.md`)
- Path: `docs/athena-timetable-lake.md` (new file; do not edit `docs/architecture.md` in this intent)

## What this mapping does not cover

- WCAG component tokens, focus rings, 44px targets, skip links (refined-mockups Q4: usual architecture markdown, not a web-app AA bar)
- Design tokens for a lake-operator console or Athena workgroup UI
- Formatter or linter config (`team-practices.md` Code Style: do not add one in this piece of work)

## Heading discipline

Match `docs/architecture.md`: one H1, then H2 chapters, H3 only inside a chapter (CFA and Kafka subheads). Do not skip from H2 to H4. Do not number chapters FR1–FR6 as the outline (refined-mockups Q2 A rejected that).

## Sources

- `docs/architecture.md` — mermaid plus text, tables, package trees
- `team-practices.md` — naming, layering, env tiers
- `stories.md` — product path and deferred list
- `requirements.md` — reserved type list and constraints
