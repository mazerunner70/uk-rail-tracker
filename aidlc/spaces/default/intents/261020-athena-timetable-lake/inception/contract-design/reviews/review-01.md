## Review

**Verdict:** READY
**Reviewer:** aidlc-architecture-reviewer-agent
**Date:** 2026-10-20T19:05:00Z
**Iteration:** 1

### Findings

| ID | Severity | Location | Finding | Required action | Status |
|---|---|---|---|---|---|
| R-01 | Major | aidlc/spaces/default/intents/261020-athena-timetable-lake/inception/contract-design/contract-summary.md > C3 names.glue_tables_named_by_provider and C1 bind_keys_the_consumer_may_use | C3 names Glue table `service_aliases` and C1 bind is `rid via service_aliases` with join target `serviceGuid`. Neither shared-schema block lists identifier or columns. `components.md` PlanQuery entity ServiceAlias is identifier `aliasTypePlusValue` with attributes `[aliasType, aliasValue, serviceGuid]`. Q1 A required named grains / Glue tables / columns. Overlay bind (AC4.1.4) can still guess a `rid`+`serviceGuid` table instead of the polymorphic alias grain. | Add `service_aliases` identifier and columns to C1 and/or C3 to match ServiceAlias in `components.md`, and state how Darwin `rid` maps onto `aliasType` / `aliasValue`. | New |
| R-02 | Major | aidlc/spaces/default/intents/261020-athena-timetable-lake/inception/contract-design/contract-summary.md > C3 names and C2 consumer_authors | AC4.1.5 and NFR1 require overlay cheap-lookup partition keys so Athena can skip unrelated dates, TOCs, and overlay types. C3 names `overlay_columns` and `type_skip` (column projection, not partition-by-overlayType) but names no overlay partition keys and assigns no author for them. C2 `consumer_authors` limits date/TOC keys to plan grains only. U2 names overlay columns and Glue tables, not keys. U2 and U3 can each invent overlay keys, or omit them. This is not units-generation R-01 (plan-grain keys on the U1–U3 edge). | In C3, name who authors overlay date/TOC partition keys (U2 per AC4.1.5, or an explicit inherit-from-C2/U3 plan-key rule) and list the key names or the inherit rule. | New |
| R-03 | Minor | aidlc/spaces/default/intents/261020-athena-timetable-lake/inception/contract-design/contract-summary.md > C1 publish_set_the_consumer_relies_on and C2 names.grains calling_points | C1 `calling_point_forecast_columns: merge-preserve` and `components.md` KafkaOverlay writes `CallingPoint.forecastArrival` / `forecastDeparture`. C1 grains omit `calling_points`. C2 `calling_points` attributes are booked-only. C3 `overlay_columns` hold row-level forecasts. A chapter writer can preserve unnamed columns or drop the per-stop forecasts. | Either add `forecastArrival` / `forecastDeparture` to a C1 or C2 `calling_points` grain, or state in C1 that per-stop forecast columns are PlanQuery persist details and the queryable overlay grain is only C3's overlay row. | New |
| R-04 | Minor | aidlc/spaces/default/intents/261020-athena-timetable-lake/inception/contract-design/contract-summary.md > C1 and C2 headings_owned_by_provider | Provider heading lists are `environments`, `heading map`, and `CFA receipt → validate → apply`. U1 also owns TOC extension, later package layering, and Deferred; `unit-of-work-dependency.md` cites that full shell as why both consumers depend on U1. Q3 A gives U1 the file shell. U2/U3 already disclaim those headings, so collision risk is low, but the specs do not pin the shell the DAG depends on. | Add TOC extension, later package layering, and Deferred to C1/C2 `headings_owned_by_provider` (or a `file_shell` list) so heading ownership matches U1. | New |

### Validation Tool Results

| Tool | Result | Interpretation |
|---|---|---|
| Stage frontmatter validation tools | None listed | Sensors are not validation tools; no executable checker was run. |
| DAG vs contracts table (manual) | PASS | Three `depends_on` edges, three contracts: C1 overlay→cfa, C2 lookups→cfa, C3 lookups→overlay. No extra runtime API. Q2 A deferred CfaIntake/KafkaOverlay → PlanQuery is named, not in the set. |
| Q1–Q4 vs specs (manual) | PASS | Shared-schema only (Q1 A); later sync calls deferred (Q2 A); provider owns C1/C2 (`cfa-receipt-apply`) and C3 (`kafka-archive-overlay`), `cheap-lookups` owns no provider spec (Q3 A); additive headings/columns safe, rename/remove needs consumer accept (Q4 A). |
| Units-generation R-01–R-03 (manual) | Not re-filed | C2 records the accepted R-01 split (U3 authors lookup keys; U1 authors grain identifiers). C1 locks `service_guid` inputs without claiming the suffix algorithm (R-02 minting gap). C3 assigns the overlay worked example to the U2 provider chapter per US4.1 / AC4.1.5 (R-03). No new contradiction on those three points. |

### Summary

The three chapter edges match the DAG and Q1–Q4. Weigh R-01 and R-02 before approving: `service_aliases` is named without columns, and overlay partition keys have no author, so Construction can still guess names AC4.1.4 / AC4.1.5 need.
