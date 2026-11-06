# Security requirements — mca-load

This unit runs in the server container on this machine. There are no accounts and no hosted API. The threats that matter are a secret landing in the load, a production MCA opened by a test, and a second replace while one is already running.

## Requirements

| Id | Area | Requirement |
| --- | --- | --- |
| NFR1.5 | Authentication | None. This unit is not reachable except as an in-process call from the query in the same server container. |
| NFR1.6 | Authorization | None. One person on this machine. No roles. |
| NFR6.1 | Secrets | This unit stores no Kafka or Darwin secret values. Those stay in the gitignored workspace-root `.env` and in Android `local.properties`. |
| NFR6.2 | Logging | Diagnostics may go to the server container's stdout. They must not contain Kafka or Darwin secret values. |
| NFR5.1 | Checks | Local secret scanning, dependency checks, and a dynamic check cover this TypeScript unit. This service does not add a continuous-integration pipeline. |
| NFR4.1 | Tests | A test of this unit uses a synthetic MCA and does not open a production timetable file. |
| NFR3.1 | Writer | This unit is the only writer of `timetable.sqlite`. The query does not create or rewrite it. |
| NFR1.7 | Network | There is no public listener on this unit. Start and progress are in-process. |
| NFR1.8 | Files | The day's MCA is read from `apps/local-timetable/mca`. This unit writes `apps/local-timetable/db/timetable.sqlite`; the query reads that same file. Both paths are on this machine. |

## Threats considered

| Threat | Against this unit | Response |
| --- | --- | --- |
| Spoofing | No accounts to impersonate | In-process caller only |
| Tampering | Host files this person can already edit | Fail closed on an unreadable or refused MCA; do not replace |
| Repudiation | One person | LoadProgress sentence is enough |
| Information disclosure | Kafka or Darwin secrets | NFR6.1, NFR6.2 |
| Denial of service | Whole MCA in memory, or two replaces | Stream; one load at a time |
| Elevation of privilege | No privilege model | No accounts |

No PCI, HIPAA, or SOC 2 scope. This is a local timetable file for one person. No cardholder data, no health records, no hosted tenant.

## Sources

- `aidlc/spaces/default/intents/261025-local-timetable-viewer/inception/requirements-analysis/requirements.md`
- `aidlc/spaces/default/intents/261025-local-timetable-viewer/construction/mca-load/functional-design/functional-spec.md`
- `aidlc/spaces/default/intents/261025-local-timetable-viewer/construction/mca-load/functional-design/rules.md`
- `aidlc/spaces/default/intents/261025-local-timetable-viewer/inception/contract-design/contract-summary.md`
- `aidlc/spaces/default/codekb/uk-rail-tracker/technology-stack.md`

## Assumptions & Open Questions

None.
