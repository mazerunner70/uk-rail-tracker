# Scalability requirements — mca-load

This unit serves one person on this machine. It does not scale out.

## Capacity

| Id | Dimension | Target | Scaling |
| --- | --- | --- | --- |
| NFR1.9 | Users | One person | None. No second consumer of start/progress. |
| NFR1.10 | Concurrent loads | One replace | A later start while running returns that progress |
| NFR1.11 | Data | One day's London Midland journeys from that day's MCA | Replace, do not grow a history of MCA days in SQLite |
| NFR1.12 | Machines | This machine, three containers | No extra replicas |

## Growth

There is no six-month or twelve-month user forecast. A later day's MCA replaces the same file. Overlay tables stay empty after this load.

When the day's MCA is large, the load streams it (NFR1.3). It does not add workers or shards.

## Sources

- `aidlc/spaces/default/intents/261025-local-timetable-viewer/inception/requirements-analysis/requirements.md`
- `aidlc/spaces/default/intents/261025-local-timetable-viewer/construction/mca-load/nfr-requirements/nfr-requirements-questions.md`
- `aidlc/spaces/default/intents/261025-local-timetable-viewer/construction/mca-load/functional-design/functional-spec.md`
- `aidlc/spaces/default/intents/261025-local-timetable-viewer/inception/contract-design/contract-summary.md`

## Assumptions & Open Questions

None.
