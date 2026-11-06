# Observability requirements — mca-load

The query reads LoadProgress. That is the signal the screen can poll. Extra diagnostics may go to the server container's stdout.

## Signals

| Id | Kind | What | When |
| --- | --- | --- | --- |
| NFR1.14 | Progress | `state`, `sentence`, and `howFar` while `running` | Every progress call, and the return of start |
| NFR1.15 | Logs | Optional stdout lines for locate, refuse, delete, write, succeed, fail | During a load |
| NFR6.2 | Redaction | Logs must not contain Kafka or Darwin secret values | Always |

## SLI / SLO

| SLI | SLO |
| --- | --- |
| Start returns LoadProgress without waiting for the replace | Always (NFR1.1) |
| While `running`, howFar is present as 0–100 text and sentence is present | Always |
| A fail-before-replace path leaves SQLite unchanged | Always (NFR2.2) |

There is no hosted dashboard, no trace sampler, and no pager. One person watches the empty screen's progress sentence.

## Alerts

None. A failed load stays on the empty screen with the button. The next start is the next attempt.

## Sources

- `aidlc/spaces/default/intents/261025-local-timetable-viewer/construction/mca-load/functional-design/functional-spec.md`
- `aidlc/spaces/default/intents/261025-local-timetable-viewer/inception/contract-design/contract-summary.md`
- `aidlc/spaces/default/intents/261025-local-timetable-viewer/inception/requirements-analysis/requirements.md`
- `aidlc/spaces/default/intents/261025-local-timetable-viewer/construction/mca-load/nfr-requirements/nfr-requirements-questions.md`

## Assumptions & Open Questions

None.
