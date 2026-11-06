# Reliability requirements — mca-load

Fail closed before the SQLite file is touched. After a successful replace, the database container keeps that file across a restart and a rebuild.

## Targets

| Id | Objective | Target |
| --- | --- | --- |
| NFR2.1 | Durability after success | A restart or a rebuild of the database container keeps the same SQLite file and the same journey rows |
| NFR2.2 | Fail before replace | Missing, unreadable, duplicate-match, or refused-header MCA → progress `failed`, SQLite left as it was |
| NFR2.3 | Fail after delete | If the write fails after the old file is gone, progress is `failed`. The query then sees a missing file |
| NFR1.13 | Process death while running | No second load is started. A later start after the process is back begins a new replace from that day's MCA |
| NFR4.2 | Test isolation | A test of this unit uses a synthetic MCA and does not change a production SQLite file |

## Recovery

| Term | For this unit |
| --- | --- |
| RPO | After a successful replace, the previous SQLite file is gone by design. The recovery point is that new file, or the file left unchanged when start failed before delete. |
| RTO | There is no hosted failover. Restore is: put a day's MCA in `apps/local-timetable/mca` and start again, or keep the unchanged file. |
| Availability | This machine with Docker running. There is no 99.9% hosted SLO. |

## Degradation

- While running, start does not start a second replace.
- A client that already has the old file open keeps that old file until it reconnects.
- There is no retry loop inside this unit. The next start is the next attempt.

## Sources

- `aidlc/spaces/default/intents/261025-local-timetable-viewer/construction/mca-load/functional-design/functional-spec.md`
- `aidlc/spaces/default/intents/261025-local-timetable-viewer/construction/mca-load/functional-design/rules.md`
- `aidlc/spaces/default/intents/261025-local-timetable-viewer/inception/requirements-analysis/requirements.md`
- `aidlc/spaces/default/intents/261025-local-timetable-viewer/inception/contract-design/contract-summary.md`

## Assumptions & Open Questions

None.
