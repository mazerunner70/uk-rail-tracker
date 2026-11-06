# Reliability design — mca-load

Fail closed before `timetable.sqlite` is touched. After a success, the database container's bind-mount keeps that file.

## Design

| NFR | Solution |
| --- | --- |
| NFR2.1 | Write `apps/local-timetable/db/timetable.sqlite` on the database container's bind-mount. A restart or rebuild of that container keeps the same host file. |
| NFR2.2 | Locate the unique day's MCA, open it, and accept the header before any unlink. On miss, unreadable, duplicate match, or refused header: state `failed`, file unchanged. |
| NFR2.3 | After unlink, a write failure sets state `failed`. The query then sees a missing file. There is no automatic retry. |
| NFR1.13 | The load flag lives in this process. Process death clears it. A later start after the process is back begins a new replace. |
| NFR4.2 | Tests use a synthetic MCA and a throwaway SQLite path. They do not unlink the host `timetable.sqlite`. |

## Patterns not used

- No circuit breaker: there is no downstream HTTP call.
- No retry with backoff: the next `start` is the next attempt.
- No multi-AZ failover. Restore is: keep the unchanged file, or put a day's MCA in `apps/local-timetable/mca` and start again.

A client that already has the old inode open keeps that old file until it reconnects.

## Sources

- `aidlc/spaces/default/intents/261025-local-timetable-viewer/construction/mca-load/nfr-requirements/reliability-requirements.md`
- `aidlc/spaces/default/intents/261025-local-timetable-viewer/construction/mca-load/nfr-design/nfr-design-questions.md`
- `aidlc/spaces/default/intents/261025-local-timetable-viewer/construction/mca-load/functional-design/functional-spec.md`
- `aidlc/spaces/default/intents/261025-local-timetable-viewer/construction/mca-load/functional-design/rules.md`
- `aidlc/spaces/default/intents/261025-local-timetable-viewer/inception/contract-design/contract-summary.md`
- `aidlc/spaces/default/intents/261025-local-timetable-viewer/construction/mca-load/nfr-requirements/tech-stack-decisions.md`

## Assumptions & Open Questions

None.
