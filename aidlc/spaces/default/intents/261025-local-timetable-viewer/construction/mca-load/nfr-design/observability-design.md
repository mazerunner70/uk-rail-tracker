# Observability design — mca-load

The query polls in-process progress. That is the only signal the screen can show. Stdout is optional for a person watching the server container.

## Design

| NFR | Solution |
| --- | --- |
| NFR1.14 | One in-process LoadProgress value. `start` and `progress` return it. While `running`, `howFar` is the percent of MCA bytes read, as text, and `sentence` is always set. |
| NFR1.15 | Optional stdout on locate, refuse, delete, write, succeed, fail. Not required for the screen. |
| NFR6.2 | Stdout does not print Kafka or Darwin secret values or the contents of `.env`. |

## Not used

Hosted metrics, traces, dashboards, correlation IDs across services, and alerts. One person reads the empty screen's sentence. A failed load stays there with the button.

## Sources

- `aidlc/spaces/default/intents/261025-local-timetable-viewer/construction/mca-load/nfr-requirements/observability-requirements.md`
- `aidlc/spaces/default/intents/261025-local-timetable-viewer/construction/mca-load/nfr-requirements/performance-requirements.md`
- `aidlc/spaces/default/intents/261025-local-timetable-viewer/construction/mca-load/nfr-design/nfr-design-questions.md`
- `aidlc/spaces/default/intents/261025-local-timetable-viewer/construction/mca-load/functional-design/functional-spec.md`
- `aidlc/spaces/default/intents/261025-local-timetable-viewer/inception/contract-design/contract-summary.md`

## Assumptions & Open Questions

None.
