# Monitoring design — mca-load

The screen is the signal. There is no hosted metrics plane, no dashboard product, and no pager.

## Metrics & KPIs

| Metric | Source | Threshold | Why it matters |
| --- | --- | --- | --- |
| LoadProgress.state | In-process value returned by `start` / `progress` | None. Values are `idle`, `running`, `succeeded`, `failed`. | NFR1.14. The empty screen's sentence is this value. |
| LoadProgress.howFar | Same in-process value | Text `0`–`100`, percent of MCA bytes read | Progress while `running`. Not a CloudWatch metric. |
| LoadProgress.sentence | Same in-process value | Always set while the load is not idle | The person reads this. No alert routes off it. |

## Alerts

| Alert | Condition | Severity | Routes to |
| --- | --- | --- | --- |
| none | — | — | No email, Slack, PagerDuty, or CloudWatch alarm. A failed load stays on the empty screen with the button. |

## SLIs / SLOs

| SLI | SLO target | Measurement window |
| --- | --- | --- |
| none | No hosted 99.9% target | This is one person on this machine. Start returns at once (NFR1.1). There is no wall-clock fail (NFR1.2). |

## Logs & Tracing

| Channel | What it carries | What it must not carry |
| --- | --- | --- |
| Optional `server` stdout | Locate, refuse, delete, write, succeed, fail (NFR1.15) | Kafka keys, Darwin keys, `.env` contents (NFR6.2) |
| `docker compose -f apps/local-timetable/compose.yaml logs server` | The same stdout, if the person tails the container | Same redaction |
| Distributed tracing | none | No X-Ray, no OpenTelemetry collector |
| Log aggregation | none | No CloudWatch Logs, no Elasticsearch |

No dashboard specification. The empty screen plus optional stdout is the whole view of this unit.

## Sources

- `aidlc/spaces/default/intents/261025-local-timetable-viewer/construction/mca-load/nfr-design/observability-design.md`
- `aidlc/spaces/default/intents/261025-local-timetable-viewer/construction/mca-load/nfr-design/performance-design.md`
- `aidlc/spaces/default/intents/261025-local-timetable-viewer/construction/mca-load/nfr-design/security-design.md`
- `aidlc/spaces/default/intents/261025-local-timetable-viewer/construction/mca-load/nfr-requirements/observability-requirements.md`
- `aidlc/spaces/default/intents/261025-local-timetable-viewer/inception/contract-design/contract-summary.md`
- `aidlc/spaces/default/intents/261025-local-timetable-viewer/construction/mca-load/functional-design/functional-spec.md`

## Assumptions & Open Questions

None.
