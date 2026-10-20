## Review

**Verdict:** READY
**Reviewer:** aidlc-product-lead-agent
**Date:** 2026-10-20T16:46:00Z
**Iteration:** 1

### Findings

| ID | Severity | Location | Finding | Required action | Status |
|---|---|---|---|---|---|
| R-01 | Major | aidlc/spaces/default/intents/261020-athena-timetable-lake/inception/refined-mockups/mockups.md > Per-section sketches > Cheap lookups | FR3.3 requires `docs/athena-timetable-lake.md` to state how timetable rows are transformed from CIF/CFA records into the cheap-lookup grains (including STP winner order, so Athena does not rescan every schedule window for FR3.1 item 3). The Cheap lookups sketch requires STP winner order C then O then P then N, a `service_guid` assignment rule, keys, columns, Glue names, file layout, and a worked example of keys and columns. It does not require prose for the CIF/CFA-to-grain mapping for journey, calling points (`service_guid`, seq), or service_day. A later implementer who fills only this sketch can ship keys without the transform; construction would then reopen FR3.3. | Add a required bullet or H3 under Cheap lookups that the later file must state how CIF/CFA records become each of grains 1–3, not only keys, STP winner order, and `service_guid` minting. | New |
| R-02 | Major | aidlc/spaces/default/intents/261020-athena-timetable-lake/inception/refined-mockups/mockups.md > Per-section sketches > Kafka archive/overlay > Overlay cheap lookup | FR3.1 requires partition keys, file layout, and table grain for all four cheapest lookups, including live overlay. Cheap lookups requires file layout, prefix/grain, and Glue table name for items 1–3 only. Kafka Overlay cheap lookup requires partition keys, projected columns, and one worked example (aligned with AC4.1.5) and does not require overlay layout or grain. The later file can specify overlay columns without overlay layout, leaving FR3.1 item 4 incomplete. | Require overlay file layout and table grain under Overlay cheap lookup, or state that overlay inherits the Cheap lookups columnar/layout pick and make that inheritance a document check. | New |
| R-03 | Minor | aidlc/spaces/default/intents/261020-athena-timetable-lake/inception/refined-mockups/mockups.md > Scan paths > Scan: Should Have (US5.1, US6.1) | Must Have scans each give Success and Fail lines a reviewer can test. The Should Have scan lists two jumps and has no Success/Fail. It also omits parser/repository client placement (AC6.1.2), which the Later package layering sketch already requires. A reviewer following scan paths only can miss AC6.1.2. | Add Success/Fail for US5.1 and US6.1, and include that object-store and query-engine clients stay out of CIF parsers and the repository layer in the US6.1 steps. | New |

### Summary

The heading map, story-to-heading table, Must Have scan paths, and document conditions match Q1–Q3; missing traveller or console screens are not a defect. Weigh two slot gaps before approving: Cheap lookups never requires the CIF/CFA-to-grain transform (FR3.3), and overlay cheap lookup never requires file layout or table grain (FR3.1 item 4).
