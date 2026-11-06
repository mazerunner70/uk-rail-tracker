# Security design — mca-load

This unit has no accounts and no public listener. Defence is: in-process only, fail closed on a bad MCA, never store or log Kafka or Darwin secrets, tests use a synthetic file.

## Design

| NFR | Solution |
| --- | --- |
| NFR1.5 | No authentication. `start` and `progress` are functions in the same process as the query. |
| NFR1.6 | No roles. One person on this machine. |
| NFR1.7 | This unit does not bind a port. REST stays on the query. |
| NFR1.8 | Read only from `apps/local-timetable/mca`. Write only `apps/local-timetable/db/timetable.sqlite`. Do not follow a caller-supplied path. |
| NFR3.1 | Only this unit unlinks or creates `timetable.sqlite`. The query opens it read-only. |
| NFR4.1 | Tests point at a synthetic MCA fixture. They do not open the host production directory. |
| NFR5.1 | Local secret scanning, dependency checks, and a dynamic check of this TypeScript unit. No CI pipeline. |
| NFR6.1 | No Kafka client, no Darwin client, no `.env` read in this unit. Feed meta may store load facts, never secret values. |
| NFR6.2 | Stdout lines name state and paths already locked above. They do not print environment values or credential files. |

## Input handling

The day's MCA is untrusted file bytes on this machine. Locate and open it, read the header, refuse a bad sequence, and only then delete `timetable.sqlite`. CIF fields are parsed as text tokens. They are not executed. A test MCA is a fixture this unit owns.

## Not in this unit

TLS, CSRF, XSS, session cookies, and IAM. Those belong to a hosted surface this service does not have.

## Sources

- `aidlc/spaces/default/intents/261025-local-timetable-viewer/construction/mca-load/nfr-requirements/security-requirements.md`
- `aidlc/spaces/default/intents/261025-local-timetable-viewer/construction/mca-load/nfr-design/nfr-design-questions.md`
- `aidlc/spaces/default/intents/261025-local-timetable-viewer/construction/mca-load/functional-design/functional-spec.md`
- `aidlc/spaces/default/intents/261025-local-timetable-viewer/construction/mca-load/functional-design/rules.md`
- `aidlc/spaces/default/intents/261025-local-timetable-viewer/inception/contract-design/contract-summary.md`
- `aidlc/spaces/default/intents/261025-local-timetable-viewer/inception/requirements-analysis/requirements.md`
- `aidlc/spaces/default/intents/261025-local-timetable-viewer/construction/mca-load/nfr-requirements/tech-stack-decisions.md`

## Assumptions & Open Questions

None.
