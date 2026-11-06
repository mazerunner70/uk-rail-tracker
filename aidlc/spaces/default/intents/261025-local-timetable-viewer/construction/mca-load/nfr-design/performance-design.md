# Performance design — mca-load

How this unit meets the load-time targets. No cache, no pool of workers, no CDN.

## Design

| NFR | Solution |
| --- | --- |
| NFR1.1 | `start` records running progress and returns at once. The replace continues on a background task in the same process. The caller is never joined to that task. |
| NFR1.2 | There is no timer that fails a running load. Progress stays `running` until succeeded or failed. |
| NFR1.3 | Open the day's MCA as a stream. Read the header first, then continue from that stream. Do not buffer the whole file. London Midland filtering happens on each journey as it is seen. |
| NFR1.4 | One in-process load flag. If it is set, `start` returns the current progress and does not spawn a second task. |

## howFar

Percent of the MCA file's size that has been read, as text from `0` to `100`. File size is taken once when the stream opens. After the header is accepted, howFar already reflects those bytes. Once every byte of the MCA has been read, howFar is `100` while state may still be `running` until the SQLite write finishes; state becomes `succeeded` when the replace completes.

## Anti-patterns not used

- No two-pass count of CIF records.
- No holding the MCA as one string or buffer.
- No request queue, connection pool, or replica.

## Sources

- `aidlc/spaces/default/intents/261025-local-timetable-viewer/construction/mca-load/nfr-requirements/performance-requirements.md`
- `aidlc/spaces/default/intents/261025-local-timetable-viewer/construction/mca-load/nfr-design/nfr-design-questions.md`
- `aidlc/spaces/default/intents/261025-local-timetable-viewer/construction/mca-load/functional-design/functional-spec.md`
- `aidlc/spaces/default/intents/261025-local-timetable-viewer/inception/contract-design/contract-summary.md`
- `aidlc/spaces/default/intents/261025-local-timetable-viewer/construction/mca-load/nfr-requirements/tech-stack-decisions.md`

## Assumptions & Open Questions

None.
