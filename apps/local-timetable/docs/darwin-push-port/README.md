# Darwin Push Port reference

Local copies of the specs and XSDs used while parsing RDM Kafka / Darwin
messages. Narrative notes live in [`../kafka-parsing.md`](../kafka-parsing.md);
implementation plan in [`../kafka-updates.md`](../kafka-updates.md).

## Specs

| File | Role |
|------|------|
| `RSPS5053 P-01-01 Darwin PubSub via RDM.pdf` | RDM Kafka transport (credentials, topics, SSL) |
| `P75301004 Push Port Data Interface Specification Issue 27.pdf` | Message semantics — **§3.3** per update type |

## XSDs (start here)

Prefer the newest of each family:

| File | Covers |
|------|--------|
| `rttiPPTSchema_v19.xsd` | Root catalogue of update children under `uR` / `sR` |
| `rttiPPTForecasts_v4.xsd` | `TS` — `arr` / `dep` / `pass`, platforms, length, reasons |
| `rttiPPTSchedules_v5.xsd` | `schedule` / cancel / calling pattern |
| `rttiPPTCommonTypes_v5.xsd` (+ v1 still imported) | Shared RID, UID, times, TIPLOC |
| `rttiPPTFormations_v3.xsd` | Coach formation / loading |

Older versioned files are kept for diffing schema history. `rttiCTT*` schemas
are timetable / reference-data shapes, not live PubSub update bodies.
