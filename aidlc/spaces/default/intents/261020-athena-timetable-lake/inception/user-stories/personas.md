# Personas

Stories are written only for the later implementer (user-stories Q1 A). A future lake operator and a traveller are out of this backlog.

## P1 — Later Implementer (primary)

- **Name:** Later Implementer
- **Role:** The person who will write the first ingest and query slice from `docs/athena-timetable-lake.md`
- **Goals:**
  - Build CFA receipt → validate → apply without reopening FR2
  - Build the four cheap Athena lookups and the three overlay types without reopening FR3–FR4
  - Know env names, layering, and what is deferred (TOC extension, reserved Kafka types)
- **Pain points:**
  - Guessing empty-lake bootstrap, HD refuse rules, or overlay bind keys
  - Finding secrets or missing `dev` / `staging` / `prod` names in the architecture file
  - Copying live-sched gaps (`schedule_poc` import, UID-wide scan, lake clients inside parsers)
- **Context:** Brownfield; ideas come from live-sched and CodeKB. This intent produces the markdown only.
- **Tech comfort:** High
- **Frequency:** Once per later construction slice
- **Priority:** 1 (only persona)

## Relationships

The Later Implementer is the sole actor in `stories.md`. Design and Refined Mockups treat the architecture file as the surface they use, not a traveller UI.
