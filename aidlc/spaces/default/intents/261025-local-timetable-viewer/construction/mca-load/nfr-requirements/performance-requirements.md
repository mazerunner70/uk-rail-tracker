# Performance requirements — mca-load

Targets for replacing the SQLite file from that day's MCA. One person, this machine. The load returns at once and continues in the same process.

## Targets

| Id | Metric | Target | Percentile | Load | Measurement |
| --- | --- | --- | --- | --- | --- |
| NFR1.1 | Time until start returns LoadProgress | Immediate; the caller is not blocked on the replace | all | one start at a time | Return of start |
| NFR1.2 | Wall-clock for a successful replace | No cap. Progress stays `running` until succeeded or failed | n/a | one day's MCA, streamed | LoadProgress.state |
| NFR1.3 | Memory while reading the day's MCA | Stream the file. Do not require the whole MCA in memory | n/a | a production-sized day's MCA | Process memory vs file size |
| NFR1.4 | Concurrent replaces | One. A start while running returns that progress | all | second start during running | LoadProgress of the first load |

## Anti-requirements

- There is no p95 or p99 bound on how long the replace itself takes.
- There is no hosted request-rate target. This unit is not a public API.
- The whole day's MCA must not be held in memory as a condition of success.

## Sources

- `aidlc/spaces/default/intents/261025-local-timetable-viewer/construction/mca-load/nfr-requirements/nfr-requirements-questions.md`
- `aidlc/spaces/default/intents/261025-local-timetable-viewer/construction/mca-load/functional-design/functional-spec.md`
- `aidlc/spaces/default/intents/261025-local-timetable-viewer/construction/mca-load/functional-design/rules.md`
- `aidlc/spaces/default/intents/261025-local-timetable-viewer/inception/requirements-analysis/requirements.md`
- `aidlc/spaces/default/intents/261025-local-timetable-viewer/inception/contract-design/contract-summary.md`

## Assumptions & Open Questions

None.
