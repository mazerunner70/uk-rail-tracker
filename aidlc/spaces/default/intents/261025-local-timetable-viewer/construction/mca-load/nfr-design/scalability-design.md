# Scalability design — mca-load

This unit does not scale out. Capacity is one person, one replace, one day's London Midland rows, this machine.

## Design

| NFR | Solution |
| --- | --- |
| NFR1.9 | A single in-process caller: the query. No second consumer of `start` or `progress`. |
| NFR1.10 | One load flag. A start while running returns that progress. |
| NFR1.11 | Each successful replace deletes `apps/local-timetable/db/timetable.sqlite` and writes that same path. No archive of previous days in SQLite. Overlay tables exist and stay empty. |
| NFR1.12 | Three containers on this machine. This unit lives in the server container. It does not add replicas. |

## Not used

Horizontal scaling, sharding, queues, load balancers, and auto-scaling. A large MCA is handled by streaming (NFR1.3), not by more workers.

## Sources

- `aidlc/spaces/default/intents/261025-local-timetable-viewer/construction/mca-load/nfr-requirements/scalability-requirements.md`
- `aidlc/spaces/default/intents/261025-local-timetable-viewer/construction/mca-load/nfr-requirements/performance-requirements.md`
- `aidlc/spaces/default/intents/261025-local-timetable-viewer/construction/mca-load/functional-design/functional-spec.md`
- `aidlc/spaces/default/intents/261025-local-timetable-viewer/inception/contract-design/contract-summary.md`
- `aidlc/spaces/default/intents/261025-local-timetable-viewer/construction/mca-load/nfr-requirements/tech-stack-decisions.md`

## Assumptions & Open Questions

None.
