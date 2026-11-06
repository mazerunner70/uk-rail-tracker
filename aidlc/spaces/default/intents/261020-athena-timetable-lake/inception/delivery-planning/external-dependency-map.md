# External Dependency Map

A Bolt is one build pass over a piece of `docs/athena-timetable-lake.md`. This map lists anything outside this session that could hold a Bolt. Question 4: nothing outside this session holds finishing the three chapters. Sample data is available if questions arise; look in the PDFs for details. Those are on-hand references, not gates.

This intent does not stand up buckets, Glue, Athena, or a Kafka client. External CFA supply and the broker belong to a later `server/` package (`components.md`, `unit-of-work.md` Q3 A).

## Gated items

None. No Bolt waits on a live zip, a broker, an AWS account, or another team's hand-off.

| Item | Owner | Lead time | Blocks | If it slips |
|------|-------|-----------|--------|-------------|
| — | — | — | — | — |

## On-hand references (not gates)

| Item | Where | Used when |
|------|-------|-----------|
| Sample data | existing fixtures / sample material in this repo | Functional Design of a chapter has a worked-example question |
| Timetable Information / CIF layout PDFs | PDFs already in the workspace (including the Timetable Information Data Feed Interface Specification under `server/`) | Functional Design needs column or record-layout detail |

If a PDF or sample is missing at the point of a question, Functional Design records the gap as an assumption in that unit's design and continues. It does not pause the Bolt for an external drop.

## Later package (out of this walk)

| Item | Why it is not a gate here |
|------|---------------------------|
| External CFA supply drop | Runtime boundary of later CfaIntake; named in the CFA chapter, not consumed live |
| Kafka broker | Runtime boundary of later KafkaOverlay |
| S3 / Glue / Athena accounts | Named not created (`requirements.md` FR1; US7.4 Deferred) |

## Sources

- `delivery-planning-questions.md` — Q4 A plus sample/PDF note
- `requirements.md` — FR1 document-only; no resource create
- `components.md` — S3, Glue, Athena are dependencies of the later package
- `team-practices.md` — no deploy in this intent
- `contract-summary.md` — external CFA supply and Kafka broker deferred
- `bolt-plan.md` — three document-check Bolts
